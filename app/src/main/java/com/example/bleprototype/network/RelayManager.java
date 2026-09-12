package com.example.bleprototype.network;

import com.example.bleprototype.model.EmergencyPacket;

import java.util.HashSet;
import java.util.Set;

public class RelayManager {
    private final Set<String> seenPacketIds = new HashSet<>();

    public boolean isDuplicate(String packetId) {
        return seenPacketIds.contains(packetId);
    }

    public EmergencyPacket receivePacket(EmergencyPacket packet) {
        if (packet == null) {
            return null;
        }

        if (isDuplicate(packet.getPacketId())) {
            return null;
        }

        seenPacketIds.add(packet.getPacketId());
        return packet;
    }

    public void markSeen(String packetId) {
        if (packetId != null) {
            seenPacketIds.add(packetId);
        }
    }

    public EmergencyPacket decrementTtl(EmergencyPacket packet) {
        if (packet == null || packet.getTtl() <= 0) {
            return null;
        }

        return packet.withDecrementedTtl();
    }

    public boolean shouldRelay(EmergencyPacket packet) {
        return packet != null && packet.getTtl() > 0;
    }
}
