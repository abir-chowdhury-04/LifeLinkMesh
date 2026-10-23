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

import com.abir2307055.lifelinkmesh.FileActivity;
import com.abir2307055.lifelinkmesh.model.FileOffer;

// Shows a notification when another phone offers me a file.
public class FileNotifier {

    private static final String CHANNEL_ID = "file_offers";

    public static void show(Context context, FileOffer offer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "File offers", NotificationManager.IMPORTANCE_DEFAULT);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);

        Intent open = new Intent(context, FileActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context, offer.offerId.hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("File offer from " + offer.senderName)
                .setContentText(offer.name + " (" + offer.sizeText() + ")")
                .setAutoCancel(true)
                .setContentIntent(pending);

        NotificationManagerCompat.from(context).notify(offer.offerId.hashCode(), builder.build());
    }
}