package com.koolkeys.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.koolkeys.app.R;

public class SettingsFragment extends Fragment {
    private static final String PREFS = "koolkeys_settings";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        CheckBox notifications = view.findViewById(R.id.notifications_setting);
        CheckBox sound = view.findViewById(R.id.sound_setting);
        CheckBox vibration = view.findViewById(R.id.vibration_setting);
        notifications.setChecked(prefs.getBoolean("notifications", true));
        sound.setChecked(prefs.getBoolean("sound", true));
        vibration.setChecked(prefs.getBoolean("vibration", true));

        Spinner review = view.findViewById(R.id.review_spinner);
        ArrayAdapter<CharSequence> reviewAdapter = ArrayAdapter.createFromResource(requireContext(),
            R.array.review_options, android.R.layout.simple_spinner_item);
        reviewAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        review.setAdapter(reviewAdapter);
        review.setSelection(prefs.getInt("review_index", 1));

        Spinner defaultDuration = view.findViewById(R.id.default_duration_spinner);
        ArrayAdapter<CharSequence> durationAdapter = ArrayAdapter.createFromResource(requireContext(),
            R.array.duration_options, android.R.layout.simple_spinner_item);
        durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        defaultDuration.setAdapter(durationAdapter);
        defaultDuration.setSelection(prefs.getInt("default_duration_index", 2));

        Spinner layout = view.findViewById(R.id.layout_spinner);
        ArrayAdapter<CharSequence> layoutAdapter = ArrayAdapter.createFromResource(requireContext(),
            R.array.keyboard_layout_options, android.R.layout.simple_spinner_item);
        layoutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        layout.setAdapter(layoutAdapter);
        layout.setSelection(prefs.getInt("layout_index", 0));

        TextView hidStatus = view.findViewById(R.id.hid_status);
        hidStatus.setText(((com.koolkeys.app.KoolKeysApplication) requireActivity().getApplication())
            .getBluetoothManager().getStatusSummary());

        View save = view.findViewById(R.id.save_settings);
        save.setOnClickListener(v -> {
            prefs.edit()
                .putBoolean("notifications", notifications.isChecked())
                .putBoolean("sound", sound.isChecked())
                .putBoolean("vibration", vibration.isChecked())
                .putInt("review_index", review.getSelectedItemPosition())
                .putInt("default_duration_index", defaultDuration.getSelectedItemPosition())
                .putInt("layout_index", layout.getSelectedItemPosition())
                .apply();
            ((com.koolkeys.app.KoolKeysApplication) requireActivity().getApplication())
                .refreshNotificationChannels();
            view.<TextView>findViewById(R.id.settings_saved).setText("Settings saved");
        });
        return view;
    }
}
