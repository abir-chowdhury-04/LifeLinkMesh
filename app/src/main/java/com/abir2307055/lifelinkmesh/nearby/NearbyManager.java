package com.abir2307055.lifelinkmesh.nearby;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.abir2307055.lifelinkmesh.model.Peer;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import android.os.ParcelFileDescriptor;
import com.abir2307055.lifelinkmesh.data.ProfileStore;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NearbyManager {

    private static final String TAG = "LifeLinkNearby";
    // P2P_CLUSTER = M-to-N topology, needed so every phone can link to several others.
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    // How long the "higher-name" phone waits so the other phone can go first.
    private static final long TIE_BREAK_DELAY_MS = 3000;
    // Endpoints Nearby has told us are gone (their ids are dead).
    private final Set<String> lostEndpoints = ConcurrentHashMap.newKeySet();

    // How NearbyManager reports events back to the screen.
    public interface Listener {
        void onEndpointFound(String endpointId, String name);
        void onEndpointLost(String endpointId);
        void onConnectionRequest(String endpointId, String name, String digits, boolean incoming);
        void onConnectionStateChanged(String endpointId, String state);
        void onMessageReceived(String endpointId, String text);
        void onLog(String message);
        default void onFilePayloadReceived(String endpointId, Payload payload) { }

        default void onTransferUpdate(String endpointId, long payloadId, int status,
                                      long bytesTransferred, long totalBytes) { }
    }

    private final ConnectionsClient client;
    private final String serviceId;
    private final Context appContext;
    // The number stays the same for the whole app run, so our identity does not jump around.
    private final int suffix = 100 + new Random().nextInt(900);
    // Not final: it is rebuilt in start(), so a name the user changed is used.
    private volatile String localName;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Endpoints for which a connection has already been initiated (by either side).
    private final Set<String> initiatedEndpoints = ConcurrentHashMap.newKeySet();
    // Endpoints we are fully connected to right now.
    private final Set<String> connectedEndpoints = ConcurrentHashMap.newKeySet();
    private Listener listener;

    private boolean running = false;   // true between start() and stop()

    public NearbyManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.client = Nearby.getConnectionsClient(appContext);
        this.serviceId = appContext.getPackageName();
        this.localName = ProfileStore.loadName(appContext) + " #" + suffix;
        this.listener = listener;
    }

    public String getLocalName() {
        return localName;
    }

    public void start() {
        running = true;
        // Use the newest chosen name each time the mesh starts.
        localName = ProfileStore.loadName(appContext) + " #" + suffix;
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        client.startAdvertising(localName, serviceId, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(unused -> log("Advertising as " + localName))
                .addOnFailureListener(e -> log("Advertising failed: " + e.getMessage()));

        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        client.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(unused -> log("Discovery started"))
                .addOnFailureListener(e -> log("Discovery failed: " + e.getMessage()));
    }

    public void stop() {
        running = false;
        client.stopAdvertising();
        client.stopDiscovery();
        client.stopAllEndpoints();
        initiatedEndpoints.clear();
        connectedEndpoints.clear();
        lostEndpoints.clear();
        log("Mesh stopped: advertising, discovery and all connections");
    }

    // Restarts only the search for nearby phones. Existing connections stay up.
    public void refreshDiscovery() {
        if (!running) {
            log("Start the mesh first");
            return;
        }
        client.stopDiscovery();
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        client.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(unused -> log("Discovery restarted"))
                .addOnFailureListener(e -> log("Refresh failed: " + e.getMessage()));
    }

    // Tie-break: if both phones tap each other at once, only one should send the request.
    // The phone with the smaller name goes first; the other waits and checks.
    // Tie-break: if both phones tap each other at once, only one should send the request.
    // The phone with the smaller name goes first; the other waits and checks.
    public void connectTo(String endpointId, String remoteName) {
        boolean iGoFirst = localName.compareTo(remoteName) <= 0;
        if (iGoFirst) {
            sendRequest(endpointId);
            return;
        }
        log("Waiting so " + remoteName + " can go first");
        handler.postDelayed(() -> {
            if (lostEndpoints.contains(endpointId)) {
                // The other phone went away (or restarted) while we waited: its id is dead.
                log(remoteName + " went away, not sending");
                listener.onConnectionStateChanged(endpointId, Peer.STATUS_FAILED);
                listener.onEndpointLost(endpointId);
            } else if (initiatedEndpoints.contains(endpointId)) {
                log("Other phone already started, not sending");
            } else {
                sendRequest(endpointId);
            }
        }, TIE_BREAK_DELAY_MS);
    }

    private void sendRequest(String endpointId) {
        client.requestConnection(localName, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(unused -> log("Connection requested"))
                .addOnFailureListener(e -> {
                    log("Request failed: " + e.getMessage());
                    listener.onConnectionStateChanged(endpointId, Peer.STATUS_FAILED);
                    if (lostEndpoints.contains(endpointId)) {
                        listener.onEndpointLost(endpointId);   // remove the dead row
                    }
                });
    }

    // Called when the user presses Accept in the dialog.
    public void acceptConnection(String endpointId) {
        client.acceptConnection(endpointId, payloadCallback);
    }

    // Called when the user presses Reject in the dialog.
    public void rejectConnection(String endpointId) {
        client.rejectConnection(endpointId);
    }
    // Sends a short text to ONE phone (used for the file handshake).
    public void sendTextTo(String endpointId, String text) {
        client.sendPayload(endpointId,
                        Payload.fromBytes(text.getBytes(StandardCharsets.UTF_8)))
                .addOnFailureListener(e -> log("Send failed: " + e.getMessage()));
    }

    public Payload newFilePayload(ParcelFileDescriptor pfd) {
        return Payload.fromFile(pfd);
    }

    public void sendPayloadTo(String endpointId, Payload payload) {
        client.sendPayload(endpointId, payload)
                .addOnFailureListener(e -> log("Send failed: " + e.getMessage()));
    }

    // Sends a short text to every connected phone.
    // Sends a short text to every connected phone.
    // Sends a short text to every connected phone.
    public void sendToAll(String text) {
        sendToAllExcept(null, text, false);
    }

    public void sendToAllExcept(String excludedEndpointId, String text) {
        sendToAllExcept(excludedEndpointId, text, false);
    }

    // Same, but skips one endpoint (relay: don't send back to where it came from).
    // quiet = true writes nothing to the log (used for the regular neighbour reports).
    public void sendToAllExcept(String excludedEndpointId, String text, boolean quiet) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int count = 0;
        for (String endpointId : connectedEndpoints) {
            if (endpointId.equals(excludedEndpointId)) {
                continue;
            }
            count++;
            // A new Payload object for each recipient.
            client.sendPayload(endpointId, Payload.fromBytes(bytes))
                    .addOnSuccessListener(unused -> {
                        if (!quiet) {
                            log("Sent to " + endpointId);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (!quiet) {
                            log("Send failed: " + e.getMessage());
                        }
                    });
        }
        if (count == 0 && !quiet) {
            log("No peers to send to");
        }
    }

    private void log(String message) {
        Log.d(TAG, message);
        if (listener != null) {
            listener.onLog(message);
        }
    }

    // Receives data from connected phones.
    // Receives data from connected phones.
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] bytes = payload.asBytes();
                if (bytes != null) {
                    listener.onMessageReceived(endpointId,
                            new String(bytes, StandardCharsets.UTF_8));
                }
            } else if (payload.getType() == Payload.Type.FILE) {
                listener.onFilePayloadReceived(endpointId, payload);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate update) {
            listener.onTransferUpdate(endpointId, update.getPayloadId(), update.getStatus(),
                    update.getBytesTransferred(), update.getTotalBytes());
        }
    };

    // Called when a nearby advertiser appears or disappears.
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId,
                                            @NonNull DiscoveredEndpointInfo info) {
                    lostEndpoints.remove(endpointId);
                    log("Found " + info.getEndpointName());
                    listener.onEndpointFound(endpointId, info.getEndpointName());
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    lostEndpoints.remove(endpointId);
                    log("Lost " + endpointId);
                    listener.onEndpointLost(endpointId);
                }
            };

    // Called for each step of connecting and disconnecting.
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId,
                                                  @NonNull ConnectionInfo info) {
                    initiatedEndpoints.add(endpointId);
                    log("Connection initiated with " + info.getEndpointName());
                    // Ask the user. Both phones must accept.
                    listener.onConnectionRequest(endpointId, info.getEndpointName(),
                            info.getAuthenticationDigits(), info.isIncomingConnection());
                    listener.onConnectionStateChanged(endpointId, Peer.STATUS_CONNECTING);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId,
                                               @NonNull ConnectionResolution result) {
                    int code = result.getStatus().getStatusCode();
                    if (code == ConnectionsStatusCodes.STATUS_OK) {
                        connectedEndpoints.add(endpointId);
                        log("Connected to " + endpointId);
                        listener.onConnectionStateChanged(endpointId, Peer.STATUS_CONNECTED);
                    } else {
                        initiatedEndpoints.remove(endpointId);
                        connectedEndpoints.remove(endpointId);
                        log("Connection failed or rejected, code " + code);
                        listener.onConnectionStateChanged(endpointId, Peer.STATUS_FAILED);
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    initiatedEndpoints.remove(endpointId);
                    connectedEndpoints.remove(endpointId);
                    log("Disconnected from " + endpointId);
                    listener.onConnectionStateChanged(endpointId, Peer.STATUS_DISCONNECTED);
                }
            };
}