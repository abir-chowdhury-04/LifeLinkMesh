package com.abir2307055.lifelinkmesh;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.data.SosAlert;
import com.abir2307055.lifelinkmesh.ui.SosAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import androidx.appcompat.app.AlertDialog;

import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

// Live board of every SOS alert in the cloud. The listener is attached only while this
// screen is visible, so it does not use data in the background.
public class SosBoardActivity extends AppCompatActivity {

    private SosAdapter adapter;
    private TextView txtBoardStatus;
    private Query query;
    private ValueEventListener listener;   // null while not listening

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_board);

        txtBoardStatus = findViewById(R.id.txtBoardStatus);
        RecyclerView recyclerBoard = findViewById(R.id.recyclerBoard);

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

    // Tapping an alert offers two actions.
    private void showActions(SosAlert alert) {
        String[] choices = {"Open map", "Mark resolved"};
        new AlertDialog.Builder(this)
                .setTitle(alert.category + " SOS from " + alert.senderName)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        openMap(alert);
                    } else {
                        markResolved(alert);
                    }
                })
                .show();
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