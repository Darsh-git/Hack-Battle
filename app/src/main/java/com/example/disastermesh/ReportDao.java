package com.example.disastermesh;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertPacket(EmergencyReport packet);

    @Query("SELECT COUNT(*) FROM packets WHERE packetId = :packetId")
    int hasPacket(String packetId);

    @Query("SELECT * FROM packets WHERE status = 'PENDING' AND ttl > 0")
    List<EmergencyReport> getPendingRelays();

    @Query("UPDATE packets SET status = 'RELAYED', relayCount = relayCount + 1 WHERE packetId = :packetId")
    void markRelayed(String packetId);

    // Retention: Remove low-priority after 24 hrs (86400000 ms), critical retained longer
    @Query("DELETE FROM packets WHERE (severity != 'CRITICAL' AND :currentTime - createdAt > 86400000) " +
            "OR (severity = 'CRITICAL' AND :currentTime - createdAt > 259200000) OR ttl <= 0")
    int removeExpiredPackets(long currentTime);

    @Query("SELECT * FROM packets ORDER BY receivedAt DESC")
    List<EmergencyReport> getAllPackets();
}
