package com.abir2307055.lifelinkmesh.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.SosAlert;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Pushes SOS alerts that are stored on this phone (sent or received through the mesh)
// to the Realtime Database when the phone has working internet.
public class CloudSync {

    public interface Listener {
        void onResult(String message);   // may be called on any thread
    }

    private static final String TAG = "CloudSync";
    private static final String PREFS = "lifelink_cloud";
    private static final String KEY_UPLOADED = "uploaded_sos_ids";
    private static final String SOS_PATH = "sos";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static boolean syncing = false;
    private static long syncingSince = 0;

    // listener == null means a silent background run (the timer in MeshService).
    public static void syncNow(Context context, Listener listener) {
        Context app = context.getApplicationContext();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            quit(listener, "Not signed in with a verified account");
            return;
        }
        if (!isOnline(app)) {
            quit(listener, "No internet right now");
            return;
        }
        synchronized (CloudSync.class) {
            long now = System.currentTimeMillis();
            // If a run seems stuck for over a minute, allow a new one.
            if (syncing && now - syncingSince < 60000) {
                quit(listener, "Sync already running");
                return;
            }
            syncing = true;
            syncingSince = now;
        }

        EXECUTOR.execute(() -> {
            // Room must run off the main thread.
            List<SosAlert> all = AppDatabase.getInstance(app).sosDao().getAll();
            Set<String> done = loadUploaded(app);
            List<SosAlert> pending = new ArrayList<>();
            for (SosAlert a : all) {
                if (!done.contains(a.id)) {
                    pending.add(a);
                }
            }
            if (pending.isEmpty()) {
                finish(listener, "Nothing new to upload", false);
                return;
            }
            // Fresh token, so the "email verified" flag the rules check is up to date.
            user.getIdToken(true).addOnCompleteListener(tokenTask -> {
                if (!tokenTask.isSuccessful()) {
                    finish(listener, "Token refresh failed: "
                            + tokenTask.getException().getMessage(), true);
                    return;
                }
                push(app, user, pending, listener);
            });
        });
    }

    private static void push(Context app, FirebaseUser user, List<SosAlert> pending,
                             Listener listener) {
        // Path "<alert id>" -> data. The same id overwrites, so a repeat never duplicates.
        Map<String, Object> updates = new HashMap<>();
        for (SosAlert a : pending) {
            Map<String, Object> item = new HashMap<>();
            item.put("senderName", a.senderName);
            item.put("category", a.category);
            item.put("note", a.note);
            item.put("timestamp", a.timestamp);
            item.put("hasLocation", a.hasLocation);
            item.put("latitude", a.latitude);
            item.put("longitude", a.longitude);
            item.put("accuracy", (double) a.accuracy);
            item.put("uploadedBy", user.getUid());   // the gateway phone's account
            updates.put(a.id, item);
        }
        final int count = pending.size();
        FirebaseDatabase.getInstance().getReference(SOS_PATH)
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        markUploaded(app, pending);
                        finish(listener, "Uploaded " + count + " SOS alert(s)", true);
                    } else {
                        finish(listener, "Upload failed: " + task.getException().getMessage(), true);
                    }
                });
    }

    private static boolean isOnline(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        // VALIDATED means Android checked that the internet really works
        // (Wi-Fi with no internet does not count).
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static Set<String> loadUploaded(Context app) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Copy the set: Android says the returned set must not be modified.
        return new HashSet<>(prefs.getStringSet(KEY_UPLOADED, new HashSet<>()));
    }

    private static void markUploaded(Context app, List<SosAlert> sent) {
        Set<String> done = loadUploaded(app);
        for (SosAlert a : sent) {
            done.add(a.id);
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_UPLOADED, done).apply();
    }

    // Early exit before a run started: log only for manual runs, so the timer stays quiet.
    private static void quit(Listener listener, String message) {
        if (listener != null) {
            Log.d(TAG, message);
            listener.onResult(message);
        }
    }

    private static void finish(Listener listener, String message, boolean log) {
        synchronized (CloudSync.class) {
            syncing = false;
        }
        if (log || listener != null) {
            Log.d(TAG, message);
        }
        if (listener != null) {
            listener.onResult(message);
        }
    }
}