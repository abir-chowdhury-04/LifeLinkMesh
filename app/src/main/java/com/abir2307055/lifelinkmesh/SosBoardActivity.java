package com.abir2307055.lifelinkmesh;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.data.SosAlert;
import com.abir2307055.lifelinkmesh.ui.SosAdapter;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Live board of every SOS alert in the cloud. The listener is attached only while this
// screen is visible, so it does not use data in the background.
public class SosBoardActivity extends AppCompatActivity {

    private SosAdapter adapter;
    private TextView txtBoardStatus;
    private Query query;
    private ValueEventListener listener;   // null while not listening
    private RequestQueue requestQueue;     // Volley: runs HTTP requests off the main thread

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_board);

        txtBoardStatus = findViewById(R.id.txtBoardStatus);
        RecyclerView recyclerBoard = findViewById(R.id.recyclerBoard);

        requestQueue = Volley.newRequestQueue(getApplicationContext());

        adapter = new SosAdapter();
        adapter.setOnSosClickListener(this::showActions);
        recyclerBoard.setLayoutManager(new LinearLayoutManager(this));
        recyclerBoard.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopListening();
        requestQueue.cancelAll(this);   // drop any place lookup still waiting for a reply
    }

    private void startListening() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            txtBoardStatus.setText("Sign in with a verified account (Cloud screen) to see the board");
            return;
        }
        txtBoardStatus.setText("Connecting...");
        // Fresh token, so the "email verified" flag the rules check is up to date.
        user.getIdToken(true).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                txtBoardStatus.setText("Token refresh failed: " + task.getException().getMessage());
                return;
            }
            attachListener();
        });
    }

    private void attachListener() {
        if (listener != null) {
            return;   // already listening
        }
        // Newest 200 by time. The adapter then sorts them by priority.
        query = FirebaseDatabase.getInstance().getReference("sos")
                .orderByChild("timestamp")
                .limitToLast(200);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SosAlert> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SosAlert alert = toAlert(child);
                    if (alert != null) {
                        list.add(alert);
                    }
                }
                adapter.setAlerts(list);   // sorts by priority, then newest first
                txtBoardStatus.setText("Live: " + list.size() + " open alert(s)");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                txtBoardStatus.setText("Board failed: " + error.getMessage());
            }
        };
        query.addValueEventListener(listener);
    }

    private void stopListening() {
        if (query != null && listener != null) {
            query.removeEventListener(listener);   // always remove, or it keeps running
        }
        listener = null;
    }

    // Turns one cloud record into the same SosAlert object the SOS screen uses.
    private SosAlert toAlert(DataSnapshot s) {
        String id = s.getKey();
        if (id == null) {
            return null;
        }
        // Resolved cases are hidden from the board (they stay in the cloud as history).
        String status = s.child("status").getValue(String.class);
        if ("resolved".equals(status)) {
            return null;
        }
        String sender = s.child("senderName").getValue(String.class);
        String category = s.child("category").getValue(String.class);
        String note = s.child("note").getValue(String.class);
        Long timestamp = s.child("timestamp").getValue(Long.class);
        Boolean hasLocation = s.child("hasLocation").getValue(Boolean.class);
        Double latitude = s.child("latitude").getValue(Double.class);
        Double longitude = s.child("longitude").getValue(Double.class);
        Double accuracy = s.child("accuracy").getValue(Double.class);

        return new SosAlert(id,
                sender == null ? "Unknown" : sender,
                category == null ? "Other" : category,
                note == null ? "" : note,
                timestamp == null ? 0L : timestamp,
                hasLocation != null && hasLocation,
                latitude == null ? 0 : latitude,
                longitude == null ? 0 : longitude,
                accuracy == null ? 0f : accuracy.floatValue(),
                false);
    }

    // Tapping an alert offers three actions.
    private void showActions(SosAlert alert) {
        String[] choices = {"Where is this?", "Open map", "Mark resolved"};
        new AlertDialog.Builder(this)
                .setTitle(alert.category + " SOS from " + alert.senderName)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        lookUpPlace(alert);
                    } else if (which == 1) {
                        openMap(alert);
                    } else {
                        markResolved(alert);
                    }
                })
                .show();
    }

    // ---- External API: turns the alert's coordinates into a place name ----

    private void lookUpPlace(SosAlert alert) {
        if (!alert.hasLocation) {
            Toast.makeText(this, "This SOS has no location", Toast.LENGTH_SHORT).show();
            return;
        }
        // Locale.US keeps the decimal point a dot, whatever the phone's language is.
        String url = String.format(Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=en"
                        + "&lat=%.6f&lon=%.6f", alert.latitude, alert.longitude);
        Toast.makeText(this, "Looking up the place...", Toast.LENGTH_SHORT).show();

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> showPlace(alert, placeLabel(response), response.has("error")),
                error -> showPlaceError(error)) {
            // The service asks every app to identify itself in the User-Agent header.
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "LifeLinkMesh/1.0 (university student project)");
                return headers;
            }
        };
        request.setTag(this);   // so onStop can cancel it
        requestQueue.add(request);
    }

    // Short label from the address parts: village / suburb / town / city, county, district.
    // We skip the road and "display_name" because road names can be long and in Bengali.
    private String placeLabel(JSONObject response) {
        JSONObject address = response.optJSONObject("address");
        if (address != null) {
            String[] keys = {"village", "suburb", "town", "city", "county", "state_district"};
            List<String> parts = new ArrayList<>();
            for (String key : keys) {
                String value = address.optString(key, "");
                if (!value.isEmpty() && !parts.contains(value) && parts.size() < 3) {
                    parts.add(value);
                }
            }
            if (!parts.isEmpty()) {
                return TextUtils.join(", ", parts);
            }
        }
        return response.optString("display_name", "");   // fallback, may be empty
    }

    private void showPlace(SosAlert alert, String label, boolean noData) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        String place = (noData || label.isEmpty())
                ? "No place name found for this spot" : label;
        String message = place
                + "\n\nCoordinates: " + String.format(Locale.US, "%.5f, %.5f",
                alert.latitude, alert.longitude)
                + "\n\n© OpenStreetMap contributors";
        new AlertDialog.Builder(this)
                .setTitle("Where is this?")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPlaceError(VolleyError error) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        String message;
        if (error.networkResponse == null) {
            message = "No internet, or the place service cannot be reached";
        } else {
            message = "Place service error (code " + error.networkResponse.statusCode + ")";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // Writes the status next to the alert. The live listener then removes it from every board.
    private void markResolved(SosAlert alert) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in first", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "resolved");
        updates.put("resolvedBy", user.getUid());
        updates.put("resolvedAt", System.currentTimeMillis());
        FirebaseDatabase.getInstance().getReference("sos").child(alert.id)
                .updateChildren(updates)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Marked as resolved", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Same map opening as the SOS screen (implicit Intent).
    private void openMap(SosAlert alert) {
        if (!alert.hasLocation) {
            Toast.makeText(this, "This SOS has no location", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse("geo:" + alert.latitude + "," + alert.longitude
                + "?q=" + alert.latitude + "," + alert.longitude
                + "(" + Uri.encode(alert.category + " SOS") + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No map app found", Toast.LENGTH_SHORT).show();
        }
    }
}