package com.abir2307055.lifelinkmesh.mesh;

import org.json.JSONException;
import org.json.JSONObject;

// A message as it travels between phones, converted to and from JSON text.
public class MeshMessage {

    public static final String TYPE_CHAT = "CHAT";

    public final String id;
    public final String type;
    public final String senderName;
    public final String text;
    public final long timestamp;
    public final int ttl;      // hop limit; used from Phase 4 (relay)

    public MeshMessage(String id, String type, String senderName,
                       String text, long timestamp, int ttl) {
        this.id = id;
        this.type = type;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
        this.ttl = ttl;
    }

    public String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type);
        json.put("senderName", senderName);
        json.put("text", text);
        json.put("timestamp", timestamp);
        json.put("ttl", ttl);
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
                json.getInt("ttl"));
    }
}