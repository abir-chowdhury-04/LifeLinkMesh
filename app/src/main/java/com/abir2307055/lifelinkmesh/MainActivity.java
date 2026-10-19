package com.abir2307055.lifelinkmesh;


import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.ChatMessage;
import java.util.UUID;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.nearby.NearbyManager;
import com.abir2307055.lifelinkmesh.ui.PeerAdapter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NearbyManager.Listener {

    private TextView txtStatus;
    private TextView txtLog;
    private PeerAdapter peerAdapter;
    private NearbyManager nearbyManager;
    private final List<String> logLines = new ArrayList<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> updateStatus());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView txtDevice = findViewById(R.id.txtDevice);
        txtStatus = findViewById(R.id.txtStatus);
        txtLog = findViewById(R.id.txtLog);
        Button btnGrant = findViewById(R.id.btnGrant);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnSendHello = findViewById(R.id.btnSendHello);
        RecyclerView recyclerPeers = findViewById(R.id.recyclerPeers);

        nearbyManager = new NearbyManager(this, this);

        txtDevice.setText("Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Name: " + nearbyManager.getLocalName());

        peerAdapter = new PeerAdapter();
        peerAdapter.setOnPeerClickListener(this::onPeerClicked);
        recyclerPeers.setLayoutManager(new LinearLayoutManager(this));
        recyclerPeers.setAdapter(peerAdapter);

        btnGrant.setOnClickListener(v ->
                permissionLauncher.launch(getRequiredPermissions()));
        btnStart.setOnClickListener(v -> startMesh());
        btnStop.setOnClickListener(v -> {
            nearbyManager.stop();
            peerAdapter.clear();
        });
        btnSendHello.setOnClickListener(v ->
                nearbyManager.sendToAll("Hello from " + nearbyManager.getLocalName()));

        // TEMPORARY Room self-test: saves one row each time the app starts.
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            db.messageDao().insert(new ChatMessage(
                    UUID.randomUUID().toString(), "test", "hello room",
                    System.currentTimeMillis(), true));
            int rows = db.messageDao().getAll().size();
            runOnUiThread(() -> appendLog("Room test: " + rows + " row(s) saved"));
        }).start();
    }

    @Override
    public void onMessageReceived(String endpointId, String text) {
        runOnUiThread(() -> appendLog("From " + endpointId + ": " + text));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void startMesh() {
        if (!allPermissionsGranted()) {
            appendLog("Grant permissions first");
            return;
        }
        nearbyManager.start();
    }

    private void onPeerClicked(Peer peer) {
        String status = peer.getStatus();
        if (Peer.STATUS_CONNECTING.equals(status) || Peer.STATUS_CONNECTED.equals(status)) {
            Toast.makeText(this, peer.getName() + " is " + status, Toast.LENGTH_SHORT).show();
            return;
        }
        peerAdapter.updateStatus(peer.getEndpointId(), Peer.STATUS_CONNECTING);
        nearbyManager.connectTo(peer.getEndpointId(), peer.getName());
    }

    // ---- NearbyManager.Listener: results may arrive off the UI thread, so hop onto it ----

    @Override
    public void onEndpointFound(String endpointId, String name) {
        runOnUiThread(() ->
                peerAdapter.addIfAbsent(new Peer(endpointId, name, Peer.STATUS_DISCOVERED)));
    }

    @Override
    public void onEndpointLost(String endpointId) {
        runOnUiThread(() -> peerAdapter.removeIfDiscovered(endpointId));
    }

    // Shows the Accept / Reject dialog. Both phones get one.
    @Override
    public void onConnectionRequest(String endpointId, String name,
                                    String digits, boolean incoming) {
        runOnUiThread(() -> {
            // Make sure the peer shows its real name in the list.
            peerAdapter.addIfAbsent(new Peer(endpointId, name, Peer.STATUS_CONNECTING));
            new AlertDialog.Builder(this)
                    .setTitle("Connect with " + name + "?")
                    .setMessage("Code: " + digits
                            + "\nCheck that the other phone shows the same code.")
                    .setPositiveButton("Accept",
                            (dialog, which) -> nearbyManager.acceptConnection(endpointId))
                    .setNegativeButton("Reject",
                            (dialog, which) -> nearbyManager.rejectConnection(endpointId))
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    public void onConnectionStateChanged(String endpointId, String state) {
        runOnUiThread(() -> {
            if (!peerAdapter.updateStatus(endpointId, state)) {
                peerAdapter.addIfAbsent(
                        new Peer(endpointId, "Unknown (" + endpointId + ")", state));
            }
        });
    }

    @Override
    public void onLog(String message) {
        runOnUiThread(() -> appendLog(message));
    }

    private void appendLog(String message) {
        logLines.add(message);
        if (logLines.size() > 8) {
            logLines.remove(0);
        }
        txtLog.setText(TextUtils.join("\n", logLines));
    }

    // ---- permissions (unchanged from Phase 0) ----

    private String[] getRequiredPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        return list.toArray(new String[0]);
    }

    private boolean allPermissionsGranted() {
        for (String p : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        for (String p : getRequiredPermissions()) {
            boolean granted = ContextCompat.checkSelfPermission(this, p)
                    == PackageManager.PERMISSION_GRANTED;
            sb.append(p.substring(p.lastIndexOf('.') + 1))
                    .append(": ")
                    .append(granted ? "granted" : "DENIED")
                    .append("\n");
        }
        txtStatus.setText(sb.toString());
    }
}