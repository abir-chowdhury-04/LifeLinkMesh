package com.abir2307055.lifelinkmesh.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {

    // If a message with the same id already exists, ignore the new one.
    // The returned number is the row id, or -1 if it was ignored (to be verified in testing).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(ChatMessage message);

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    List<ChatMessage> getAll();

    @Query("DELETE FROM messages")
    void deleteAll();
}