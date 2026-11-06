package com.abir2307055.lifelinkmesh.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

// Keeps the user's emergency email addresses on the phone (works with no internet).
public class ContactStore {

    private static final String PREFS = "lifelink_contacts";
    private static final String KEY_1 = "contact1";
    private static final String KEY_2 = "contact2";

    public static void save(Context context, String email1, String email2) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_1, email1).putString(KEY_2, email2).apply();
    }

    // Returns only the non-empty addresses (0, 1 or 2 items).
    public static List<String> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> result = new ArrayList<>();
        String a = prefs.getString(KEY_1, "");
        String b = prefs.getString(KEY_2, "");
        if (a != null && !a.isEmpty()) result.add(a);
        if (b != null && !b.isEmpty()) result.add(b);
        return result;
    }
}