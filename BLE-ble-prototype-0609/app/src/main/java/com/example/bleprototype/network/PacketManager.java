package com.example.bleprototype.network;

import com.example.bleprototype.model.EmergencyPacket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.UUID;

public class PacketManager {
    /**
     * Legacy BLE advertisements allow 31 bytes.  A 128-bit service-data UUID
     * consumes 18 bytes of that budget, leaving at most 13 bytes including the
     * AD header.  This packet is deliberately kept to 10 bytes.
     */
    public static final int ENCODED_PACKET_SIZE = 10;
    private static final byte TYPE_MEDICAL = 1;
    private static final byte TYPE_OTHER = 127;

    public EmergencyPacket decodePacket(String rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new IllegalArgumentException("Packet payload is empty");
        }

        String[] parts = rawPayload.split("\\|");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid packet payload: " + rawPayload);
        }

        String packetId = parts[0];
        String type = parts[1];
        int ttl;

        if (!parts[2].startsWith("TTL=")) {
            throw new IllegalArgumentException("Packet TTL is missing");
        }
        try {
            ttl = Integer.parseInt(parts[2].substring(4));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Packet TTL is invalid", exception);
        }

        return new EmergencyPacket(packetId, type, ttl, System.currentTimeMillis(), 0.0, 0.0, "unknown");
    }

    public String encodePacket(EmergencyPacket packet) {
        if (!validatePacket(packet)) {
            throw new IllegalArgumentException("Cannot encode an invalid packet");
        }
        return String.format(Locale.US, "%s|%s|TTL=%d", packet.getPacketId(), packet.getType(), packet.getTtl());
    }

    public byte[] encodeForAdvertisement(EmergencyPacket packet) {
        if (!validatePacket(packet)) {
            throw new IllegalArgumentException("Cannot encode an invalid packet");
        }
        long packetId;
        try {
            packetId = Long.parseLong(packet.getPacketId(), 16);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Packet ID must be eight hexadecimal characters", exception);
        }
        if (packetId > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Packet ID is too large");
        }

        return ByteBuffer.allocate(ENCODED_PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)
                .putInt((int) packetId)
                .put(typeToCode(packet.getType()))
                .put((byte) packet.getTtl())
                .putInt((int) (packet.getTimestamp() / 1000L))
                .array();
    }

    public EmergencyPacket decodeAdvertisement(byte[] payload, String sourceAddress) {
        if (payload == null || payload.length != ENCODED_PACKET_SIZE) {
            throw new IllegalArgumentException("Invalid BLE packet length");
        }
        ByteBuffer bytes = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        String packetId = String.format(Locale.US, "%08X", bytes.getInt());
        String type = codeToType(bytes.get());
        int ttl = Byte.toUnsignedInt(bytes.get());
        long timestamp = Integer.toUnsignedLong(bytes.getInt()) * 1000L;
        return new EmergencyPacket(packetId, type, ttl, timestamp, 0.0, 0.0, sourceAddress);
    }

    public String generatePacketId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);
    }

    public boolean validatePacket(EmergencyPacket packet) {
        return packet != null && packet.getPacketId() != null
                && packet.getPacketId().matches("[0-9A-Fa-f]{8}")
                && packet.getType() != null && !packet.getType().trim().isEmpty()
                && packet.getTtl() >= 0 && packet.getTtl() <= 255;
    }

    private byte typeToCode(String type) {
        return "MEDICAL".equalsIgnoreCase(type) ? TYPE_MEDICAL : TYPE_OTHER;
    }

    private String codeToType(byte code) {
        return code == TYPE_MEDICAL ? "MEDICAL" : "OTHER";
    }
}
