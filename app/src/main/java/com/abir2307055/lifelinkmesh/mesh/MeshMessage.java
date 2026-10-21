package com.abir2307055.lifelinkmesh.mesh;

import org.json.JSONException;
import org.json.JSONObject;

// A message as it travels between phones, converted to and from JSON text.
public class MeshMessage {

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_SOS = "SOS";

    public final String id;
    public final String type;
    public final String senderName;
    public final String text;
    public final long timestamp;
    public final int ttl;            // hop limit
    // SOS only (empty / false for chat):
    public final String category;
    public final boolean hasLocation;
    public final double latitude;
    public final double longitude;
    public final float accuracy;

    // Chat message.
    public MeshMessage(String id, String type, String senderName,
                       String text, long timestamp, int ttl) {
        this(id, type, senderName, text, timestamp, ttl, "", false, 0, 0, 0f);
    }

    // Full constructor (SOS).
    public MeshMessage(String id, String type, String senderName, String text,
                       long timestamp, int ttl, String category, boolean hasLocation,
                       double latitude, double longitude, float accuracy) {
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
    }

    public String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type);
        json.put("senderName", senderName);
        json.put("text", text);
        json.put("timestamp", timestamp);
        json.put("ttl", ttl);
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
                (float) json.optDouble("accuracy", 0));
    }

    // A copy of this message with a different ttl (used when relaying).
    public MeshMessage withTtl(int newTtl) {
        return new MeshMessage(id, type, senderName, text, timestamp, newTtl,
                category, hasLocation, latitude, longitude, accuracy);
    }
}