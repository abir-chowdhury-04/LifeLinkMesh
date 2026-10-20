package com.abir2307055.lifelinkmesh.mesh;

import android.content.Context;

import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.nearby.NearbyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// One shared object for the whole app. It owns Nearby and the list of peers.
// Screens never talk to Nearby directly; they talk to the hub and listen to it.
public class MeshHub implements NearbyManager.Listener {

    private static MeshHub instance;

    public static synchronized MeshHub getInstance(Context context) {
        if (instance == null) {
            instance = new MeshHub(context.getApplicationContext());
        }
        return instance;
    }

    private final NearbyManager nearbyManager;
    // Screens that want to hear about events (MainActivity now, ChatActivity later).
    private final List<NearbyManager.Listener> uiListeners = new CopyOnWriteArrayList<>();
    // The single source of truth for who is nearby and their status.
    private final List<Peer> peers = new ArrayList<>();

    private MeshHub(Context appContext) {
        nearbyManager = new NearbyManager(appContext, this);
    }

    // ---- screens register and unregister ----

    public void addListener(NearbyManager.Listener listener) {
        uiListeners.add(listener);
    }

    public void removeListener(NearbyManager.Listener listener) {
        uiListeners.remove(listener);
    }

    // ---- commands from screens ----

    public String getLocalName() {
        return nearbyManager.getLocalName();
    }

    // Copies of the current peers, so a new screen can fill its list.
    public List<Peer> getPeers() {
        synchronized (peers) {
            List<Peer> copy = new ArrayList<>();
            for (Peer p : peers) {
                copy.add(new Peer(p.getEndpointId(), p.getName(), p.getStatus()));
            }
            return copy;
        }
    }

    public void start() {
        nearbyManager.start();
    }

    public void stop() {
        nearbyManager.stop();
        synchronized (peers) {
            peers.clear();
        }
    }

    public void connectTo(String endpointId, String name) {
        onConnectionStateChanged(endpointId, Peer.STATUS_CONNECTING);
        nearbyManager.connectTo(endpointId, name);
    }

    public void acceptConnection(String endpointId) {
        nearbyManager.acceptConnection(endpointId);
    }

    public void rejectConnection(String endpointId) {
        nearbyManager.rejectConnection(endpointId);
    }

    public void sendToAll(String text) {
        nearbyManager.sendToAll(text);
    }

    // ---- events coming from NearbyManager: update state, then tell the screens ----

    // Must be called inside synchronized (peers).
    private Peer find(String endpointId) {
        for (Peer p : peers) {
            if (p.getEndpointId().equals(endpointId)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void onEndpointFound(String endpointId, String name) {
        synchronized (peers) {
            if (find(endpointId) == null) {
                peers.add(new Peer(endpointId, name, Peer.STATUS_DISCOVERED));
            }
        }
        for (NearbyManager.Listener l : uiListeners) {
            l.onEndpointFound(endpointId, name);
        }
    }

    @Override
    public void onEndpointLost(String endpointId) {
        synchronized (peers) {
            Peer p = find(endpointId);
            if (p != null && Peer.STATUS_DISCOVERED.equals(p.getStatus())) {
                peers.remove(p);
            }
        }
        for (NearbyManager.Listener l : uiListeners) {
            l.onEndpointLost(endpointId);
        }
    }

    @Override
    public void onConnectionRequest(String endpointId, String name,
                                    String digits, boolean incoming) {
        synchronized (peers) {
            if (find(endpointId) == null) {
                peers.add(new Peer(endpointId, name, Peer.STATUS_CONNECTING));
            }
        }
        for (NearbyManager.Listener l : uiListeners) {
            l.onConnectionRequest(endpointId, name, digits, incoming);
        }
    }

    @Override
    public void onConnectionStateChanged(String endpointId, String state) {
        synchronized (peers) {
            Peer p = find(endpointId);
            if (p != null) {
                p.setStatus(state);
            } else {
                peers.add(new Peer(endpointId, "Unknown (" + endpointId + ")", state));
            }
        }
        for (NearbyManager.Listener l : uiListeners) {
            l.onConnectionStateChanged(endpointId, state);
        }
    }

    @Override
    public void onMessageReceived(String endpointId, String text) {
        for (NearbyManager.Listener l : uiListeners) {
            l.onMessageReceived(endpointId, text);
        }
    }

    @Override
    public void onLog(String message) {
        for (NearbyManager.Listener l : uiListeners) {
            l.onLog(message);
        }
    }
}