package com.abir2307055.lifelinkmesh.mesh;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.abir2307055.lifelinkmesh.model.FileOffer;
import com.abir2307055.lifelinkmesh.nearby.NearbyManager;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// File sharing: the offer / accept handshake, sending, receiving and saving.
// Files go DIRECTLY between two connected phones (they are not relayed).
public class FileTransferManager {

    // Screens implement this to hear about file events.
    public interface Listener {
        void onFileOffer(FileOffer offer);   // someone wants to send me a file
        void onStatus(String text);          // progress and results
        void onFilesChanged();               // the received-files folder changed
    }

    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;   // 25 MB

    private static class OutgoingFile {
        final String endpointId;
        final Uri uri;
        final String name;

        OutgoingFile(String endpointId, Uri uri, String name) {
            this.endpointId = endpointId;
            this.uri = uri;
            this.name = name;
        }
    }

    private final Context appContext;
    private final NearbyManager nearby;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // Sending side
    private final Map<String, OutgoingFile> outgoingOffers = new ConcurrentHashMap<>();  // by offerId
    private final Map<Long, OutgoingFile> outgoingPayloads = new ConcurrentHashMap<>();  // by payloadId
    // Receiving side
    private final Map<String, FileOffer> pendingOffers = new ConcurrentHashMap<>();      // not answered yet
    private final Map<String, FileOffer> acceptedOffers = new ConcurrentHashMap<>();     // accepted, file coming
    private final Map<Long, FileOffer> expectedPayloads = new ConcurrentHashMap<>();     // payloadId -> offer
    private final Map<Long, Payload> receivedPayloads = new ConcurrentHashMap<>();       // payloadId -> payload
    private final Set<Long> finishedPayloads = ConcurrentHashMap.newKeySet();

    public FileTransferManager(Context appContext, NearbyManager nearby) {
        this.appContext = appContext;
        this.nearby = nearby;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void status(String text) {
        for (Listener l : listeners) {
            l.onStatus(text);
        }
    }

    public List<FileOffer> getPendingOffers() {
        return new ArrayList<>(pendingOffers.values());
    }

    // The files I have received, newest first.
    public List<File> getReceivedFiles() {
        File[] found = receivedDir().listFiles();
        List<File> list = new ArrayList<>();
        if (found != null) {
            list.addAll(Arrays.asList(found));
            Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        return list;
    }

    private File receivedDir() {
        File dir = new File(appContext.getFilesDir(), "received");
        dir.mkdirs();
        return dir;
    }

    // ---- sending ----

    // Step 1 (sender): offer a file to one connected phone.
    public void sendOffer(String endpointId, Uri uri) {
        String name = "file";
        long size = -1;
        try (Cursor c = appContext.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameCol >= 0 && !c.isNull(nameCol)) {
                    name = c.getString(nameCol);
                }
                int sizeCol = c.getColumnIndex(OpenableColumns.SIZE);
                if (sizeCol >= 0 && !c.isNull(sizeCol)) {
                    size = c.getLong(sizeCol);
                }
            }
        } catch (Exception e) {
            status("Could not read the chosen file: " + e.getMessage());
            return;
        }
        if (size > MAX_FILE_BYTES) {
            status("File is too big (limit 25 MB)");
            return;
        }
        try {
            String offerId = UUID.randomUUID().toString();
            outgoingOffers.put(offerId, new OutgoingFile(endpointId, uri, name));

            JSONObject body = new JSONObject();
            body.put("name", name);
            body.put("size", size);
            MeshMessage offer = new MeshMessage(offerId, MeshMessage.TYPE_FILE_OFFER,
                    nearby.getLocalName(), body.toString(), System.currentTimeMillis(), 1);
            nearby.sendTextTo(endpointId, offer.toJson());
            status("Offered " + name + ", waiting for the other phone to accept");
        } catch (JSONException e) {
            status("Could not build the offer: " + e.getMessage());
        }
    }

