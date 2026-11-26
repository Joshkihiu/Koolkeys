package com.koolkeys.app.bluetooth;

import android.app.PendingIntent;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.koolkeys.app.KoolKeysApplication;
import com.koolkeys.app.MainActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class BluetoothManager {
    public interface StatusListener {
        void onStatusChanged(String status);
    }

    private static final int KEYBOARD_REPORT_ID = 1;
    private static final int MOUSE_REPORT_ID = 2;
    private static final int MODIFIER_SHIFT = 0x02;
    private static final long KEY_PRESS_DURATION_MILLIS = 40;
    private static final byte[] REPORT_DESCRIPTOR = {
        (byte) 0x05, (byte) 0x01, (byte) 0x09, (byte) 0x06, (byte) 0xA1, (byte) 0x01,
        (byte) 0x85, (byte) 0x01, (byte) 0x05, (byte) 0x07, (byte) 0x19, (byte) 0xE0,
        (byte) 0x29, (byte) 0xE7, (byte) 0x15, (byte) 0x00, (byte) 0x25, (byte) 0x01,
        (byte) 0x75, (byte) 0x01, (byte) 0x95, (byte) 0x08, (byte) 0x81, (byte) 0x02,
        (byte) 0x95, (byte) 0x01, (byte) 0x75, (byte) 0x08, (byte) 0x81, (byte) 0x01,
        (byte) 0x95, (byte) 0x06, (byte) 0x75, (byte) 0x08, (byte) 0x15, (byte) 0x00,
        (byte) 0x25, (byte) 0x65, (byte) 0x05, (byte) 0x07, (byte) 0x19, (byte) 0x00,
        (byte) 0x29, (byte) 0x65, (byte) 0x81, (byte) 0x00, (byte) 0xC0,
        (byte) 0x05, (byte) 0x01, (byte) 0x09, (byte) 0x02, (byte) 0xA1, (byte) 0x01,
        (byte) 0x85, (byte) 0x02, (byte) 0x09, (byte) 0x01, (byte) 0xA1, (byte) 0x00,
        (byte) 0x05, (byte) 0x09, (byte) 0x19, (byte) 0x01, (byte) 0x29, (byte) 0x03,
        (byte) 0x15, (byte) 0x00, (byte) 0x25, (byte) 0x01, (byte) 0x95, (byte) 0x03,
        (byte) 0x75, (byte) 0x01, (byte) 0x81, (byte) 0x02, (byte) 0x95, (byte) 0x01,
        (byte) 0x75, (byte) 0x05, (byte) 0x81, (byte) 0x01, (byte) 0x05, (byte) 0x01,
        (byte) 0x09, (byte) 0x30, (byte) 0x09, (byte) 0x31, (byte) 0x09, (byte) 0x38,
        (byte) 0x15, (byte) 0x81, (byte) 0x25, (byte) 0x7F, (byte) 0x75, (byte) 0x08,
        (byte) 0x95, (byte) 0x03, (byte) 0x81, (byte) 0x06, (byte) 0xC0, (byte) 0xC0
    };

    private final Context context;
    private final BluetoothAdapter adapter;
    private final Executor callbackExecutor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice pendingDevice;
    private BluetoothDevice connectedDevice;
    private StatusListener statusListener;
    private boolean appRegistered;
    private boolean profileRequestPending;
    private boolean registrationDesired;
    private boolean receiverRegistered;
    private int registrationAttempts;
    private long registrationGeneration;
    private String status = "Bluetooth not initialized";
    private int modifierMask;
    private final Runnable registrationTimeout = () -> {
        profileRequestPending = false;
        if (!appRegistered) {
            retryRegistrationOrReportTimeout();
        }
    };

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receivingContext, Intent intent) {
            if (intent == null || !BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_OFF) {
                cleanupHidProxy();
                connectedDevice = null;
                appRegistered = false;
                updateStatus("Bluetooth off. Turn it on to restore HID.");
            } else if (state == BluetoothAdapter.STATE_ON) {
                updateStatus("Bluetooth on.");
                if (registrationDesired) {
                    handler.postDelayed(() -> {
                        if (registrationDesired) registerHid(pendingDevice);
                    }, 750);
                }
            }
        }
    };

    private final BluetoothHidDevice.Callback hidCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
            appRegistered = registered;
            profileRequestPending = false;
            handler.removeCallbacks(registrationTimeout);
            if (!registered) {
                connectedDevice = null;
                modifierMask = 0;
                updateStatus("HID profile registration was lost. Keep KoolKeys open and try again.");
            } else if (pendingDevice != null) {
                registrationAttempts = 0;
                connectToPendingDevice();
            } else {
                registrationAttempts = 0;
                updateStatus("HID ready. Pair this phone from Windows Bluetooth settings.");
            }
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                boolean wasConnected = connectedDevice != null;
                connectedDevice = device;
                pendingDevice = null;
                updateStatus("Connected to " + safeDeviceName(device));
                if (!wasConnected) postConnectionNotification("Connection restored");
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                boolean wasConnected = connectedDevice != null && connectedDevice.equals(device);
                if (wasConnected) {
                    connectedDevice = null;
                    modifierMask = 0;
                }
                updateStatus("Disconnected from Windows host");
                if (wasConnected) postConnectionNotification("Bluetooth disconnected");
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                updateStatus("Connecting to " + safeDeviceName(device));
            }
        }
    };

    public BluetoothManager(Context context) {
        this.context = context.getApplicationContext();
        android.bluetooth.BluetoothManager manager =
            (android.bluetooth.BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        callbackExecutor = ContextCompat.getMainExecutor(context);
        IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this.context, bluetoothStateReceiver, bluetoothFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    public void setStatusListener(StatusListener listener) {
        statusListener = listener;
        if (listener != null) listener.onStatusChanged(getStatusSummary());
    }

    public boolean hasHidApi() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
            && adapter != null;
    }

    public List<BluetoothDevice> getBondedDevices() {
        if (adapter == null || !hasConnectPermission()) return Collections.emptyList();
        try {
            return new ArrayList<>(adapter.getBondedDevices());
        } catch (SecurityException exception) {
            updateStatus("Bluetooth permission is required to list paired devices.");
            return Collections.emptyList();
        }
    }

    public void registerHid(BluetoothDevice device) {
        registrationDesired = true;
        pendingDevice = device;
        if (!hasHidApi()) {
            updateStatus("Bluetooth HID Device requires Android 9+ and a compatible phone.");
            return;
        }
        if (adapter == null) {
            updateStatus("Bluetooth is unavailable.");
            return;
        }
        if (!hasConnectPermission()) {
            updateStatus("Allow Nearby devices permission to use Bluetooth HID.");
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                updateStatus("Turn on Bluetooth, then try Connect again.");
                return;
            }
            if (hidDevice != null && appRegistered) {
                if (device == null) updateStatus("HID ready. Pair this phone from Windows Bluetooth settings.");
                else connectToPendingDevice();
                return;
            }
            if (profileRequestPending) {
                updateStatus("Opening Bluetooth HID profile...");
                return;
            }
            boolean requested = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE);
            if (!requested) updateStatus("This phone could not open the Bluetooth HID Device profile.");
            else {
                profileRequestPending = true;
                registrationAttempts++;
                updateStatus("Opening Bluetooth HID profile...");
                handler.removeCallbacks(registrationTimeout);
                handler.postDelayed(registrationTimeout, 15000);
            }
        } catch (SecurityException exception) {
            updateStatus("Bluetooth permission was denied. Allow Nearby devices and retry.");
        }
    }

    public void disconnect() {
        registrationDesired = false;
        if (hidDevice == null) {
            updateStatus("Not connected");
            return;
        }
        try {
            if (connectedDevice != null) {
                hidDevice.disconnect(connectedDevice);
            } else if (appRegistered) {
                hidDevice.unregisterApp();
                updateStatus("HID disconnected");
            }
            handler.postDelayed(this::cleanupHidProxy, 250);
        } catch (SecurityException exception) {
            updateStatus("Bluetooth permission was denied while disconnecting.");
        }
    }

    public void shutdown() {
        registrationDesired = false;
        handler.removeCallbacks(registrationTimeout);
        cleanupHidProxy();
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothStateReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
    }

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            profileRequestPending = false;
            if (profile != BluetoothProfile.HID_DEVICE || !(proxy instanceof BluetoothHidDevice)) return;
            hidDevice = (BluetoothHidDevice) proxy;
            BluetoothHidDeviceAppSdpSettings settings = new BluetoothHidDeviceAppSdpSettings(
                "KoolKeys", "Wireless keyboard and mouse", "KoolKeys", (byte) 0xC0, REPORT_DESCRIPTOR);
            try {
                boolean accepted = hidDevice.registerApp(settings, null, null, callbackExecutor, hidCallback);
                if (!accepted) updateStatus("Android rejected HID registration. This phone may not support HID Device.");
                else {
                    updateStatus("Registering as a Bluetooth keyboard and mouse...");
                    handler.removeCallbacks(registrationTimeout);
                    handler.postDelayed(registrationTimeout, 15000);
                }
            } catch (SecurityException exception) {
                updateStatus("Bluetooth permission was denied during HID registration.");
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            profileRequestPending = false;
            handler.removeCallbacks(registrationTimeout);
            hidDevice = null;
            appRegistered = false;
            connectedDevice = null;
            modifierMask = 0;
            updateStatus("Bluetooth HID profile disconnected. Tap Connect to retry.");
        }
    };

    private void retryRegistrationOrReportTimeout() {
        if (!registrationDesired || appRegistered) return;
        if (registrationAttempts < 2) {
            updateStatus("HID registration timed out. Retrying automatically...");
            cleanupHidProxy();
            handler.postDelayed(() -> {
                if (registrationDesired && !appRegistered) registerHid(pendingDevice);
            }, 750);
            return;
        }
        updateStatus("HID registration timed out after retries. Turn Bluetooth off/on or reopen KoolKeys.");
        cleanupHidProxy();
    }

    private void cleanupHidProxy() {
        handler.removeCallbacks(registrationTimeout);
        profileRequestPending = false;
        BluetoothHidDevice proxy = hidDevice;
        boolean wasRegistered = appRegistered;
        hidDevice = null;
        appRegistered = false;
        connectedDevice = null;
        modifierMask = 0;
        if (proxy != null) {
            try {
                if (wasRegistered) proxy.unregisterApp();
            } catch (SecurityException ignored) {
            }
            try {
                adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy);
            } catch (Exception ignored) {
            }
        }
    }

    private void connectToPendingDevice() {
        if (hidDevice == null || pendingDevice == null || !hasConnectPermission()) return;
        try {
            boolean accepted = hidDevice.connect(pendingDevice);
            if (!accepted) updateStatus("Could not start connection. Pair KoolKeys in Windows Bluetooth settings.");
        } catch (SecurityException exception) {
            updateStatus("Bluetooth permission was denied while connecting.");
        }
    }

    public boolean isConnected() {
        return connectedDevice != null;
    }

    public String getStatusSummary() {
        if (adapter == null) return "Bluetooth unavailable";
        if (!hasHidApi()) return "Bluetooth HID Device requires Android 9+";
        if (!hasConnectPermission()) return "Nearby devices permission required";
        try {
            if (!adapter.isEnabled()) return "Bluetooth off";
        } catch (SecurityException exception) {
            return "Nearby devices permission required";
        }
        return status;
    }

    public void mouseMove(int dx, int dy) {
        if (connectedDevice == null) return;
        sendReport(MOUSE_REPORT_ID, new byte[]{0, clamp(dx), clamp(dy), 0});
    }

    public void mouseClick(boolean right) {
        if (connectedDevice == null) {
            updateStatus("Connect KoolKeys to a Windows host before sending input.");
            return;
        }
        sendReport(MOUSE_REPORT_ID, new byte[]{(byte) (right ? 2 : 1), 0, 0, 0});
        sendReport(MOUSE_REPORT_ID, new byte[]{0, 0, 0, 0});
    }

    public void setModifier(int modifier, boolean enabled) {
        if (connectedDevice == null) {
            updateStatus("Connect KoolKeys to a Windows host before sending input.");
            return;
        }
        modifierMask = enabled ? modifierMask | modifier : modifierMask & ~modifier;
        sendKeyboardReport(0, modifierMask);
    }

    public void clearModifiers() {
        modifierMask = 0;
        if (connectedDevice != null) sendKeyboardReport(0, 0);
    }

    public boolean sendCharacter(char character) {
        int[] key = mapUsKeyboardCharacter(character);
        if (key == null) {
            updateStatus(String.format("Cannot type U+%04X with the US keyboard map.", (int) character));
            return false;
        }
        return sendKey(key[0], key[1]);
    }

    public boolean sendEnter() { return sendKey(0x28, 0); }
    public boolean sendTab() { return sendKey(0x2B, 0); }
    public boolean sendBackspace() { return sendKey(0x2A, 0); }
    public boolean sendEscape() { return sendKey(0x29, 0); }
    public boolean sendArrow(int usage) { return sendKey(usage, 0); }

    public String validateText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\r' || character == '\n' || character == '\t') continue;
            if (mapUsKeyboardCharacter(character) == null) {
                return String.format("Unsupported character U+%04X at position %d.", (int) character, i + 1);
            }
        }
        return null;
    }

    private boolean sendKey(int usage, int extraModifiers) {
        if (connectedDevice == null) {
            updateStatus("Connect KoolKeys to a Windows host before sending input.");
            return false;
        }
        boolean pressed = sendKeyboardReport(usage, modifierMask | extraModifiers);
        if (usage != 0) SystemClock.sleep(KEY_PRESS_DURATION_MILLIS);
        boolean released = sendKeyboardReport(0, modifierMask);
        return pressed && released;
    }

    private boolean sendKeyboardReport(int usage, int modifiers) {
        byte[] report = new byte[8];
        report[0] = (byte) modifiers;
        if (usage != 0) report[2] = (byte) usage;
        return sendReport(KEYBOARD_REPORT_ID, report);
    }

    private boolean sendReport(int reportId, byte[] report) {
        if (hidDevice == null || connectedDevice == null || !hasConnectPermission()) return false;
        try {
            boolean accepted = hidDevice.sendReport(connectedDevice, reportId, report);
            if (!accepted) updateStatus("Bluetooth HID rejected an input report. Check the Windows connection.");
            return accepted;
        } catch (SecurityException exception) {
            updateStatus("Bluetooth permission was denied while sending input.");
            return false;
        }
    }

    private String safeDeviceName(BluetoothDevice device) {
        if (!hasConnectPermission()) return "Windows host";
        try {
            String name = device.getName();
            return name == null ? "Windows host" : name;
        } catch (SecurityException exception) {
            return "Windows host";
        }
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatus(String newStatus) {
        status = newStatus;
        if (statusListener != null) statusListener.onStatusChanged(newStatus);
    }

    private void postConnectionNotification(String message) {
        if (!(context instanceof KoolKeysApplication)
            || !((KoolKeysApplication) context).areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        Intent launchIntent = new Intent(context, MainActivity.class);
        PendingIntent openApp = PendingIntent.getActivity(context, message.hashCode(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
            KoolKeysApplication.EVENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KoolKeys")
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(message.hashCode(), notification.build());
    }

    private static byte clamp(int value) {
        return (byte) Math.max(-127, Math.min(127, value));
    }

    private static int[] mapUsKeyboardCharacter(char c) {
        if (c >= 'a' && c <= 'z') return new int[]{0x04 + c - 'a', 0};
        if (c >= 'A' && c <= 'Z') return new int[]{0x04 + c - 'A', MODIFIER_SHIFT};
        if (c >= '1' && c <= '9') return new int[]{0x1E + c - '1', 0};
        if (c == '0') return new int[]{0x27, 0};
        if (c == ' ') return new int[]{0x2C, 0};
        if (c >= '!' && c <= ')' && c != '"' && c != '\'') {
            String shiftedDigits = "!@#$%^&*(";
            int index = shiftedDigits.indexOf(c);
            if (index >= 0) return new int[]{0x1E + index, MODIFIER_SHIFT};
        }
        switch (c) {
            case '\n': case '\r': return new int[]{0x28, 0};
            case '\t': return new int[]{0x2B, 0};
            case '-': return new int[]{0x2D, 0};
            case '_': return new int[]{0x2D, MODIFIER_SHIFT};
            case '=': return new int[]{0x2E, 0};
            case '+': return new int[]{0x2E, MODIFIER_SHIFT};
            case '[': return new int[]{0x2F, 0};
            case '{': return new int[]{0x2F, MODIFIER_SHIFT};
            case ']': return new int[]{0x30, 0};
            case '}': return new int[]{0x30, MODIFIER_SHIFT};
            case '\\': return new int[]{0x31, 0};
            case '|': return new int[]{0x31, MODIFIER_SHIFT};
            case ';': return new int[]{0x33, 0};
            case ':': return new int[]{0x33, MODIFIER_SHIFT};
            case '\'': return new int[]{0x34, 0};
            case '"': return new int[]{0x34, MODIFIER_SHIFT};
            case '`': return new int[]{0x35, 0};
            case '~': return new int[]{0x35, MODIFIER_SHIFT};
            case ',': return new int[]{0x36, 0};
            case '<': return new int[]{0x36, MODIFIER_SHIFT};
            case '.': return new int[]{0x37, 0};
            case '>': return new int[]{0x37, MODIFIER_SHIFT};
            case '/': return new int[]{0x38, 0};
            case '?': return new int[]{0x38, MODIFIER_SHIFT};
            case '!': return new int[]{0x1E, MODIFIER_SHIFT};
            case '@': return new int[]{0x1F, MODIFIER_SHIFT};
            case '#': return new int[]{0x20, MODIFIER_SHIFT};
            case '$': return new int[]{0x21, MODIFIER_SHIFT};
            case '%': return new int[]{0x22, MODIFIER_SHIFT};
            case '^': return new int[]{0x23, MODIFIER_SHIFT};
            case '&': return new int[]{0x24, MODIFIER_SHIFT};
            case '*': return new int[]{0x25, MODIFIER_SHIFT};
            case '(': return new int[]{0x26, MODIFIER_SHIFT};
            case ')': return new int[]{0x27, MODIFIER_SHIFT};
            default: return null;
        }
    }
}
