package com.koolkeys.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.koolkeys.app.bluetooth.BluetoothManager;
import com.koolkeys.app.MainActivity;
import com.koolkeys.app.service.InsertionForegroundService;
import com.koolkeys.app.session.SessionManager;

public class KoolKeysApplication extends Application {
    public static final String SESSION_CHANNEL_ID = "koolkeys_session";
    public static final String EVENT_CHANNEL_ID = "koolkeys_events";
    private BluetoothManager bluetoothManager;
    private SessionManager sessionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothManager = new BluetoothManager(this);
        sessionManager = new SessionManager(bluetoothManager);
        sessionManager.setLifecycleListener(new SessionManager.LifecycleListener() {
            @Override public void onComplete() {
                postEventNotification("Code insertion completed");
                stopService(new Intent(KoolKeysApplication.this, InsertionForegroundService.class));
            }

            @Override public void onReviewReminder() {
                postEventNotification("Review reminder — check the inserted code");
            }

            @Override public void onStopped() {
                stopService(new Intent(KoolKeysApplication.this, InsertionForegroundService.class));
            }
        });
        createNotificationChannels(false);
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public void onTerminate() {
        bluetoothManager.shutdown();
        super.onTerminate();
    }

    public boolean areNotificationsEnabled() {
        return getSharedPreferences("koolkeys_settings", MODE_PRIVATE)
            .getBoolean("notifications", true);
    }

    public void refreshNotificationChannels() {
        createNotificationChannels(true);
    }

    private void createNotificationChannels(boolean replaceEventChannel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel sessionChannel = new NotificationChannel(
            SESSION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW);
        sessionChannel.setDescription(getString(R.string.notification_channel_description));
        sessionChannel.setSound(null, null);
        sessionChannel.enableVibration(false);
        manager.createNotificationChannel(sessionChannel);

        if (replaceEventChannel) manager.deleteNotificationChannel(EVENT_CHANNEL_ID);
        android.content.SharedPreferences prefs = getSharedPreferences("koolkeys_settings", MODE_PRIVATE);
        NotificationChannel eventChannel = new NotificationChannel(
            EVENT_CHANNEL_ID,
            getString(R.string.notification_event_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT);
        eventChannel.setDescription(getString(R.string.notification_event_channel_description));
        eventChannel.enableVibration(prefs.getBoolean("vibration", true));
        if (prefs.getBoolean("sound", true)) {
            Uri sound = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            eventChannel.setSound(sound, attributes);
        } else {
            eventChannel.setSound(null, null);
        }
        manager.createNotificationChannel(eventChannel);
    }

    private void postEventNotification(String message) {
        if (!areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        PendingIntent openApp = PendingIntent.getActivity(this, message.hashCode(),
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, EVENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KoolKeys")
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true);
        NotificationManagerCompat.from(this).notify(message.hashCode(), notification.build());
    }
}
