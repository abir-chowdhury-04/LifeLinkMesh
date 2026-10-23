package com.abir2307055.lifelinkmesh;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.mesh.FileTransferManager;
import com.abir2307055.lifelinkmesh.mesh.MeshHub;
import com.abir2307055.lifelinkmesh.model.FileOffer;
import com.abir2307055.lifelinkmesh.model.Peer;
import com.abir2307055.lifelinkmesh.ui.FileAdapter;
import com.abir2307055.lifelinkmesh.ui.PeerAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FileActivity extends AppCompatActivity implements FileTransferManager.Listener {

    private static final String FILE_AUTHORITY = "com.abir2307055.lifelinkmesh.fileprovider";

    private MeshHub hub;
    private FileTransferManager files;
    private PeerAdapter peerAdapter;
    private FileAdapter fileAdapter;
    private TextView txtFileStatus;
    private String selectedEndpointId;
    private String lastPeerSignature = "";
    private final Set<String> shownOffers = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Keeps the "connected phones" list up to date while this screen is visible.
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshPeers();
            handler.postDelayed(this, 1000);
        }
    };

    // The system file picker: no storage permission needed.
    private final ActivityResultLauncher<String[]> pickFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_files);

        hub = MeshHub.getInstance(this);
        files = hub.files();
        txtFileStatus = findViewById(R.id.txtFileStatus);

        RecyclerView recyclerFilePeers = findViewById(R.id.recyclerFilePeers);
        peerAdapter = new PeerAdapter();
        peerAdapter.setOnPeerClickListener(peer -> {
            selectedEndpointId = peer.getEndpointId();
            pickFile.launch(new String[]{"*/*"});
        });
        recyclerFilePeers.setLayoutManager(new LinearLayoutManager(this));
        recyclerFilePeers.setAdapter(peerAdapter);

        RecyclerView recyclerReceived = findViewById(R.id.recyclerReceived);
        fileAdapter = new FileAdapter();
        fileAdapter.setOnFileClickListener(this::openFile);
        recyclerReceived.setLayoutManager(new LinearLayoutManager(this));
        recyclerReceived.setAdapter(fileAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        files.addListener(this);
        handler.post(tick);
        refreshReceived();
        // Offers that arrived while this screen was closed.
        for (FileOffer offer : files.getPendingOffers()) {
            if (shownOffers.add(offer.offerId)) {
                showOfferDialog(offer);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        files.removeListener(this);
        handler.removeCallbacks(tick);
    }

    private void onFilePicked(Uri uri) {
        if (uri == null || selectedEndpointId == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // not all providers allow it; the picked file is still readable for now
        }
        files.sendOffer(selectedEndpointId, uri);
    }

    // Only connected phones can receive a file.
    private void refreshPeers() {
        List<Peer> connected = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        for (Peer p : hub.getPeers()) {
            if (Peer.STATUS_CONNECTED.equals(p.getStatus())) {
                connected.add(p);
                signature.append(p.getEndpointId()).append(',');
            }
        }
        if (!signature.toString().equals(lastPeerSignature)) {
            lastPeerSignature = signature.toString();
            peerAdapter.setPeers(connected);
            if (connected.isEmpty()) {
                txtFileStatus.setText("No connected phones. Connect on the main screen first.");
            }
        }
    }

    private void refreshReceived() {
        fileAdapter.setFiles(files.getReceivedFiles());
    }

    private void showOfferDialog(FileOffer offer) {
        new AlertDialog.Builder(this)
                .setTitle("File from " + offer.senderName)
                .setMessage(offer.name + " (" + offer.sizeText() + ")\n\nAccept this file?")
                .setPositiveButton("Accept",
                        (dialog, which) -> files.replyToOffer(offer.offerId, true))
                .setNegativeButton("Decline",
                        (dialog, which) -> files.replyToOffer(offer.offerId, false))
                .setCancelable(false)
                .show();
    }

    // Opens a received file in another app (implicit Intent through a FileProvider).
    private void openFile(File file) {
        Uri uri = FileProvider.getUriForFile(this, FILE_AUTHORITY, file);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mime == null) {
            mime = "*/*";
        }
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, mime);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(view);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- FileTransferManager.Listener (may be called from other threads) ----

    @Override
    public void onFileOffer(FileOffer offer) {
        runOnUiThread(() -> {
            if (shownOffers.add(offer.offerId)) {
                showOfferDialog(offer);
            }
        });
    }

    @Override
    public void onStatus(String text) {
        runOnUiThread(() -> txtFileStatus.setText(text));
    }

    @Override
    public void onFilesChanged() {
        runOnUiThread(this::refreshReceived);
    }
}