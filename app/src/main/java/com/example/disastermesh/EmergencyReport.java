package com.example.disastermesh;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "packets")
public class EmergencyReport {

    @PrimaryKey
    @NonNull
    private String packetId; // Unique Primary Key

    private String type;
    private String severity; // "LOW", "MEDIUM", "CRITICAL"
    private String location;
    private long createdAt;
    private long receivedAt;
    private int ttl;
    private String sourceDevice;
    private int relayCount;
    private String status; // "PENDING", "RELAYED", "EXPIRED"

    public EmergencyReport(@NonNull String packetId, String type, String severity, String location,
                           long createdAt, long receivedAt, int ttl, String sourceDevice,
                           int relayCount, String status) {
        this.packetId = packetId;
        this.type = type;
        this.severity = severity;
        this.location = location;
        this.createdAt = createdAt;
        this.receivedAt = receivedAt;
        this.ttl = ttl;
        this.sourceDevice = sourceDevice;
        this.relayCount = relayCount;
        this.status = status;
    }

    // Getters and Setters
    @NonNull public String getPacketId() { return packetId; }
    public void setPacketId(@NonNull String packetId) { this.packetId = packetId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getReceivedAt() { return receivedAt; }
    public void setReceivedAt(long receivedAt) { this.receivedAt = receivedAt; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public String getSourceDevice() { return sourceDevice; }
    public void setSourceDevice(String sourceDevice) { this.sourceDevice = sourceDevice; }

    public int getRelayCount() { return relayCount; }
    public void setRelayCount(int relayCount) { this.relayCount = relayCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
