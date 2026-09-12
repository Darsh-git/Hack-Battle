package com.example.bleprototype.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.bleprototype.model.EmergencyPacket;

import java.util.ArrayList;
import java.util.List;

public class PacketRepository extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "ble_packets.db";
    private static final int DATABASE_VERSION = 3;
    private static final long LOW_PRIORITY_RETENTION_MS = 24L * 60L * 60L * 1000L;
    private static final long CRITICAL_RETENTION_MS = 7L * LOW_PRIORITY_RETENTION_MS;

    public PacketRepository(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE packets (" +
                    "packet_id TEXT PRIMARY KEY NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "severity TEXT NOT NULL, " +
                    "location TEXT, " +
                    "created_at INTEGER NOT NULL, " +
                    "received_at INTEGER NOT NULL, " +
                    "ttl INTEGER NOT NULL, " +
                    "source_device TEXT, " +
                    "relay_count INTEGER NOT NULL DEFAULT 0, " +
                    "status TEXT NOT NULL DEFAULT 'PENDING', " +
                    "description TEXT, reporter_name TEXT, contact_info TEXT, " +
                    "people_affected TEXT, assistance_needed TEXT, notes TEXT" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE packets RENAME TO packets_legacy");
            onCreate(db);
            db.execSQL("INSERT OR IGNORE INTO packets " +
                    "(packet_id, type, severity, location, created_at, received_at, ttl, source_device) " +
                    "SELECT packet_id, type, 'MEDIUM', printf('%.5f,%.5f', latitude, longitude), " +
                    "timestamp, received_at, ttl, 'unknown' FROM packets_legacy");
            db.execSQL("DROP TABLE packets_legacy");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE packets ADD COLUMN description TEXT");
            db.execSQL("ALTER TABLE packets ADD COLUMN reporter_name TEXT");
            db.execSQL("ALTER TABLE packets ADD COLUMN contact_info TEXT");
            db.execSQL("ALTER TABLE packets ADD COLUMN people_affected TEXT");
            db.execSQL("ALTER TABLE packets ADD COLUMN assistance_needed TEXT");
            db.execSQL("ALTER TABLE packets ADD COLUMN notes TEXT");
        }
    }

    public synchronized boolean hasPacket(String packetId) {
        if (packetId == null) {
            return false;
        }
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("packets", new String[]{"packet_id"}, "packet_id=?",
                new String[]{packetId}, null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public synchronized boolean saveIfNew(EmergencyPacket packet) {
        if (packet == null || packet.getPacketId() == null || packet.getTtl() <= 0) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("packet_id", packet.getPacketId());
        values.put("type", packet.getType());
        values.put("severity", packet.getSeverity());
        values.put("location", packet.getLocation());
        values.put("created_at", packet.getCreatedAt());
        values.put("received_at", packet.getReceivedAt() > 0
                ? packet.getReceivedAt() : System.currentTimeMillis());
        values.put("ttl", packet.getTtl());
        values.put("source_device", packet.getSourceDevice());
        values.put("relay_count", packet.getRelayCount());
        values.put("status", packet.getStatus());
        values.put("description", packet.getDescription());
        values.put("reporter_name", packet.getReporterName());
        values.put("contact_info", packet.getContactInfo());
        values.put("people_affected", packet.getPeopleAffected());
        values.put("assistance_needed", packet.getAssistanceNeeded());
        values.put("notes", packet.getNotes());

        // The primary key and conflict policy make this check-and-save atomic.
        return db.insertWithOnConflict("packets", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public synchronized long savePacket(EmergencyPacket packet) {
        return saveIfNew(packet) ? 1 : -1;
    }

    public synchronized List<EmergencyPacket> getPendingRelays() {
        return readPackets("status=? AND ttl>0", new String[]{"PENDING"});
    }

    public synchronized void markRelayed(String packetId) {
        if (packetId == null) {
            return;
        }
        getWritableDatabase().execSQL(
                "UPDATE packets SET status='RELAYED', relay_count=relay_count+1 WHERE packet_id=?",
                new Object[]{packetId});
    }

    public synchronized int removeExpiredPackets() {
        long now = System.currentTimeMillis();
        return getWritableDatabase().delete("packets",
                "(severity=? AND ? - created_at > ?) OR " +
                        "(severity<>? AND ? - created_at > ?) OR ttl<=0",
                new String[]{"CRITICAL", String.valueOf(now), String.valueOf(CRITICAL_RETENTION_MS),
                        "CRITICAL", String.valueOf(now), String.valueOf(LOW_PRIORITY_RETENTION_MS)});
    }

    public synchronized List<EmergencyPacket> getAllPackets() {
        return readPackets(null, null);
    }

    private List<EmergencyPacket> readPackets(String selection, String[] selectionArgs) {
        List<EmergencyPacket> packets = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("packets", null, selection, selectionArgs, null, null, "received_at DESC");

        while (cursor.moveToNext()) {
            EmergencyPacket packet = new EmergencyPacket(
                    cursor.getString(cursor.getColumnIndexOrThrow("packet_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    cursor.getString(cursor.getColumnIndexOrThrow("severity")),
                    cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("ttl")),
                    cursor.getString(cursor.getColumnIndexOrThrow("source_device")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("relay_count")),
                    cursor.getString(cursor.getColumnIndexOrThrow("status"))
            );
                    packets.add(packet.withReportDetails(
                        cursor.getString(cursor.getColumnIndexOrThrow("description")),
                        cursor.getString(cursor.getColumnIndexOrThrow("reporter_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("contact_info")),
                        cursor.getString(cursor.getColumnIndexOrThrow("people_affected")),
                        cursor.getString(cursor.getColumnIndexOrThrow("assistance_needed")),
                        cursor.getString(cursor.getColumnIndexOrThrow("notes"))));
        }
        cursor.close();
        return packets;
    }
}
