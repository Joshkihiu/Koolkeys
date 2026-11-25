package com.koolkeys.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.koolkeys.app.KoolKeysApplication;
import com.koolkeys.app.R;
import com.koolkeys.app.session.SessionManager;
import com.koolkeys.app.service.InsertionForegroundService;

import java.util.concurrent.TimeUnit;
import java.util.Locale;

public class InsertionFragment extends Fragment {
    private SessionManager session;
    private TextView status;
    private TextView progressText;
    private TextView currentLine;
    private TextView timeRemaining;
    private ProgressBar progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_insertion, container, false);
        status = view.findViewById(R.id.insertion_status);
        progressText = view.findViewById(R.id.progress_text);
        currentLine = view.findViewById(R.id.current_line);
        timeRemaining = view.findViewById(R.id.time_remaining);
        progress = view.findViewById(R.id.insertion_progress);

        Spinner duration = view.findViewById(R.id.duration_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
            R.array.duration_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        duration.setAdapter(adapter);
        duration.setSelection(requireContext().getSharedPreferences("koolkeys_settings", Context.MODE_PRIVATE)
            .getInt("default_duration_index", 2));
        SwitchMaterial extendedMode = view.findViewById(R.id.extended_mode);
        extendedMode.setOnCheckedChangeListener((button, checked) -> duration.setEnabled(checked));
        duration.setEnabled(false);

        KoolKeysApplication application = (KoolKeysApplication) requireActivity().getApplication();
        session = application.getSessionManager();
        session.setListener(new SessionManager.Listener() {
            @Override public void onProgress(int completed, int total, int line,
                                             long elapsedMillis, long durationMillis) {
                progress.setMax(Math.max(total, 1));
                progress.setProgress(completed);
                progressText.setText(completed + " / " + total + " characters");
                currentLine.setText("Current line: " + line);
                long remaining = Math.max(0, durationMillis - elapsedMillis);
                timeRemaining.setText("Elapsed " + formatDuration(elapsedMillis)
                    + "  ·  Remaining " + formatDuration(remaining));
            }

            @Override public void onStateChanged(String state) {
                status.setText(state);
            }

        });

        view.findViewById(R.id.start_button).setOnClickListener(v ->
            showPreviewAndStart(duration, extendedMode));
        view.findViewById(R.id.pause_button).setOnClickListener(v -> {
            session.pause();
            if (session.isPaused()) startNotificationService("Code insertion paused");
        });
        view.findViewById(R.id.resume_button).setOnClickListener(v -> {
            session.resume();
            if (session.isRunning()) startNotificationService("Automated code insertion running");
        });
        view.findViewById(R.id.stop_button).setOnClickListener(v -> {
            session.stop();
        });
        return view;
    }

    @Override
    public void onDestroyView() {
        session.setListener(null);
        status = null;
        progressText = null;
        currentLine = null;
        timeRemaining = null;
        progress = null;
        super.onDestroyView();
    }

    private long parseMinutes(String label) {
        return Long.parseLong(label.substring(0, label.indexOf(' ')));
    }

    private void showPreviewAndStart(Spinner duration, SwitchMaterial extendedMode) {
        String text = CodeEditorFragment.getSavedText(requireContext());
        if (text.isEmpty()) {
            status.setText("No code to insert");
            return;
        }
        TextView preview = new TextView(requireContext());
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextSize(13);
        preview.setTextColor(requireContext().getColor(R.color.text_primary));
        preview.setText(text);
        preview.setPadding(20, 16, 20, 16);
        preview.setHorizontallyScrolling(true);
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        scroll.addView(preview);
        new AlertDialog.Builder(requireContext())
            .setTitle("Review complete code — " + text.length() + " characters")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start automated insertion", (dialog, which) -> {
                boolean extended = extendedMode.isChecked();
                long durationMillis = extended
                    ? TimeUnit.MINUTES.toMillis(parseMinutes((String) duration.getSelectedItem()))
                    : Math.max(40, countActions(text) * 40L);
                long reviewInterval = readReviewInterval(requireContext());
                session.configure(text, durationMillis, reviewInterval, extended);
                session.start();
                if (session.isRunning()) startNotificationService("Automated code insertion running");
            })
            .show();
    }

    private long readReviewInterval(Context context) {
        int index = context.getSharedPreferences("koolkeys_settings", Context.MODE_PRIVATE)
            .getInt("review_index", 1);
        long[] intervals = {0, 5, 10, 15, 30};
        return index >= 0 && index < intervals.length
            ? TimeUnit.MINUTES.toMillis(intervals[index])
            : TimeUnit.MINUTES.toMillis(5);
    }

    private String formatDuration(long millis) {
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private int countActions(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') i++;
            count++;
        }
        return count;
    }

    private void startNotificationService(String message) {
        Intent intent = new Intent(requireContext(), InsertionForegroundService.class);
        intent.putExtra(InsertionForegroundService.EXTRA_MESSAGE, message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
    }

    private void stopNotificationService() {
        Intent intent = new Intent(requireContext(), InsertionForegroundService.class);
        requireContext().stopService(intent);
    }
}