    // Step 3 (sender): the receiver accepted, so send the file.
    private void startSending(String offerId, OutgoingFile out) {
        ioExecutor.execute(() -> {
            try {
                ParcelFileDescriptor pfd =
                        appContext.getContentResolver().openFileDescriptor(out.uri, "r");
                if (pfd == null) {
                    status("Could not open " + out.name);
                    return;
                }
                Payload payload = nearby.newFilePayload(pfd);
                outgoingPayloads.put(payload.getId(), out);

                // Tell the receiver which payload id belongs to this offer, THEN send the file.
                JSONObject body = new JSONObject();
                body.put("offerId", offerId);
                body.put("payloadId", payload.getId());
                MeshMessage start = new MeshMessage(UUID.randomUUID().toString(),
                        MeshMessage.TYPE_FILE_START, nearby.getLocalName(),
                        body.toString(), System.currentTimeMillis(), 1);
                nearby.sendTextTo(out.endpointId, start.toJson());
                nearby.sendPayloadTo(out.endpointId, payload);
                status("Sending " + out.name + "...");
            } catch (Exception e) {
                status("Could not send " + out.name + ": " + e.getMessage());
            }
        });
    }

    // ---- receiving ----

    // Step 2 (receiver): the user answered the dialog.
    public void replyToOffer(String offerId, boolean accepted) {
        FileOffer offer = pendingOffers.remove(offerId);
        if (offer == null) {
            return;
        }
        if (accepted) {
            acceptedOffers.put(offerId, offer);
        }
        try {
            JSONObject body = new JSONObject();
            body.put("offerId", offerId);
            body.put("accepted", accepted);
            MeshMessage reply = new MeshMessage(UUID.randomUUID().toString(),
                    MeshMessage.TYPE_FILE_REPLY, nearby.getLocalName(),
                    body.toString(), System.currentTimeMillis(), 1);
            nearby.sendTextTo(offer.endpointId, reply.toJson());
            status(accepted ? "Accepted " + offer.name + ", waiting for the file"
                    : "Declined " + offer.name);
        } catch (JSONException e) {
            status("Could not answer: " + e.getMessage());
        }
    }

    // Called by the hub for every incoming text. Returns true if it was a file message.
    public boolean handleControl(String endpointId, MeshMessage wire) {
        try {
            switch (wire.type) {
                case MeshMessage.TYPE_FILE_OFFER: {
                    JSONObject body = new JSONObject(wire.text);
                    FileOffer offer = new FileOffer(wire.id, endpointId, wire.senderName,
                            body.getString("name"), body.optLong("size", -1));
                    pendingOffers.put(offer.offerId, offer);
                    if (offer.size > MAX_FILE_BYTES) {
                        replyToOffer(offer.offerId, false);   // too big: decline automatically
                        status("Declined " + offer.name + " (over 25 MB)");
                        return true;
                    }
                    for (Listener l : listeners) {
                        l.onFileOffer(offer);
                    }
                    FileNotifier.show(appContext, offer);
                    return true;
                }
                case MeshMessage.TYPE_FILE_REPLY: {
                    JSONObject body = new JSONObject(wire.text);
                    String offerId = body.getString("offerId");
                    OutgoingFile out = outgoingOffers.remove(offerId);
                    if (out == null) {
                        return true;
                    }
                    if (body.getBoolean("accepted")) {
                        startSending(offerId, out);
                    } else {
                        status(out.name + " was declined");
                    }
                    return true;
                }
                case MeshMessage.TYPE_FILE_START: {
                    JSONObject body = new JSONObject(wire.text);
                    FileOffer offer = acceptedOffers.get(body.getString("offerId"));
                    if (offer != null) {
                        long payloadId = body.getLong("payloadId");
                        expectedPayloads.put(payloadId, offer);
                        tryFinalize(payloadId);
                    }
                    return true;
                }
                default:
                    return false;
            }
        } catch (JSONException e) {
            status("Unreadable file message: " + e.getMessage());
            return true;
        }
    }

    // The file payload itself arrived (it may still be transferring).
    public void onFilePayload(Payload payload) {
        receivedPayloads.put(payload.getId(), payload);
    }

