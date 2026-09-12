package com.example.bleprototype.network;

import com.example.bleprototype.model.EmergencyPacket;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class PacketManager {
    /**
     * Legacy BLE advertisements allow 31 bytes.  A 128-bit service-data UUID
     * consumes 18 bytes of that budget, leaving at most 13 bytes including the
     * AD header.  This packet is deliberately kept to 10 bytes.
     */
    public static final int ENCODED_PACKET_SIZE = 10;
    private static final int TYPE_MEDICAL = 1;
    private static final int TYPE_FIRE = 2;
    private static final int TYPE_FLOOD = 3;
    private static final int TYPE_ACCIDENT = 4;
    private static final int TYPE_EARTHQUAKE = 5;
    private static final int TYPE_SHELTER = 6;
    private static final int TYPE_OTHER = 7;
    private static final int SEVERITY_LOW = 1;
    private static final int SEVERITY_MEDIUM = 2;
    private static final int SEVERITY_CRITICAL = 3;

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

    public byte[] encodeGattPayload(EmergencyPacket packet) {
        if (!validatePacket(packet)) {
            throw new IllegalArgumentException("Cannot encode an invalid GATT packet");
        }
        JSONObject message = new JSONObject();
        try {
            message.put("packet_id", packet.getPacketId());
            message.put("type", packet.getType());
            message.put("severity", packet.getSeverity());
            message.put("location", packet.getLocation());
            message.put("created_at", packet.getCreatedAt());
            message.put("received_at", packet.getReceivedAt() > 0 ? packet.getReceivedAt() : System.currentTimeMillis());
            message.put("ttl", packet.getTtl());
            message.put("source_device", packet.getSourceDevice());
            message.put("relay_count", packet.getRelayCount());
            message.put("status", packet.getStatus());
            message.put("description", packet.getDescription());
            message.put("reporter_name", packet.getReporterName());
            message.put("contact_info", packet.getContactInfo());
            message.put("people_affected", packet.getPeopleAffected());
            message.put("assistance_needed", packet.getAssistanceNeeded());
            message.put("notes", packet.getNotes());
            message.put("kind", "packet");
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not serialize packet message", exception);
        }
        return message.toString().getBytes(StandardCharsets.UTF_8);
    }

    public EmergencyPacket decodeTransportPayload(byte[] payload, String sourceAddress) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Transport payload is empty");
        }
        if (payload.length == ENCODED_PACKET_SIZE) {
            return decodeAdvertisement(payload, sourceAddress);
        }
        String json = new String(payload, StandardCharsets.UTF_8);
        try {
            JSONObject object = new JSONObject(json);
                EmergencyPacket packet = new EmergencyPacket(
                    object.optString("packet_id", UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US)),
                    object.optString("type", "OTHER"),
                    object.optString("severity", "MEDIUM"),
                    object.optString("location", "Unknown"),
                    object.optLong("created_at", System.currentTimeMillis()),
                    object.optLong("received_at", System.currentTimeMillis()),
                    object.optInt("ttl", 1),
                    object.optString("source_device", sourceAddress),
                    object.optInt("relay_count", 0),
                    object.optString("status", "PENDING")
            );
                    return packet.withReportDetails(
                        object.optString("description", ""),
                        object.optString("reporter_name", ""),
                        object.optString("contact_info", ""),
                        object.optString("people_affected", ""),
                        object.optString("assistance_needed", ""),
                        object.optString("notes", ""));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid GATT transport packet", exception);
        }
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
                .put((byte) ((typeToCode(packet.getType()) << 2)
                    | severityToCode(packet.getSeverity())))
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
        int typeAndSeverity = Byte.toUnsignedInt(bytes.get());
        String type = codeToType(typeAndSeverity >> 2);
        String severity = codeToSeverity(typeAndSeverity & 0x03);
        int ttl = Byte.toUnsignedInt(bytes.get());
        long timestamp = Integer.toUnsignedLong(bytes.getInt()) * 1000L;
        return new EmergencyPacket(packetId, type, severity, "Unknown", timestamp,
            System.currentTimeMillis(), ttl, sourceAddress, 0, "PENDING");
    }

    public String generatePacketId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);
    }

    public boolean validatePacket(EmergencyPacket packet) {
        return packet != null && packet.getPacketId() != null
                && packet.getPacketId().matches("[0-9A-Fa-f]{8}")
                && isSupportedType(packet.getType())
                && packet.getTtl() >= 0 && packet.getTtl() <= 255;
    }

    public boolean isSupportedType(String type) {
        return "MEDICAL".equalsIgnoreCase(type)
                || "FIRE".equalsIgnoreCase(type)
                || "FLOOD".equalsIgnoreCase(type)
                || "ACCIDENT".equalsIgnoreCase(type)
                || "EARTHQUAKE".equalsIgnoreCase(type)
                || "SHELTER".equalsIgnoreCase(type)
                || "OTHER".equalsIgnoreCase(type);
    }

    private int typeToCode(String type) {
        if ("MEDICAL".equalsIgnoreCase(type)) {
            return TYPE_MEDICAL;
        }
        if ("FIRE".equalsIgnoreCase(type)) {
            return TYPE_FIRE;
        }
        if ("FLOOD".equalsIgnoreCase(type)) {
            return TYPE_FLOOD;
        }
        if ("ACCIDENT".equalsIgnoreCase(type)) {
            return TYPE_ACCIDENT;
        }
        if ("EARTHQUAKE".equalsIgnoreCase(type)) {
            return TYPE_EARTHQUAKE;
        }
        if ("SHELTER".equalsIgnoreCase(type)) {
            return TYPE_SHELTER;
        }
        return TYPE_OTHER;
    }

    private int severityToCode(String severity) {
        if ("LOW".equalsIgnoreCase(severity)) {
            return SEVERITY_LOW;
        }
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return SEVERITY_CRITICAL;
        }
        return SEVERITY_MEDIUM;
    }

    private String codeToType(int code) {
        switch (code) {
            case TYPE_MEDICAL:
                return "MEDICAL";
            case TYPE_FIRE:
                return "FIRE";
            case TYPE_FLOOD:
                return "FLOOD";
            case TYPE_ACCIDENT:
                return "ACCIDENT";
            case TYPE_EARTHQUAKE:
                return "EARTHQUAKE";
            case TYPE_SHELTER:
                return "SHELTER";
            default:
                return "OTHER";
        }
    }

    private String codeToSeverity(int code) {
        switch (code) {
            case SEVERITY_LOW:
                return "LOW";
            case SEVERITY_CRITICAL:
                return "CRITICAL";
            default:
                return "MEDIUM";
        }
    }
}
