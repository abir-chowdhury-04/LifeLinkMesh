package com.abir2307055.lifelinkmesh;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.ui.PeerAdapter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView txtDevice;
    private TextView txtStatus;
    private PeerAdapter peerAdapter;
    private int testPeerCounter = 0;

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
        Button btnGrant = findViewById(R.id.btnGrant);
        Button btnAddTestPeer = findViewById(R.id.btnAddTestPeer);
        RecyclerView recyclerPeers = findViewById(R.id.recyclerPeers);

        txtDevice.setText("Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");

        btnGrant.setOnClickListener(v ->
                permissionLauncher.launch(getRequiredPermissions()));

        // RecyclerView needs: a LayoutManager, an Adapter, and data.
        peerAdapter = new PeerAdapter();
        recyclerPeers.setLayoutManager(new LinearLayoutManager(this));
        recyclerPeers.setAdapter(peerAdapter);

        // TEMPORARY: lets us see the list grow before Nearby exists.
        btnAddTestPeer.setOnClickListener(v -> {
            testPeerCounter++;
            peerAdapter.addPeer(new Peer(
                    "test-" + testPeerCounter,
                    "Test peer " + testPeerCounter,
                    "Discovered"));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

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