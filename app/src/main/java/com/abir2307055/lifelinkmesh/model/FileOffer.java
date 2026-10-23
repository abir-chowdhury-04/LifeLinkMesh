package com.abir2307055.lifelinkmesh.model;

import java.util.Locale;

// "Someone wants to send me a file."
public class FileOffer {

    public final String offerId;
    public final String endpointId;   // the phone that wants to send
    public final String senderName;
    public final String name;
    public final long size;           // bytes, or -1 if unknown

    public FileOffer(String offerId, String endpointId, String senderName,
                     String name, long size) {
        this.offerId = offerId;
        this.endpointId = endpointId;
        this.senderName = senderName;
        this.name = name;
        this.size = size;
    }

    public String sizeText() {
        if (size < 0) {
            return "unknown size";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", size / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
    }
}