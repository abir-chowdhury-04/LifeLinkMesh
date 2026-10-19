package com.abir2307055.lifelinkmesh.model;

public class Peer {
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