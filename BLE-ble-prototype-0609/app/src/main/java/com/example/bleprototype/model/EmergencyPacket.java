package com.example.bleprototype.model;

import java.util.Locale;
import java.util.UUID;

public class EmergencyPacket {
    private final String packetId;
    private final String type;
    private final int ttl;
    private final long timestamp;
    private final double latitude;
    private final double longitude;
    private final String sourceAddress;

    public EmergencyPacket(String packetId, String type, int ttl, long timestamp,
                          double latitude, double longitude, String sourceAddress) {
        this.packetId = packetId;
        this.type = type;
        this.ttl = ttl;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sourceAddress = sourceAddress;
    }

    public String getPacketId() {
        return packetId;
    }

    public String getType() {
        return type;
    }

    public int getTtl() {
        return ttl;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public EmergencyPacket withDecrementedTtl() {
        return new EmergencyPacket(packetId, type, Math.max(0, ttl - 1), timestamp,
                latitude, longitude, sourceAddress);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "PacketId=%s, Type=%s, TTL=%d, Lat=%.5f, Lng=%.5f, Source=%s",
                packetId, type, ttl, latitude, longitude, sourceAddress);
    }
}
