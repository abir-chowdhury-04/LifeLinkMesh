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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView txtDevice;
    private TextView txtStatus;

    // Asks Android to show the permission dialogs; when the user answers, refresh the screen.
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

        txtDevice.setText("Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");

        btnGrant.setOnClickListener(v ->
                permissionLauncher.launch(getRequiredPermissions()));
    }

    // Runs every time the screen comes back to the front, e.g. after the
    // user changes permissions in system Settings.
    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    // Builds the permission list for THIS phone's Android version.
    private String[] getRequiredPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {          // Android 12+
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {   // Android 13+
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