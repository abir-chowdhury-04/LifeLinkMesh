package com.abir2307055.lifelinkmesh.mesh;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.ChatMessage;
import com.abir2307055.lifelinkmesh.data.SosAlert;

import java.util.List;

// A Content Provider is Android's standard "door" to an app's data, addressed by URIs.
// This one is read-only and exposes the saved chat messages and SOS alerts.
public class MeshLogProvider extends ContentProvider {

    public static final String AUTHORITY = "com.abir2307055.lifelinkmesh.provider";
    public static final Uri MESSAGES_URI = Uri.parse("content://" + AUTHORITY + "/messages");
    public static final Uri SOS_URI = Uri.parse("content://" + AUTHORITY + "/sos");

    private static final int CODE_MESSAGES = 1;
    private static final int CODE_SOS = 2;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "messages", CODE_MESSAGES);
        MATCHER.addURI(AUTHORITY, "sos", CODE_SOS);
    }

    @Override
    public boolean onCreate() {
        return true;   // nothing to prepare; the database is opened when first needed
    }

    // projection, selection and sortOrder are ignored: we always return every row,
    // oldest first.
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        AppDatabase db = AppDatabase.getInstance(context);

        switch (MATCHER.match(uri)) {
            case CODE_MESSAGES: {
                MatrixCursor cursor = new MatrixCursor(new String[]{
                        "id", "senderName", "text", "timestamp", "mine"});
                List<ChatMessage> list = db.messageDao().getAll();
                for (ChatMessage m : list) {
                    cursor.addRow(new Object[]{
                            m.id, m.senderName, m.text, m.timestamp, m.mine ? 1 : 0});
                }
                return cursor;
            }
            case CODE_SOS: {
                MatrixCursor cursor = new MatrixCursor(new String[]{
                        "id", "senderName", "category", "note", "timestamp",
                        "hasLocation", "latitude", "longitude", "accuracy", "mine"});
                List<SosAlert> list = db.sosDao().getAll();
                for (SosAlert a : list) {
                    cursor.addRow(new Object[]{
                            a.id, a.senderName, a.category, a.note, a.timestamp,
                            a.hasLocation ? 1 : 0, a.latitude, a.longitude,
                            a.accuracy, a.mine ? 1 : 0});
                }
                return cursor;
            }
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    // Tells the caller what kind of data a URI returns (a list of rows).
    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case CODE_MESSAGES:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".message";
            case CODE_SOS:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".sos";
            default:
                return null;
        }
    }

    // The log is read-only: nobody can change it through the provider.
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("The log is read-only");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("The log is read-only");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("The log is read-only");
    }
}