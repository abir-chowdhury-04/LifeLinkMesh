package com.abir2307055.lifelinkmesh;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.SosAlert;
import com.abir2307055.lifelinkmesh.data.SosDao;
import com.abir2307055.lifelinkmesh.mesh.MeshHub;
import com.abir2307055.lifelinkmesh.ui.SosAdapter;
import com.abir2307055.lifelinkmesh.data.ContactStore;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SosActivity extends AppCompatActivity
        implements MeshHub.SosListener, LocationListener {

    private MeshHub hub;
    private SosDao sosDao;
    private SosAdapter adapter;
    private RadioGroup radioCategory;
    private EditText editSosNote;
    private TextView txtLocation;
    private LocationManager locationManager;
    private Location bestLocation;   // newest GPS fix we know about, or null
    // Background thread used only to load saved alerts when the screen opens.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos);

        hub = MeshHub.getInstance(this);
        sosDao = AppDatabase.getInstance(this).sosDao();

        radioCategory = findViewById(R.id.radioCategory);
        editSosNote = findViewById(R.id.editSosNote);
        txtLocation = findViewById(R.id.txtLocation);
        Button btnSendSos = findViewById(R.id.btnSendSos);
        Button btnClearSos = findViewById(R.id.btnClearSos);
        btnClearSos.setOnClickListener(v -> confirmClear());
        Button btnEmailContacts = findViewById(R.id.btnEmailContacts);
        RecyclerView recyclerSos = findViewById(R.id.recyclerSos);

        adapter = new SosAdapter();
        adapter.setOnSosClickListener(this::openMap);
        recyclerSos.setLayoutManager(new LinearLayoutManager(this));
        recyclerSos.setAdapter(adapter);

        btnSendSos.setOnClickListener(v -> sendSos());
        loadAlerts();

        btnEmailContacts.setOnClickListener(v -> emailContacts());
    }

    private void loadAlerts() {
        executor.execute(() -> {
            List<SosAlert> saved = sosDao.getAll();
            runOnUiThread(() -> {
                adapter.setAlerts(saved);
                // Listen only after the saved alerts are shown, so none appears twice.
                hub.addSosListener(this);
            });
        });
    }

    private void sendSos() {
        RadioButton checked = findViewById(radioCategory.getCheckedRadioButtonId());
        String category = checked.getText().toString();
        String note = editSosNote.getText().toString().trim();
        boolean hasLocation = bestLocation != null;
        hub.setEmergencyMode(true, "turned on because an SOS was sent");

        hub.sendSos(category, note, hasLocation,
                hasLocation ? bestLocation.getLatitude() : 0,
                hasLocation ? bestLocation.getLongitude() : 0,
                hasLocation ? bestLocation.getAccuracy() : 0f);

        editSosNote.setText("");
        Toast.makeText(this,
                hasLocation ? "SOS sent with location"
                        : "SOS sent WITHOUT location (no GPS fix yet)",
                Toast.LENGTH_SHORT).show();
    }

    // New alert from the hub (sent by me, or received from the mesh).
    @Override
    public void onSos(SosAlert alert) {
        runOnUiThread(() -> adapter.addAlert(alert));
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear SOS alerts?")
                .setMessage("This deletes the saved alerts on this phone only.")
                .setPositiveButton("Clear", (dialog, which) -> executor.execute(() -> {
                    sosDao.deleteAll();
                    runOnUiThread(() -> adapter.clear());
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Opens a map app at the alert's position (implicit Intent).
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

    // Opens the email app with a prefilled SOS message for the saved emergency contacts.
    // The user taps Send; nothing is sent silently.
    private void emailContacts() {
        List<String> contacts = ContactStore.load(this);
        if (contacts.isEmpty()) {
            Toast.makeText(this, "Add an emergency contact first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, ContactsActivity.class));
            return;
        }

        RadioButton checked = findViewById(radioCategory.getCheckedRadioButtonId());
        String category = checked.getText().toString();
        String note = editSosNote.getText().toString().trim();

        StringBuilder body = new StringBuilder();
        body.append("I need help. Category: ").append(category).append(".\n");
        if (!note.isEmpty()) {
            body.append("Note: ").append(note).append("\n");
        }
        if (bestLocation != null) {
            body.append(String.format(Locale.US,
                    "My location: https://maps.google.com/?q=%.6f,%.6f (accuracy about %.0f m)\n",
                    bestLocation.getLatitude(), bestLocation.getLongitude(),
                    bestLocation.getAccuracy()));
        } else {
            body.append("My location is not available yet.\n");
        }
        body.append("Sent from LifeLink Mesh.");

        // "mailto:" limits the choice to email apps.
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, contacts.toArray(new String[0]));
        intent.putExtra(Intent.EXTRA_SUBJECT, "SOS: " + category);
        intent.putExtra(Intent.EXTRA_TEXT, body.toString());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_LONG).show();
        }
    }

    // ---- GPS: only while this screen is visible ----

    @Override
    protected void onStart() {
        super.onStart();
        startLocation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    private void startLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            txtLocation.setText("Location: permission not granted");
            return;
        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            txtLocation.setText("Location: GPS is off. Turn on Location in settings.");
            return;
        }
        Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last != null) {
            bestLocation = last;
            showLocation();
        } else {
            txtLocation.setText("Location: searching for GPS... (go near a window or outside)");
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, this);
    }

    private void showLocation() {
        txtLocation.setText(String.format(Locale.US, "Location: %.5f, %.5f (±%.0f m)",
                bestLocation.getLatitude(), bestLocation.getLongitude(),
                bestLocation.getAccuracy()));
    }

    // LocationListener: below Android 11 every method below must be overridden.

    @Override
    public void onLocationChanged(@NonNull Location location) {
        bestLocation = location;
        showLocation();
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        txtLocation.setText("Location: GPS was turned off");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hub.removeSosListener(this);
        executor.shutdown();
    }
}