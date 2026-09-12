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
    private static final int DATABASE_VERSION = 1;

    public PacketRepository(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE packets (" +
                        "packet_id TEXT PRIMARY KEY, " +
                        "type TEXT, " +
                        "ttl INTEGER, " +
                        "timestamp INTEGER, " +
                        "latitude REAL, " +
                        "longitude REAL, " +
                        "received_at INTEGER" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS packets");
        onCreate(db);
    }

    public boolean hasPacket(String packetId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("packets", new String[]{"packet_id"}, "packet_id=?",
                new String[]{packetId}, null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public long savePacket(EmergencyPacket packet) {
        if (packet == null) {
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("packet_id", packet.getPacketId());
        values.put("type", packet.getType());
        values.put("ttl", packet.getTtl());
        values.put("timestamp", packet.getTimestamp());
        values.put("latitude", packet.getLatitude());
        values.put("longitude", packet.getLongitude());
        values.put("received_at", System.currentTimeMillis());

        return db.insertWithOnConflict("packets", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<EmergencyPacket> getAllPackets() {
        List<EmergencyPacket> packets = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("packets", null, null, null, null, null, "received_at DESC");

        while (cursor.moveToNext()) {
            EmergencyPacket packet = new EmergencyPacket(
                    cursor.getString(cursor.getColumnIndexOrThrow("packet_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("ttl")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    "db"
            );
            packets.add(packet);
        }
        cursor.close();
        return packets;
    }

    public int removeExpiredPackets(long oldestTimestampMillis) {
        return getWritableDatabase().delete("packets", "timestamp<?",
                new String[]{String.valueOf(oldestTimestampMillis)});
    }
}
