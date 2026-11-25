package com.koolkeys.app.ui;

import android.os.Bundle;
import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import com.koolkeys.app.R;
import com.koolkeys.app.bluetooth.BluetoothManager;
import com.koolkeys.app.ui.widget.TouchpadView;

public class HomeFragment extends Fragment {
    private BluetoothManager bluetoothManager;
    private TextView status;
    private boolean shiftEnabled;
    private boolean ctrlEnabled;
    private boolean altEnabled;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        status = view.findViewById(R.id.connection_status);
        bluetoothManager = ((com.koolkeys.app.KoolKeysApplication) requireActivity().getApplication())
            .getBluetoothManager();
        Button connect = view.findViewById(R.id.connect_button);
        bluetoothManager.setStatusListener(newStatus -> {
            if (status != null) status.setText(newStatus);
            if (connect != null) connect.setText(bluetoothManager.isConnected() ? "Disconnect" : "Connect");
        });
        updateStatus();
        connect.setOnClickListener(v -> {
            if (bluetoothManager.isConnected()) {
                bluetoothManager.disconnect();
                return;
            }
            showPairedDevices();
        });

        TouchpadView touchpad = view.findViewById(R.id.touchpad);
        touchpad.setBluetoothManager(bluetoothManager);
        view.findViewById(R.id.left_click).setOnClickListener(v -> bluetoothManager.mouseClick(false));
        view.findViewById(R.id.right_click).setOnClickListener(v -> bluetoothManager.mouseClick(true));
        com.hijamoya.keyboardview.KeyboardView keyboard =
            view.findViewById(R.id.qwerty_keyboard);
        keyboard.setKeyboard(new com.hijamoya.keyboardview.Keyboard(
            requireContext(), R.xml.qwerty_keyboard));
        keyboard.setOnKeyboardActionListener(new com.hijamoya.keyboardview.KeyboardView.OnKeyboardActionListener() {
            @Override public void onPress(int primaryCode) { }
            @Override public void onRelease(int primaryCode) { }
            @Override public void onKey(int primaryCode, int[] keyCodes) {
                if (primaryCode == -1) {
                    shiftEnabled = !shiftEnabled;
                    bluetoothManager.setModifier(0x02, shiftEnabled);
                } else if (primaryCode == -1001) {
                    ctrlEnabled = !ctrlEnabled;
                    bluetoothManager.setModifier(0x01, ctrlEnabled);
                } else if (primaryCode == -6) {
                    altEnabled = !altEnabled;
                    bluetoothManager.setModifier(0x04, altEnabled);
                } else if (primaryCode == -5) {
                    bluetoothManager.sendBackspace();
                } else if (primaryCode == 10) {
                    bluetoothManager.sendEnter();
                } else if (primaryCode == 9) {
                    bluetoothManager.sendTab();
                } else if (primaryCode == 27) {
                    bluetoothManager.sendEscape();
                } else if (primaryCode >= 5001 && primaryCode <= 5005) {
                    bluetoothManager.sendArrow(0x3A + primaryCode - 5001);
                } else if (primaryCode == 5006) {
                    bluetoothManager.sendArrow(0x50);
                } else if (primaryCode == 5007) {
                    bluetoothManager.sendArrow(0x51);
                } else if (primaryCode == 5008) {
                    bluetoothManager.sendArrow(0x52);
                } else if (primaryCode == 5009) {
                    bluetoothManager.sendArrow(0x4F);
                } else if (primaryCode > 0) {
                    bluetoothManager.sendCharacter((char) primaryCode);
                }
            }
            @Override public void onText(CharSequence text) {
                if (text != null) {
                    for (int i = 0; i < text.length(); i++) {
                        bluetoothManager.sendCharacter(text.charAt(i));
                    }
                }
            }
            @Override public void swipeLeft() { }
            @Override public void swipeRight() { }
            @Override public void swipeDown() { }
            @Override public void swipeUp() { }
        });
        return view;
    }

    private void showPairedDevices() {
        java.util.List<BluetoothDevice> devices = bluetoothManager.getBondedDevices();
        if (devices.isEmpty()) {
            bluetoothManager.registerHid(null);
            new AlertDialog.Builder(requireContext())
                .setTitle("Pair with Windows")
                .setMessage("KoolKeys will register as a Bluetooth keyboard and mouse. Then open Windows Bluetooth settings and pair with KoolKeys.")
                .setPositiveButton("Got it", null)
                .show();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            String name = devices.get(i).getName();
            names[i] = name == null ? devices.get(i).getAddress() : name;
        }
        new AlertDialog.Builder(requireContext())
            .setTitle("Connect to paired device")
            .setItems(names, (dialog, index) -> bluetoothManager.registerHid(devices.get(index)))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateStatus() {
        status.setText(bluetoothManager.getStatusSummary());
    }

    @Override
    public void onDestroyView() {
        bluetoothManager.clearModifiers();
        bluetoothManager.setStatusListener(null);
        status = null;
        super.onDestroyView();
    }
}
