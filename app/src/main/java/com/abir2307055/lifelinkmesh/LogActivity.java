package com.abir2307055.lifelinkmesh;

import android.database.Cursor;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.abir2307055.lifelinkmesh.mesh.MeshLogProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Reads the saved messages and SOS alerts THROUGH the Content Provider
// (not straight from the database) and can share them as JSON.
public class LogActivity extends AppCompatActivity {

    private TextView txtLogBody;
    private TextView txtLogInfo;
    private volatile String exportJson = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        txtLogBody = findViewById(R.id.txtLogBody);
        txtLogInfo = findViewById(R.id.txtLogInfo);
        Button btnShareJson = findViewById(R.id.btnShareJson);
        btnShareJson.setOnClickListener(v -> shareJson());

        loadLog();
    }

    private void loadLog() {
        executor.execute(() -> {
            StringBuilder text = new StringBuilder();
            JSONArray messages = new JSONArray();
            JSONArray alerts = new JSONArray();
            try {
                // 1. Chat messages, through the provider's "messages" URI.
                try (Cursor c = getContentResolver().query(
                        MeshLogProvider.MESSAGES_URI, null, null, null, null)) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            String sender = c.getString(c.getColumnIndexOrThrow("senderName"));
                            String body = c.getString(c.getColumnIndexOrThrow("text"));
                            long time = c.getLong(c.getColumnIndexOrThrow("timestamp"));
                            boolean mine = c.getInt(c.getColumnIndexOrThrow("mine")) == 1;

                            JSONObject o = new JSONObject();
                            o.put("id", c.getString(c.getColumnIndexOrThrow("id")));
                            o.put("senderName", sender);
                            o.put("text", body);
                            o.put("timestamp", time);
                            o.put("mine", mine);
                            messages.put(o);

                            text.append("[").append(timeFormat.format(new Date(time))).append("] ")
                                    .append(mine ? "Me" : sender).append(": ")
                                    .append(body).append("\n");
                        }
                    }
                }
                text.append("\n--- SOS alerts ---\n");

                // 2. SOS alerts, through the "sos" URI.
                try (Cursor c = getContentResolver().query(
                        MeshLogProvider.SOS_URI, null, null, null, null)) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            String sender = c.getString(c.getColumnIndexOrThrow("senderName"));
                            String category = c.getString(c.getColumnIndexOrThrow("category"));
                            String note = c.getString(c.getColumnIndexOrThrow("note"));
                            long time = c.getLong(c.getColumnIndexOrThrow("timestamp"));
                            boolean hasLocation =
                                    c.getInt(c.getColumnIndexOrThrow("hasLocation")) == 1;
                            double lat = c.getDouble(c.getColumnIndexOrThrow("latitude"));
                            double lng = c.getDouble(c.getColumnIndexOrThrow("longitude"));
                            boolean mine = c.getInt(c.getColumnIndexOrThrow("mine")) == 1;

                            JSONObject o = new JSONObject();
                            o.put("id", c.getString(c.getColumnIndexOrThrow("id")));
                            o.put("senderName", sender);
                            o.put("category", category);
                            o.put("note", note);
                            o.put("timestamp", time);
                            o.put("hasLocation", hasLocation);
                            if (hasLocation) {
                                o.put("latitude", lat);
                                o.put("longitude", lng);
                            }
                            o.put("mine", mine);
                            alerts.put(o);

                            text.append("[").append(timeFormat.format(new Date(time))).append("] ")
                                    .append(category).append(" from ")
                                    .append(mine ? "Me" : sender).append(": ")
                                    .append(note == null || note.isEmpty() ? "(no note)" : note)
                                    .append(hasLocation ? " (with location)" : " (no location)")
                                    .append("\n");
                        }
                    }
                }

                JSONObject root = new JSONObject();
                root.put("exportedAt", System.currentTimeMillis());
                root.put("messages", messages);
                root.put("sos", alerts);
                exportJson = root.toString(2);
            } catch (JSONException e) {
                text.append("\nCould not build JSON: ").append(e.getMessage());
            }

            String body = text.toString();
            int messageCount = messages.length();
            int alertCount = alerts.length();
            runOnUiThread(() -> {
                txtLogBody.setText(body);
                txtLogInfo.setText(messageCount + " message(s), " + alertCount
                        + " SOS alert(s), read through the Content Provider");
            });
        });
    }

    // Sends the JSON text to whichever app the user picks (an implicit Intent).
    private void shareJson() {
        if (exportJson.isEmpty()) {
            Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "LifeLink Mesh log");
        send.putExtra(Intent.EXTRA_TEXT, exportJson);
        startActivity(Intent.createChooser(send, "Share log as JSON"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}