package com.abir2307055.lifelinkmesh.mesh;

import android.content.Context;

import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.ChatMessage;
import com.abir2307055.lifelinkmesh.data.MessageDao;
import com.abir2307055.lifelinkmesh.data.SosAlert;
import com.abir2307055.lifelinkmesh.data.SosDao;
import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.nearby.NearbyManager;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// One shared object for the whole app. It owns Nearby, the peer list, message saving
// and relaying. Screens never talk to Nearby directly; they talk to the hub.
public class MeshHub implements NearbyManager.Listener {

    // Screens showing chat implement this to be told about each new chat message.
    public interface ChatListener {
        void onChatMessage(ChatMessage message);
    }

    // Screens showing SOS alerts implement this.
    public interface SosListener {
        void onSos(SosAlert alert);
    }

    private static final int DEFAULT_TTL = 5;   // chat: up to 5 hops
    private static final int SOS_TTL = 8;       // SOS reaches further than chat

    private static MeshHub instance;

    private final Context appContext;

    public static synchronized MeshHub getInstance(Context context) {
        if (instance == null) {
            instance = new MeshHub(context.getApplicationContext());
        }
        return instance;
    }

    private final NearbyManager nearbyManager;
    private final MessageDao dao;
    private final SosDao sosDao;
    // One background thread for all database work done by the hub.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final List<NearbyManager.Listener> uiListeners = new CopyOnWriteArrayList<>();
    private final List<ChatListener> chatListeners = new CopyOnWriteArrayList<>();
    private final List<SosListener> sosListeners = new CopyOnWriteArrayList<>();
    // The single source of truth for who is nearby and their status.
    private final List<Peer> peers = new ArrayList<>();
    // Duplicate cache: ids of messages this phone has already handled.
    private final Set<String> seenIds = ConcurrentHashMap.newKeySet();

    private MeshHub(Context appContext) {
        this.appContext = appContext;
        nearbyManager = new NearbyManager(appContext, this);
        AppDatabase db = AppDatabase.getInstance(appContext);
        dao = db.messageDao();
        sosDao = db.sosDao();
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

    public void addSosListener(SosListener listener) {
        sosListeners.add(listener);
    }

    public void removeSosListener(SosListener listener) {
        sosListeners.remove(listener);
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

    // Forgets idle peers and restarts the search, without dropping live connections.
    public void refresh() {
        synchronized (peers) {
            List<Peer> keep = new ArrayList<>();
            for (Peer p : peers) {
                String s = p.getStatus();
                if (Peer.STATUS_CONNECTED.equals(s) || Peer.STATUS_CONNECTING.equals(s)) {
                    keep.add(p);
                }
            }
            peers.clear();
            peers.addAll(keep);
        }
        nearbyManager.refreshDiscovery();
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

    // Saves my chat message in Room, shows it, and sends it to every connected phone.
    public void sendChat(String text) {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        String senderName = nearbyManager.getLocalName();
        seenIds.add(id);   // so my own message is ignored if it comes back around

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

    // Saves my SOS in Room, shows it, and sends it with a longer hop limit.
    public void sendSos(String category, String note, boolean hasLocation,
                        double latitude, double longitude, float accuracy) {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        String senderName = nearbyManager.getLocalName();
        seenIds.add(id);

        SosAlert alert = new SosAlert(id, senderName, category, note, now,
                hasLocation, latitude, longitude, accuracy, true);
        dbExecutor.execute(() -> {
            sosDao.insert(alert);
            notifySos(alert);
        });

        MeshMessage wire = new MeshMessage(id, MeshMessage.TYPE_SOS, senderName, note,
                now, SOS_TTL, category, hasLocation, latitude, longitude, accuracy);
        try {
            nearbyManager.sendToAll(wire.toJson());
        } catch (JSONException e) {
            onLog("Could not build SOS: " + e.getMessage());
        }
    }

    private void notifyChat(ChatMessage message) {
        for (ChatListener l : chatListeners) {
            l.onChatMessage(message);
        }
    }

    private void notifySos(SosAlert alert) {
        for (SosListener l : sosListeners) {
            l.onSos(alert);
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
            Peer existing = find(endpointId);
            if (existing == null) {
                peers.add(new Peer(endpointId, name, Peer.STATUS_DISCOVERED));
            } else if (Peer.STATUS_DISCONNECTED.equals(existing.getStatus())
                    || Peer.STATUS_FAILED.equals(existing.getStatus())) {
                existing.setStatus(Peer.STATUS_DISCOVERED);   // seen again after it dropped
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

    // Raw text arrived from a peer: read the JSON, drop duplicates, save, show, relay.
    @Override
    public void onMessageReceived(String endpointId, String text) {
        try {
            MeshMessage wire = MeshMessage.fromJson(text);
            boolean isChat = MeshMessage.TYPE_CHAT.equals(wire.type);
            boolean isSos = MeshMessage.TYPE_SOS.equals(wire.type);
            if (!isChat && !isSos) {
                onLog("Ignored message type " + wire.type);
                return;
            }
            // Layer 1: in-memory duplicate cache.
            if (!seenIds.add(wire.id)) {
                onLog("Duplicate ignored (seen before)");
                return;
            }
            dbExecutor.execute(() -> {
                // Layer 2: Room ignores an id it already stores (returns -1).
                boolean isNew;
                if (isSos) {
                    SosAlert alert = new SosAlert(wire.id, wire.senderName, wire.category,
                            wire.text, wire.timestamp, wire.hasLocation, wire.latitude,
                            wire.longitude, wire.accuracy, false);
                    isNew = sosDao.insert(alert) != -1;
                    if (isNew) {
                        onLog("SOS " + wire.category + " from " + wire.senderName
                                + " (ttl " + wire.ttl + ")");
                        notifySos(alert);
                        SosNotifier.show(appContext, alert);
                    }
                } else {
                    ChatMessage received = new ChatMessage(
                            wire.id, wire.senderName, wire.text, wire.timestamp, false);
                    isNew = dao.insert(received) != -1;
                    if (isNew) {
                        onLog("Chat from " + wire.senderName + " (ttl " + wire.ttl + ")");
                        notifyChat(received);
                    }
                }
                if (!isNew) {
                    onLog("Duplicate ignored (already saved)");
                    return;
                }
                relay(endpointId, wire);
            });
        } catch (JSONException e) {
            onLog("Unreadable message: " + e.getMessage());
        }
    }

    // Forwards a new message to everyone except the phone it came from.
    private void relay(String fromEndpointId, MeshMessage wire) {
        if (wire.ttl <= 1) {
            onLog("TTL used up, not relaying");
            return;
        }
        try {
            String json = wire.withTtl(wire.ttl - 1).toJson();
            nearbyManager.sendToAllExcept(fromEndpointId, json);
            onLog("Relayed with ttl " + (wire.ttl - 1));
        } catch (JSONException e) {
            onLog("Could not relay: " + e.getMessage());
        }
    }

    @Override
    public void onLog(String message) {
        for (NearbyManager.Listener l : uiListeners) {
            l.onLog(message);
        }
    }
}