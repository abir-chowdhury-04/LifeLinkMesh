package com.abir2307055.lifelinkmesh.model;

public class Peer {
    public static final String STATUS_DISCOVERED = "Discovered";
    public static final String STATUS_CONNECTING = "Connecting";
    public static final String STATUS_CONNECTED = "Connected";
    public static final String STATUS_DISCONNECTED = "Disconnected";
    public static final String STATUS_FAILED = "Failed";

    private final String endpointId;
    private final String name;
    private String status;

    public Peer(String endpointId, String name, String status) {
        this.endpointId = endpointId;
        this.name = name;
        this.status = status;
    }

    public String getEndpointId() { return endpointId; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}