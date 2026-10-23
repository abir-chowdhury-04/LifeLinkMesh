package com.abir2307055.lifelinkmesh.mesh;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// A message as it travels between phones, converted to and from JSON text.
public class MeshMessage {

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_SOS = "SOS";
    public static final String TYPE_TOPO = "TOPO";// "these are my neighbours"
    public static final String TYPE_FILE_OFFER = "FILE_OFFER";   // "I want to send you a file"
    public static final String TYPE_FILE_REPLY = "FILE_REPLY";   // "accepted" or "declined"
    public static final String TYPE_FILE_START = "FILE_START";   // "the file payload has this id"

    public final String id;
    public final String type;
    public final String senderName;
    public final String text;
    public final long timestamp;
    public final int ttl;            // hop limit
    // SOS only (empty / false otherwise):
    public final String category;
    public final boolean hasLocation;
    public final double latitude;
    public final double longitude;
    public final float accuracy;
    // Names of the phones that handled this message before me, origin first.
    public final List<String> path;

    // Chat and TOPO messages.
    public MeshMessage(String id, String type, String senderName,
                       String text, long timestamp, int ttl) {
        this(id, type, senderName, text, timestamp, ttl, "", false, 0, 0, 0f,
                new ArrayList<>());
    }

    // SOS message.
    public MeshMessage(String id, String type, String senderName, String text,
                       long timestamp, int ttl, String category, boolean hasLocation,
                       double latitude, double longitude, float accuracy) {
        this(id, type, senderName, text, timestamp, ttl, category, hasLocation,
                latitude, longitude, accuracy, new ArrayList<>());
    }

    private MeshMessage(String id, String type, String senderName, String text,
                        long timestamp, int ttl, String category, boolean hasLocation,
                        double latitude, double longitude, float accuracy,
                        List<String> path) {
        this.id = id;
        this.type = type;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
        this.ttl = ttl;
        this.category = category;
        this.hasLocation = hasLocation;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
    }

    public String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type);
        json.put("senderName", senderName);
        json.put("text", text);
        json.put("timestamp", timestamp);
        json.put("ttl", ttl);
        JSONArray pathArray = new JSONArray();
        for (String name : path) {
            pathArray.put(name);
        }
        json.put("path", pathArray);
        if (TYPE_SOS.equals(type)) {
            json.put("category", category);
            json.put("hasLocation", hasLocation);
            if (hasLocation) {
                json.put("latitude", latitude);
                json.put("longitude", longitude);
                json.put("accuracy", (double) accuracy);
            }
        }
        return json.toString();
    }

    public static MeshMessage fromJson(String jsonText) throws JSONException {
        JSONObject json = new JSONObject(jsonText);
        List<String> path = new ArrayList<>();
        JSONArray pathArray = json.optJSONArray("path");
        if (pathArray != null) {
            for (int i = 0; i < pathArray.length(); i++) {
                path.add(pathArray.getString(i));
            }
        }
        return new MeshMessage(
                json.getString("id"),
                json.getString("type"),
                json.getString("senderName"),
                json.getString("text"),
                json.getLong("timestamp"),
                json.getInt("ttl"),
                json.optString("category", ""),
                json.optBoolean("hasLocation", false),
                json.optDouble("latitude", 0),
                json.optDouble("longitude", 0),
                (float) json.optDouble("accuracy", 0),
                path);
    }

    // A copy with a different ttl (used when relaying).
    public MeshMessage withTtl(int newTtl) {
        return new MeshMessage(id, type, senderName, text, timestamp, newTtl,
                category, hasLocation, latitude, longitude, accuracy, path);
    }

    // A copy with a different path.
    public MeshMessage withPath(List<String> newPath) {
        return new MeshMessage(id, type, senderName, text, timestamp, ttl,
                category, hasLocation, latitude, longitude, accuracy, newPath);
    }

    // A copy with one more name at the end of the path (a relay adds itself).
    public MeshMessage appendToPath(String name) {
        List<String> longer = new ArrayList<>(path);
        longer.add(name);
        return withPath(longer);
    }
}