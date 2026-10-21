package com.abir2307055.lifelinkmesh.mesh;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.abir2307055.lifelinkmesh.R;
import com.abir2307055.lifelinkmesh.SosActivity;
import com.abir2307055.lifelinkmesh.data.SosAlert;

// Shows a notification when an SOS arrives from another phone.
public class SosNotifier {

    private static final String CHANNEL_ID = "sos_alerts";

    public static void show(Context context, SosAlert alert) {
        // On Android 13+ notifications need a runtime permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "SOS alerts", NotificationManager.IMPORTANCE_HIGH);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);

        // Tapping the notification opens the SOS screen.
        Intent open = new Intent(context, SosActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context, alert.id.hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String note = (alert.note == null || alert.note.isEmpty()) ? "(no note)" : alert.note;
        String where = alert.hasLocation ? "with location" : "no location";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("SOS: " + alert.category + " · " + alert.senderName)
                .setContentText(note + " (" + where + ")")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending);

        // One notification per alert (different id each time).
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), builder.build());
    }
}