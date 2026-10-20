package com.abir2307055.lifelinkmesh.mesh;

import android.content.Context;

import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.ChatMessage;
import com.abir2307055.lifelinkmesh.data.MessageDao;
import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.nearby.NearbyManager;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// One shared object for the whole app. It owns Nearby, the peer list and message saving.
// Screens never talk to Nearby directly; they talk to the hub and listen to it.
public class MeshHub implements NearbyManager.Listener {

    // Screens showing chat implement this to be told about each new message.
    public interface ChatListener {
        void onChatMessage(ChatMessage message);
    }

    // Placeholder hop limit; it starts to matter in Phase 4 (relay).
    private static final int DEFAULT_TTL = 5;

    private static MeshHub instance;

    public static synchronized MeshHub getInstance(Context context) {
        if (instance == null) {
            instance = new MeshHub(context.getApplicationContext());
        }
        return instance;
    }

    private final NearbyManager nearbyManager;
    private final MessageDao dao;
    // One background thread for all database work done by the hub.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final List<NearbyManager.Listener> uiListeners = new CopyOnWriteArrayList<>();
    private final List<ChatListener> chatListeners = new CopyOnWriteArrayList<>();
    // The single source of truth for who is nearby and their status.
    private final List<Peer> peers = new ArrayList<>();

    private MeshHub(Context appContext) {
        nearbyManager = new NearbyManager(appContext, this);
        dao = AppDatabase.getInstance(appContext).messageDao();
    }

    // ---- screens register and unregister ----

    public void addListener(NearbyManager.Listener listener) {
        uiListeners.add(listener);
    }

    public void removeListener(NearbyManager.Listener listener) {
        uiListeners.remove(listener);
    }

    public void addChatListener(ChatListener listener) {
        chatListeners.add(listener);
    }

    public void removeChatListener(ChatListener listener) {
        chatListeners.remove(listener);
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

    // Saves my message in Room, shows it, and sends it to every connected phone.
    public void sendChat(String text) {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        String senderName = nearbyManager.getLocalName();

        ChatMessage saved = new ChatMessage(id, senderName, text, now, true);
        dbExecutor.execute(() -> {
            dao.insert(saved);
            notifyChat(saved);
        });

        MeshMessage wire = new MeshMessage(
                id, MeshMessage.TYPE_CHAT, senderName, text, now, DEFAULT_TTL);
        try {
            nearbyManager.sendToAll(wire.toJson());
        } catch (JSONException e) {
            onLog("Could not build message: " + e.getMessage());
        }
    }

    private void notifyChat(ChatMessage message) {
        for (ChatListener l : chatListeners) {
            l.onChatMessage(message);
        }
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

    // Raw text arrived from a peer: read the JSON, save it, tell the chat screen.
    @Override
    public void onMessageReceived(String endpointId, String text) {
        try {
            MeshMessage wire = MeshMessage.fromJson(text);
            if (!MeshMessage.TYPE_CHAT.equals(wire.type)) {
                onLog("Ignored message type " + wire.type);
                return;
            }
            ChatMessage received = new ChatMessage(
                    wire.id, wire.senderName, wire.text, wire.timestamp, false);
            dbExecutor.execute(() -> {
                long rowId = dao.insert(received);
                if (rowId == -1) {
                    onLog("Duplicate message ignored");
                } else {
                    onLog("Chat from " + wire.senderName);
                    notifyChat(received);
                }
            });
        } catch (JSONException e) {
            onLog("Unreadable message: " + e.getMessage());
        }
    }

    @Override
    public void onLog(String message) {
        for (NearbyManager.Listener l : uiListeners) {
            l.onLog(message);
        }
    }
}