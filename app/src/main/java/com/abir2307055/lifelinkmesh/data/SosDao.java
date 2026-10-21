package com.abir2307055.lifelinkmesh.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SosDao {

    // A repeated id is ignored (returns -1), like for chat.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(SosAlert alert);

    @Query("SELECT * FROM sos_alerts ORDER BY timestamp DESC")
    List<SosAlert> getAll();

    @Query("DELETE FROM sos_alerts")
    void deleteAll();
}