package com.abir2307055.lifelinkmesh.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

// Keeps the display name the user chose, on the phone.
public class ProfileStore {

    public static final int MAX_NAME_LENGTH = 20;   // kept short on purpose

    private static final String PREFS = "lifelink_profile";
    private static final String KEY_NAME = "display_name";

    // The chosen name, or the phone model if the user has not chosen one yet.
    public static String loadName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_NAME, "");
        if (saved == null || saved.isEmpty()) {
            return Build.MODEL;
        }
        return saved;
    }

    public static void saveName(Context context, String name) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_NAME, name).apply();
    }

    // Trims spaces, removes "#" (we add our own "#number"), and cuts to the maximum length.
    public static String cleanName(String raw) {
        String name = raw.replace("#", "").trim().replaceAll("\\s+", " ");
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH).trim();
        }
        return name;
    }
}