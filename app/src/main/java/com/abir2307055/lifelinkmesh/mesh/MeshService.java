package com.abir2307055.lifelinkmesh.mesh;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.abir2307055.lifelinkmesh.MainActivity;

// Keeps the mesh alive in the background. Android requires a visible notification
// while a foreground service runs.
public class MeshService extends Service {

    private static final String CHANNEL_ID = "mesh_service";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        // Must be called quickly after the service starts.
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

        MeshHub.getInstance(this).start();
        // If Android kills the service, ask it to restart it.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MeshHub.getInstance(this).stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;   // nobody binds to this service; it is only started and stopped
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Mesh service", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        // Tapping the notification brings the app back.
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, openApp, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LifeLink Mesh is running")
                .setContentText("Looking for nearby phones")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }
}