package com.koolkeys.app.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.koolkeys.app.KoolKeysApplication;
import com.koolkeys.app.MainActivity;

public class InsertionForegroundService extends Service {
    public static final String EXTRA_MESSAGE = "message";
    private static final String COMPLETED_MESSAGE = "Code insertion completed";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String message = intent == null ? "KoolKeys session" : intent.getStringExtra(EXTRA_MESSAGE);
        boolean completed = COMPLETED_MESSAGE.equals(message);
        boolean event = completed || "Code insertion paused".equals(message)
            || (message != null && message.startsWith("Review reminder"));
        PendingIntent openApp = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, KoolKeysApplication.SESSION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("KoolKeys")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(!completed)
            .setAutoCancel(completed)
            .build();
        startForeground(1001, notification);
        if (event) postEventNotification(message, openApp);
        if (completed) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    private void postEventNotification(String message, PendingIntent openApp) {
        if (!((KoolKeysApplication) getApplication()).areNotificationsEnabled()) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            && androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Notification eventNotification = new NotificationCompat.Builder(this,
            KoolKeysApplication.EVENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KoolKeys")
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build();
        NotificationManagerCompat.from(this).notify(1002, eventNotification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
