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

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NearbyManager {

    private static final String TAG = "LifeLinkNearby";
    // P2P_CLUSTER = M-to-N topology, needed so every phone can link to several others.
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    // How long the "higher-name" phone waits so the other phone can go first.
    private static final long TIE_BREAK_DELAY_MS = 3000;

    // How NearbyManager reports events back to the screen.
    public interface Listener {
        void onEndpointFound(String endpointId, String name);
        void onEndpointLost(String endpointId);
        void onConnectionRequest(String endpointId, String name, String digits, boolean incoming);
        void onConnectionStateChanged(String endpointId, String state);
        void onLog(String message);
    }

    private final ConnectionsClient client;
    private final String serviceId;
    private final String localName;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Endpoints for which a connection has already been initiated (by either side).
    private final Set<String> initiatedEndpoints = ConcurrentHashMap.newKeySet();
    private Listener listener;

    public NearbyManager(Context context, Listener listener) {
        Context appContext = context.getApplicationContext();
        this.client = Nearby.getConnectionsClient(appContext);
        this.serviceId = appContext.getPackageName();
        this.localName = Build.MODEL + " #" + (100 + new Random().nextInt(900));
        this.listener = listener;
    }

    public String getLocalName() {
        return localName;
    }

    public void start() {
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
        client.stopAdvertising();
        client.stopDiscovery();
        client.stopAllEndpoints();
        initiatedEndpoints.clear();
        log("Mesh stopped: advertising, discovery and all connections");
    }

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
            if (initiatedEndpoints.contains(endpointId)) {
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

    private void log(String message) {
        Log.d(TAG, message);
        if (listener != null) {
            listener.onLog(message);
        }
    }

    // Receives data from connected phones. Empty for now; Phase 1c fills it in.
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate update) {
        }
    };

    // Called when a nearby advertiser appears or disappears.
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId,
                                            @NonNull DiscoveredEndpointInfo info) {
                    log("Found " + info.getEndpointName());
                    listener.onEndpointFound(endpointId, info.getEndpointName());
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
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
                        log("Connected to " + endpointId);
                        listener.onConnectionStateChanged(endpointId, Peer.STATUS_CONNECTED);
                    } else {
                        initiatedEndpoints.remove(endpointId);
                        log("Connection failed or rejected, code " + code);
                        listener.onConnectionStateChanged(endpointId, Peer.STATUS_FAILED);
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    initiatedEndpoints.remove(endpointId);
                    log("Disconnected from " + endpointId);
                    listener.onConnectionStateChanged(endpointId, Peer.STATUS_DISCONNECTED);
                }
            };
}