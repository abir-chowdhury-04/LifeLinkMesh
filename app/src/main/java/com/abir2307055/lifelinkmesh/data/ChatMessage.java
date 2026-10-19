package com.abir2307055.lifelinkmesh.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// One row in the "messages" table.
@Entity(tableName = "messages")
public class ChatMessage {

    @PrimaryKey
    @NonNull
    public String id;          // unique per message (will also detect duplicates later)
    public String senderName;
    public String text;
    public long timestamp;     // milliseconds since 1970
    public boolean mine;       // true if this phone wrote it

    public ChatMessage(@NonNull String id, String senderName, String text,
                       long timestamp, boolean mine) {
        this.id = id;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
        this.mine = mine;
    }
}