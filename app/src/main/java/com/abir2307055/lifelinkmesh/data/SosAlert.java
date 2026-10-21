package com.abir2307055.lifelinkmesh.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// One row in the "sos_alerts" table (alerts I sent and alerts I received).
@Entity(tableName = "sos_alerts")
public class SosAlert {

    @PrimaryKey
    @NonNull
    public String id;
    public String senderName;
    public String category;     // Medical, Trapped, Supplies, Other
    public String note;
    public long timestamp;
    public boolean hasLocation;
    public double latitude;
    public double longitude;
    public float accuracy;      // metres
    public boolean mine;

    public SosAlert(@NonNull String id, String senderName, String category, String note,
                    long timestamp, boolean hasLocation, double latitude,
                    double longitude, float accuracy, boolean mine) {
        this.id = id;
        this.senderName = senderName;
        this.category = category;
        this.note = note;
        this.timestamp = timestamp;
        this.hasLocation = hasLocation;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.mine = mine;
    }

    // Lower number = more urgent. Used to sort the list.
    public int priority() {
        if ("Medical".equals(category)) return 0;
        if ("Trapped".equals(category)) return 1;
        if ("Supplies".equals(category)) return 2;
        return 3;
    }
}