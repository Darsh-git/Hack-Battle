package com.example.disastermesh;

import android.content.Context;
import java.util.List;

public class PacketRepository {

    private final ReportDao dao;

    public PacketRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.dao = db.reportDao();
    }

    // Thread-safe duplicate checking and saving.
    // Returns true if packet was genuinely NEW; false if duplicate or expired.
    public synchronized boolean saveIfNew(EmergencyReport packet) {
        if (packet.getTtl() <= 0) {
            return false;
        }

        long result = dao.insertPacket(packet);
        // OnConflictStrategy.IGNORE returns -1 if row already exists
        return result != -1;
    }

    public synchronized boolean hasPacket(String packetId) {
        return dao.hasPacket(packetId) > 0;
    }

    public synchronized List<EmergencyReport> getPendingRelays() {
        return dao.getPendingRelays();
    }

    public synchronized void markRelayed(String packetId) {
        dao.markRelayed(packetId);
    }

    public synchronized int removeExpiredPackets() {
        return dao.removeExpiredPackets(System.currentTimeMillis());
    }

    public synchronized List<EmergencyReport> getAllPackets() {
        return dao.getAllPackets();
    }
}
