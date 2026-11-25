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
        view.findViewById(R.id.key_enter).setOnClickListener(v -> bluetoothManager.sendEnter());
        view.findViewById(R.id.key_tab).setOnClickListener(v -> bluetoothManager.sendTab());
        view.findViewById(R.id.key_esc).setOnClickListener(v -> bluetoothManager.sendEscape());
        view.findViewById(R.id.key_backspace).setOnClickListener(v -> bluetoothManager.sendBackspace());
        view.findViewById(R.id.key_space).setOnClickListener(v -> bluetoothManager.sendCharacter(' '));
        view.findViewById(R.id.key_left).setOnClickListener(v -> bluetoothManager.sendArrow(0x50));
        view.findViewById(R.id.key_down).setOnClickListener(v -> bluetoothManager.sendArrow(0x51));
        view.findViewById(R.id.key_up).setOnClickListener(v -> bluetoothManager.sendArrow(0x52));
        view.findViewById(R.id.key_right).setOnClickListener(v -> bluetoothManager.sendArrow(0x4F));
        view.findViewById(R.id.key_shift).setOnClickListener(v -> {
            shiftEnabled = !shiftEnabled;
            bluetoothManager.setModifier(0x02, shiftEnabled);
        });
        view.findViewById(R.id.key_ctrl).setOnClickListener(v -> {
            ctrlEnabled = !ctrlEnabled;
            bluetoothManager.setModifier(0x01, ctrlEnabled);
        });
        view.findViewById(R.id.key_alt).setOnClickListener(v -> {
            altEnabled = !altEnabled;
            bluetoothManager.setModifier(0x04, altEnabled);
        });

        android.widget.LinearLayout functionKeys = view.findViewById(R.id.function_keys);
        for (int i = 0; i < 12; i++) {
            final int usage = 0x3A + i;
            Button functionKey = new Button(requireContext());
            functionKey.setText("F" + (i + 1));
            functionKey.setOnClickListener(v -> bluetoothManager.sendArrow(usage));
            functionKeys.addView(functionKey);
        }

        android.widget.GridLayout keyboard = view.findViewById(R.id.qwerty_keyboard);
        addQwertyRow(keyboard, "1234567890", bluetoothManager);
        addQwertyRow(keyboard, "QWERTYUIOP", bluetoothManager);
        addQwertyRow(keyboard, "ASDFGHJKL", bluetoothManager);
        addQwertyRow(keyboard, "ZXCVBNM", bluetoothManager);
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

    private void addQwertyRow(android.widget.GridLayout keyboard, String row, BluetoothManager bluetoothManager) {
        for (char character : row.toCharArray()) {
            Button key = new Button(requireContext());
            key.setText(String.valueOf(character));
            key.setAllCaps(false);
            key.setPadding(4, 4, 4, 4);
            key.setOnClickListener(v -> bluetoothManager.sendCharacter(character));
            android.widget.GridLayout.LayoutParams params =
                new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            keyboard.addView(key, params);
        }
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
