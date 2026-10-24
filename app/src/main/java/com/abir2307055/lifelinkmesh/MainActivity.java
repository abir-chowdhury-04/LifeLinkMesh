package com.abir2307055.lifelinkmesh;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.mesh.MeshHub;
import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.nearby.NearbyManager;
import com.abir2307055.lifelinkmesh.ui.PeerAdapter;
import com.abir2307055.lifelinkmesh.mesh.MeshService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NearbyManager.Listener {

    private TextView txtStatus;
    private TextView txtLog;
    private PeerAdapter peerAdapter;
    private MeshHub hub;
    private final List<String> logLines = new ArrayList<>();
    private SwitchCompat switchSaver;
    private SwitchCompat switchEmergency;

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
        Button btnOpenChat = findViewById(R.id.btnOpenChat);
        Button btnSos = findViewById(R.id.btnSos);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        Button btnGraph = findViewById(R.id.btnGraph);
        Button btnLog = findViewById(R.id.btnLog);
        Button btnFiles = findViewById(R.id.btnFiles);
        RecyclerView recyclerPeers = findViewById(R.id.recyclerPeers);

        hub = MeshHub.getInstance(this);
        switchSaver = findViewById(R.id.switchSaver);
        switchSaver.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) {   // only when the user taps it, not when we set it
                hub.setBatterySaver(checked, checked
                        ? "turned on with the test switch" : "turned off with the test switch");
            }
        });

        txtDevice.setText("Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Name: " + hub.getLocalName());

        peerAdapter = new PeerAdapter();
        peerAdapter.setOnPeerClickListener(this::onPeerClicked);
        recyclerPeers.setLayoutManager(new LinearLayoutManager(this));
        recyclerPeers.setAdapter(peerAdapter);
        // If this screen was recreated (e.g. rotation), restore the peers from the hub.
        peerAdapter.setPeers(hub.getPeers());

        hub.addListener(this);

        btnGrant.setOnClickListener(v ->
                permissionLauncher.launch(getRequiredPermissions()));
        btnStart.setOnClickListener(v -> startMesh());
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, MeshService.class));   // the service stops the hub
            peerAdapter.clear();
        });

        btnOpenChat.setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));

        btnSos.setOnClickListener(v ->
                startActivity(new Intent(this, SosActivity.class)));

        btnRefresh.setOnClickListener(v -> {
            hub.refresh();
            peerAdapter.setPeers(hub.getPeers());
        });

        btnGraph.setOnClickListener(v ->
                startActivity(new Intent(this, NetworkGraphActivity.class)));

        btnLog.setOnClickListener(v ->
                startActivity(new Intent(this, LogActivity.class)));

        btnFiles.setOnClickListener(v ->
                startActivity(new Intent(this, FileActivity.class)));

        switchEmergency = findViewById(R.id.switchEmergency);
        switchEmergency.setOnCheckedChangeListener((button, checked) -> {
            updateEmergencyUi(checked);
            if (button.isPressed()) {   // only when the user taps it
                hub.setEmergencyMode(checked, checked
                        ? "turned on by the user: connecting and accepting automatically"
                        : "turned off by the user");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        switchSaver.setChecked(hub.isBatterySaver());
        switchEmergency.setChecked(hub.isEmergencyMode());
        updateEmergencyUi(hub.isEmergencyMode());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop listening, so the hub never calls into a destroyed screen.
        hub.removeListener(this);
    }

    private void startMesh() {
        if (!allPermissionsGranted()) {
            appendLog("Grant permissions first");
            return;
        }
        ContextCompat.startForegroundService(this, new Intent(this, MeshService.class));
    }

    private void onPeerClicked(Peer peer) {
        String status = peer.getStatus();
        if (Peer.STATUS_CONNECTING.equals(status) || Peer.STATUS_CONNECTED.equals(status)) {
            Toast.makeText(this, peer.getName() + " is " + status, Toast.LENGTH_SHORT).show();
            return;
        }
        // The hub marks the peer "Connecting" and tells us, which updates the list.
        hub.connectTo(peer.getEndpointId(), peer.getName());
    }

    // ---- NearbyManager.Listener (events forwarded by the hub) ----

    @Override
    public void onEndpointFound(String endpointId, String name) {
        runOnUiThread(() -> {
            if (!peerAdapter.reviveIfIdle(endpointId)) {
                peerAdapter.addIfAbsent(new Peer(endpointId, name, Peer.STATUS_DISCOVERED));
            }
        });
    }

    @Override
    public void onEndpointLost(String endpointId) {
        runOnUiThread(() -> peerAdapter.removeIfDiscovered(endpointId));
    }

    @Override
    public void onConnectionRequest(String endpointId, String name,
                                    String digits, boolean incoming) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            peerAdapter.addIfAbsent(new Peer(endpointId, name, Peer.STATUS_CONNECTING));
            new AlertDialog.Builder(this)
                    .setTitle("Connect with " + name + "?")
                    .setMessage("Code: " + digits
                            + "\nCheck that the other phone shows the same code.")
                    .setPositiveButton("Accept",
                            (dialog, which) -> hub.acceptConnection(endpointId))
                    .setNegativeButton("Reject",
                            (dialog, which) -> hub.rejectConnection(endpointId))
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    public void onConnectionStateChanged(String endpointId, String state) {
        runOnUiThread(() -> {
            if (!peerAdapter.updateStatus(endpointId, state)) {
                peerAdapter.setPeers(hub.getPeers());   // the hub knows the real name
            }
        });
    }

    @Override
    public void onMessageReceived(String endpointId, String text) {
        runOnUiThread(() -> appendLog("From " + endpointId + ": " + text));
    }

    @Override
    public void onLog(String message) {
        runOnUiThread(() -> appendLog(message));
    }

    // Makes emergency mode impossible to miss: red text when it is on.
    private void updateEmergencyUi(boolean on) {
        switchEmergency.setText(on ? "EMERGENCY MODE: ON (connecting automatically)"
                : "Emergency mode: OFF");
        switchEmergency.setTextColor(on ? Color.RED : Color.GRAY);
    }

    private void appendLog(String message) {
        logLines.add(message);
        if (logLines.size() > 8) {
            logLines.remove(0);
        }
        txtLog.setText(TextUtils.join("\n", logLines));
    }

    // ---- permissions (unchanged) ----

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
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {   // Android 9 and below
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE);
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