    public void onTransferUpdate(long payloadId, int statusCode,
                                 long bytesTransferred, long totalBytes) {
        OutgoingFile out = outgoingPayloads.get(payloadId);
        FileOffer in = expectedPayloads.get(payloadId);

        if (statusCode == PayloadTransferUpdate.Status.IN_PROGRESS) {
            int percent = totalBytes > 0 ? (int) (100 * bytesTransferred / totalBytes) : 0;
            if (out != null) {
                status("Sending " + out.name + ": " + percent + "%");
            } else if (in != null) {
                status("Receiving " + in.name + ": " + percent + "%");
            }
        } else if (statusCode == PayloadTransferUpdate.Status.SUCCESS) {
            if (out != null) {
                status("Sent " + out.name);
                outgoingPayloads.remove(payloadId);
            }
            if (receivedPayloads.containsKey(payloadId)) {
                finishedPayloads.add(payloadId);
                tryFinalize(payloadId);
            }
        } else if (statusCode == PayloadTransferUpdate.Status.FAILURE) {
            if (out != null) {
                status("Sending " + out.name + " failed");
                outgoingPayloads.remove(payloadId);
            } else if (in != null) {
                status("Receiving " + in.name + " failed");
                expectedPayloads.remove(payloadId);
            }
        } else if (statusCode == PayloadTransferUpdate.Status.CANCELED) {
            status("Transfer canceled");
        }
    }

    // The file is saved only when BOTH the file and the "this payload belongs to this offer"
    // message have arrived, in either order.
    private void tryFinalize(long payloadId) {
        if (!finishedPayloads.contains(payloadId)) {
            return;
        }
        Payload payload = receivedPayloads.get(payloadId);
        FileOffer offer = expectedPayloads.get(payloadId);
        if (payload == null || offer == null) {
            return;
        }
        if (expectedPayloads.remove(payloadId) == null) {
            return;   // another thread already took it
        }
        receivedPayloads.remove(payloadId);
        finishedPayloads.remove(payloadId);
        ioExecutor.execute(() -> saveReceived(offer, payload));
    }

    // Copies Nearby's file (in Downloads) into private app storage under its real name.
    // Opens the received file: first through its Uri (needed on newer Android),
    // then through its old file path (older phones).
    private InputStream openReceived(Payload.File received) throws IOException {
        Uri uri = received.asUri();
        if (uri != null) {
            try {
                InputStream in = appContext.getContentResolver().openInputStream(uri);
                if (in != null) {
                    return in;
                }
            } catch (Exception ignored) {
                // fall through and try the file path
            }
        }
        File javaFile = received.asJavaFile();
        if (javaFile != null) {
            return new FileInputStream(javaFile);   // Android 9 and below may need storage permission
        }
        throw new IOException("no readable file");
    }

    // Copies Nearby's file (in Downloads) into private app storage under its real name.
    private void saveReceived(FileOffer offer, Payload payload) {
        String safeName = offer.name.replaceAll("[\\\\/:*?\"<>|]", "_");
        File target = new File(receivedDir(), safeName);
        int n = 1;
        while (target.exists()) {
            target = new File(receivedDir(), n + "_" + safeName);
            n++;
        }

        Payload.File received = payload.asFile();
        if (received == null) {
            status("Could not read the received file");
            return;
        }

        try (InputStream in = openReceived(received);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } catch (IOException e) {
            target.delete();   // remove a half-written copy
            status("Could not save " + offer.name + ": " + e.getMessage());
            return;
        }

        // Best effort: remove Nearby's nameless copy from Downloads.
        try {
            Uri uri = received.asUri();
            if (uri != null) {
                appContext.getContentResolver().delete(uri, null, null);
            }
        } catch (Exception ignored) {
            // leaving the copy behind is harmless
        }
        try {
            File javaFile = received.asJavaFile();
            if (javaFile != null) {
                javaFile.delete();
            }
        } catch (Exception ignored) {
            // same
        }

        status("Received " + target.getName());
        for (Listener l : listeners) {
            l.onFilesChanged();
        }
    }
}