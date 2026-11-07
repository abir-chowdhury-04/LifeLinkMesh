package com.abir2307055.lifelinkmesh;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.cloud.ProfileSync;
import com.abir2307055.lifelinkmesh.data.ProfileStore;
import com.abir2307055.lifelinkmesh.mesh.MeshHub;
import com.abir2307055.lifelinkmesh.mesh.MeshService;
import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.nearby.NearbyManager;
import com.abir2307055.lifelinkmesh.ui.PeerAdapter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NearbyManager.Listener {

    private TextView txtStatus;
    private TextView txtDevice;
    private TextView txtLog;
    private TextView txtAbout;
    private TextView txtEmpty;
    private View cardPermissions;
    private RecyclerView recyclerPeers;
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

        txtDevice = findViewById(R.id.txtDevice);
        txtStatus = findViewById(R.id.txtStatus);
        txtLog = findViewById(R.id.txtLog);
        txtAbout = findViewById(R.id.txtAbout);
        txtEmpty = findViewById(R.id.txtEmpty);
        cardPermissions = findViewById(R.id.cardPermissions);
        Button btnGrant = findViewById(R.id.btnGrant);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnOpenChat = findViewById(R.id.btnOpenChat);
        Button btnSos = findViewById(R.id.btnSos);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        Button btnGraph = findViewById(R.id.btnGraph);
        Button btnLog = findViewById(R.id.btnLog);
        Button btnFiles = findViewById(R.id.btnFiles);
        Button btnCloud = findViewById(R.id.btnCloud);
        Button btnContacts = findViewById(R.id.btnContacts);
        recyclerPeers = findViewById(R.id.recyclerPeers);

        hub = MeshHub.getInstance(this);
        switchSaver = findViewById(R.id.switchSaver);
        switchSaver.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) {   // only when the user taps it, not when we set it
                hub.setBatterySaver(checked, checked
                        ? "turned on with the test switch" : "turned off with the test switch");
            }
        });

        showDevice();
        txtDevice.setOnClickListener(v -> showNameDialog());

        peerAdapter = new PeerAdapter();
        peerAdapter.setOnPeerClickListener(this::onPeerClicked);
        recyclerPeers.setLayoutManager(new LinearLayoutManager(this));
        recyclerPeers.setAdapter(peerAdapter);
        // Whenever the list changes, refresh the "empty" text and the status line.
        // One observer here means we do not have to remember it at every call site.
        peerAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { updatePeerUi(); }
            @Override public void onItemRangeInserted(int start, int count) { updatePeerUi(); }
            @Override public void onItemRangeRemoved(int start, int count) { updatePeerUi(); }
            @Override public void onItemRangeChanged(int start, int count) { updatePeerUi(); }
        });
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

        // Technical items stay hidden until the user asks for them.
        Button btnMore = findViewById(R.id.btnMore);
        View layoutMore = findViewById(R.id.layoutMore);
        btnMore.setOnClickListener(v -> {
            boolean show = layoutMore.getVisibility() != View.VISIBLE;
            layoutMore.setVisibility(show ? View.VISIBLE : View.GONE);
            btnMore.setText(show ? "Fewer options" : "More options");
        });

        btnCloud.setOnClickListener(v ->
                startActivity(new Intent(this, CloudActivity.class)));

        btnContacts.setOnClickListener(v ->
                startActivity(new Intent(this, ContactsActivity.class)));

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

    private void showDevice() {
        String chosen = ProfileStore.loadName(this);
        String text = "Your name: " + hub.getLocalName() + "  (tap to change)";
        // The running name only changes when the mesh starts, so tell the user.
        if (!hub.getLocalName().startsWith(chosen + " #")) {
            text += "\nNew name \"" + chosen + "\" is used the next time you start the mesh";
        }
        txtDevice.setText(text);
    }

    private void showNameDialog() {
        EditText input = new EditText(this);
        input.setHint("Your name, for example Rahim");
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(ProfileStore.MAX_NAME_LENGTH)});
        input.setText(ProfileStore.loadName(this));
        input.setSelection(input.getText().length());

        // A little padding so the field does not touch the dialog edges.
        FrameLayout box = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Change your name")
                .setMessage("Other phones see this name. A number is added to keep it unique. "
                        + "It is used the next time you start the mesh.")
                .setView(box)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = ProfileStore.cleanName(input.getText().toString());
                    if (name.isEmpty()) {
                        Toast.makeText(this, "The name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ProfileStore.saveName(this, name);
                    showDevice();
                    ProfileSync.backupName(name, message ->
                            runOnUiThread(() ->
                                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showDevice();
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
            Toast.makeText(this, "Please allow the permissions first", Toast.LENGTH_SHORT).show();
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
        runOnUiThread(() -> peerAdapter.setPeers(hub.getPeers()));
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

    // ---- permissions (logic unchanged) ----

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

    // Permission card + the "About this phone" debug text + the status line.
    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        for (String p : getRequiredPermissions()) {
            boolean granted = ContextCompat.checkSelfPermission(this, p)
                    == PackageManager.PERMISSION_GRANTED;
            sb.append(p.substring(p.lastIndexOf('.') + 1))
                    .append(": ")
                    .append(granted ? "granted" : "DENIED")
                    .append("\n");
        }
        txtAbout.setText(sb.toString());
        cardPermissions.setVisibility(allPermissionsGranted() ? View.GONE : View.VISIBLE);
        updatePeerUi();
    }

    // The status line and the empty-list text, based on what the hub knows.
    private void updatePeerUi() {
        List<Peer> peers = hub.getPeers();
        int connected = 0;
        for (Peer p : peers) {
            if (Peer.STATUS_CONNECTED.equals(p.getStatus())) {
                connected++;
            }
        }
        String status;
        if (!allPermissionsGranted()) {
            status = "Allow permissions to begin";
        } else if (connected > 0) {
            status = "Connected to " + connected + (connected == 1 ? " phone" : " phones");
        } else if (!peers.isEmpty()) {
            status = peers.size() + (peers.size() == 1 ? " phone" : " phones")
                    + " found. Tap one to connect";
        } else {
            status = "Ready. Tap Start mesh";
        }
        txtStatus.setText(status);
        txtEmpty.setVisibility(peerAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}