package com.koolkeys.app.ui;

import android.content.Context;
import android.content.Intent;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.koolkeys.app.KoolKeysApplication;
import com.koolkeys.app.R;
import com.koolkeys.app.session.SessionManager;
import com.koolkeys.app.service.InsertionForegroundService;
import com.koolkeys.app.session.SessionProgressTracker;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class InsertionFragment extends Fragment {
    private SessionManager session;
    private TextView status;
    private TextView progressText;
    private TextView currentLine;
    private TextView currentCharacter;
    private TextView currentInputWord;
    private TextView localInputMonitor;
    private TextView paceText;
    private TextView timeRemaining;
    private TextView durationReference;
    private TextView diagnosticsText;
    private ProgressBar progress;
    private Button startButton;
    private Button pauseButton;
    private Button resumeButton;
    private Button stopButton;
    private Spinner speedSpinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_insertion, container, false);
        status = view.findViewById(R.id.insertion_status);
        progressText = view.findViewById(R.id.progress_text);
        currentLine = view.findViewById(R.id.current_line);
        currentCharacter = view.findViewById(R.id.current_character);
        currentInputWord = view.findViewById(R.id.current_input_word);
        localInputMonitor = view.findViewById(R.id.local_input_monitor);
        paceText = view.findViewById(R.id.insertion_pace);
        timeRemaining = view.findViewById(R.id.time_remaining);
        durationReference = view.findViewById(R.id.duration_reference);
        diagnosticsText = view.findViewById(R.id.insertion_diagnostics);
        progress = view.findViewById(R.id.insertion_progress);
        startButton = view.findViewById(R.id.start_button);
        pauseButton = view.findViewById(R.id.pause_button);
        resumeButton = view.findViewById(R.id.resume_button);
        stopButton = view.findViewById(R.id.stop_button);

        Spinner duration = view.findViewById(R.id.duration_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
            R.array.duration_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        duration.setAdapter(adapter);
        duration.setSelection(requireContext().getSharedPreferences("koolkeys_settings", Context.MODE_PRIVATE)
            .getInt("default_duration_index", 2));
        speedSpinner = view.findViewById(R.id.insertion_speed_spinner);
        ArrayAdapter<CharSequence> speedAdapter = ArrayAdapter.createFromResource(requireContext(),
            R.array.insertion_speed_options, android.R.layout.simple_spinner_item);
        speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speedSpinner.setAdapter(speedAdapter);
        speedSpinner.setSelection(2);
        speedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View item, int position, long id) {
                updatePaceLabel();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        SwitchMaterial extendedMode = view.findViewById(R.id.extended_mode);
        extendedMode.setOnCheckedChangeListener((button, checked) -> duration.setEnabled(checked));
        duration.setEnabled(false);

        KoolKeysApplication application = (KoolKeysApplication) requireActivity().getApplication();
        session = application.getSessionManager();
        session.setListener(new SessionManager.Listener() {
            @Override public void onProgress(int completed, int total, int line,
                                             long elapsedMillis, long remainingMillis,
                                             SessionProgressTracker.Snapshot snapshot) {
                progress.setMax(Math.max(total, 1));
                progress.setProgress(Math.max(0, Math.min(completed, total)));
                progressText.setText(getString(R.string.insertion_progress, completed, total));
                String source = session.getText();
                if (source.isEmpty()) source = CodeEditorFragment.getSavedText(requireContext());
                currentLine.setText(getString(R.string.insertion_line,
                    line, countLines(source)));
                updateNextSourceCharacter(source, completed, snapshot);
                updateCurrentInputWord(source, snapshot);
                updateLocalInputMonitor(source, snapshot);
                timeRemaining.setText(getString(R.string.insertion_time_remaining,
                    formatDuration(elapsedMillis), formatDuration(remainingMillis)));
                long targetDuration = session.getTargetDurationMillis();
                durationReference.setText(targetDuration > 0
                    ? getString(R.string.insertion_reference_duration, formatDuration(targetDuration))
                    : getString(R.string.insertion_no_reference_duration));
                paceText.setText(getString(R.string.insertion_pace,
                    session.isRunning()
                        ? session.getWordsPerMinute()
                        : parseWordsPerMinute((String) speedSpinner.getSelectedItem())));
                updateDiagnostics(snapshot);
                updateSessionControls();
            }

            @Override public void onStateChanged(String state) {
                status.setText(state);
                updateSessionControls();
            }

            @Override public void onComplete() {
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, R.string.insertion_finished_toast, Toast.LENGTH_LONG).show();
                }
            }
        });

        startButton.setOnClickListener(v ->
            showPreviewAndStart(duration, extendedMode, speedSpinner));
        pauseButton.setOnClickListener(v -> {
            session.pause();
            if (session.isPaused()) startNotificationService("Code insertion paused");
        });
        resumeButton.setOnClickListener(v -> {
            session.resume();
            if (session.isRunning()) startNotificationService("Automated code insertion running");
        });
        stopButton.setOnClickListener(v -> session.stop());
        return view;
    }

    @Override
    public void onDestroyView() {
        session.setListener(null);
        status = null;
        progressText = null;
        currentLine = null;
        currentCharacter = null;
        currentInputWord = null;
        localInputMonitor = null;
        paceText = null;
        timeRemaining = null;
        durationReference = null;
        diagnosticsText = null;
        progress = null;
        startButton = null;
        pauseButton = null;
        resumeButton = null;
        stopButton = null;
        speedSpinner = null;
        super.onDestroyView();
    }

    private long parseMinutes(String label) {
        return Long.parseLong(label.substring(0, label.indexOf(' ')));
    }

    private int parseWordsPerMinute(String label) {
        return Integer.parseInt(label.substring(0, label.indexOf(' ')));
    }

    private void showPreviewAndStart(
        Spinner duration, SwitchMaterial extendedMode, Spinner speedSpinner) {
        String text = CodeEditorFragment.getSavedText(requireContext());
        if (text.isEmpty()) {
            status.setText("No code to insert");
            return;
        }
        int wordsPerMinute = parseWordsPerMinute((String) speedSpinner.getSelectedItem());
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.insertion_start_confirmation_title)
            .setMessage(getString(R.string.insertion_start_confirmation_message,
                text.length(), countLines(text), wordsPerMinute))
            .setNegativeButton("Cancel", null)
            .setPositiveButton(R.string.insertion_start_button, (dialog, which) -> {
                boolean extended = extendedMode.isChecked();
                long durationMillis = extended
                    ? TimeUnit.MINUTES.toMillis(parseMinutes((String) duration.getSelectedItem()))
                    : 0;
                long reviewInterval = readReviewInterval(requireContext());
                session.configure(text, durationMillis, reviewInterval, extended, wordsPerMinute);
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

    private int countLines(String text) {
        int lines = 1;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\r') {
                lines++;
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
            } else if (character == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private void updateNextSourceCharacter(
        String source, int completed, SessionProgressTracker.Snapshot snapshot) {
        int inFlightAction = snapshot.getInFlightActionIndex();
        if (inFlightAction >= 0) {
            String action = snapshot.getInFlightActionName();
            int sourceStart = snapshot.getInFlightSourceStart();
            if ("CHARACTER".equals(action) && sourceStart < source.length()) {
                char character = source.charAt(sourceStart);
                currentCharacter.setText(getString(R.string.insertion_current_character,
                    Character.toString(character), (int) character));
            } else if ("ESCAPE".equals(action)) {
                currentCharacter.setText(R.string.insertion_current_escape);
            } else if ("ENTER".equals(action)) {
                currentCharacter.setText(R.string.insertion_current_enter);
            } else if ("TAB".equals(action)) {
                currentCharacter.setText(R.string.insertion_current_tab);
            } else if ("BACKSPACE".equals(action)) {
                currentCharacter.setText(R.string.insertion_current_backspace);
            }
            return;
        }
        if (completed >= source.length()) {
            currentCharacter.setText(R.string.insertion_no_next_source);
            return;
        }
        char next = source.charAt(completed);
        if (next == '\r' || next == '\n') {
            currentCharacter.setText(R.string.insertion_next_enter);
        } else if (next == '\t') {
            currentCharacter.setText(R.string.insertion_next_tab);
        } else if (next == '\b') {
            currentCharacter.setText(R.string.insertion_next_backspace);
        } else {
            currentCharacter.setText(getString(R.string.insertion_next_character,
                Character.toString(next), (int) next));
        }
    }

    private void updateCurrentInputWord(String source, SessionProgressTracker.Snapshot snapshot) {
        int position = snapshot.getInFlightActionIndex() >= 0
            ? snapshot.getInFlightSourceStart()
            : snapshot.getSourcePosition();
        String action = snapshot.getInFlightActionName();
        String item;
        if ("ENTER".equals(action) || "ESCAPE".equals(action)) {
            item = getString(R.string.insertion_word_enter);
        } else if ("TAB".equals(action)) {
            item = getString(R.string.insertion_word_tab);
        } else if ("BACKSPACE".equals(action)) {
            item = getString(R.string.insertion_word_backspace);
        } else {
            item = getCurrentWord(source, position);
        }
        currentInputWord.setText(getString(R.string.insertion_current_word, item));
    }

    private String getCurrentWord(String source, int position) {
        if (position >= source.length()) return getString(R.string.insertion_no_pending_word);
        char character = source.charAt(position);
        if (character == '\r' || character == '\n') return getString(R.string.insertion_word_enter);
        if (character == '\t') return getString(R.string.insertion_word_tab);
        if (character == '\b') return getString(R.string.insertion_word_backspace);
        if (Character.isWhitespace(character)) return getString(R.string.insertion_word_space);

        int start = position;
        while (start > 0 && !Character.isWhitespace(source.charAt(start - 1))) start--;
        int end = position + 1;
        while (end < source.length() && !Character.isWhitespace(source.charAt(end))) end++;
        if (end - start > 32) {
            end = start + 32;
            if (Character.isHighSurrogate(source.charAt(end - 1))) end--;
            return source.substring(start, end) + "...";
        }
        return source.substring(start, end);
    }

    private void updateLocalInputMonitor(
        String source, SessionProgressTracker.Snapshot snapshot) {
        int position = Math.max(0, Math.min(snapshot.getSourcePosition(), source.length()));
        int start = Math.max(0, position - 32);
        int end = Math.min(source.length(), position + 32);
        SpannableStringBuilder monitor = new SpannableStringBuilder();
        monitor.append(source.substring(start, position));
        int acceptedEnd = monitor.length();
        monitor.append('|');
        int pendingStart = monitor.length();
        monitor.append(source.substring(position, end));

        if (acceptedEnd > 0) {
            monitor.setSpan(new ForegroundColorSpan(
                requireContext().getColor(R.color.success)), 0, acceptedEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (pendingStart < monitor.length()) {
            monitor.setSpan(new ForegroundColorSpan(
                requireContext().getColor(R.color.text_muted)), pendingStart, monitor.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (snapshot.getInFlightActionIndex() >= 0) {
            int currentStart = Math.max(position,
                Math.min(snapshot.getInFlightSourceStart(), source.length()));
            int currentEnd = Math.max(currentStart,
                Math.min(snapshot.getInFlightSourceEnd(), end));
            int spanStart = pendingStart + currentStart - position;
            int spanEnd = pendingStart + currentEnd - position;
            if (spanEnd > spanStart) {
                monitor.setSpan(new BackgroundColorSpan(
                    requireContext().getColor(R.color.warning)), spanStart, spanEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        monitorTextColor(monitor, acceptedEnd);
        localInputMonitor.setText(monitor);
    }

    private void monitorTextColor(SpannableStringBuilder monitor, int acceptedEnd) {
        monitor.setSpan(new ForegroundColorSpan(
            requireContext().getColor(R.color.warning)), acceptedEnd, acceptedEnd + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void updatePaceLabel() {
        if (paceText == null || speedSpinner == null || speedSpinner.getSelectedItem() == null) return;
        paceText.setText(getString(R.string.insertion_pace,
            parseWordsPerMinute((String) speedSpinner.getSelectedItem())));
    }

    private void updateDiagnostics(SessionProgressTracker.Snapshot snapshot) {
        String currentAction = snapshot.getInFlightActionIndex() < 0
            ? getString(R.string.insertion_no_current_action)
            : snapshot.getInFlightActionName() + " #" + (snapshot.getInFlightActionIndex() + 1);
        String callbackGap = snapshot.getBluetoothCallbackCount() < 2
            ? getString(R.string.insertion_not_measured)
            : formatNanos(snapshot.getLastBluetoothCallbackGapNanos());
        String actionStartGap = formatOptionalNanos(snapshot.getLastActionStartGapNanos());
        String actionLast = snapshot.getSubmittedActions() == 0
            ? getString(R.string.insertion_not_measured)
            : formatNanos(snapshot.getLastActionNanos());
        String actionAverage = snapshot.getSubmittedActions() == 0
            ? getString(R.string.insertion_not_measured)
            : formatNanos(snapshot.getAverageActionNanos());
        String actionMaximum = snapshot.getSubmittedActions() == 0
            ? getString(R.string.insertion_not_measured)
            : formatNanos(snapshot.getMaxActionNanos());
        String stateGap = formatOptionalNanos(snapshot.getLastStateChangeGapNanos());
        String pauseResponse = formatOptionalNanos(snapshot.getLastPauseResponseNanos());
        String resumeResponse = formatOptionalNanos(snapshot.getLastResumeResponseNanos());
        String cancelResponse = formatOptionalNanos(snapshot.getLastCancellationResponseNanos());
        String sessionDuration = snapshot.getSessionDurationNanos() == 0
            ? getString(R.string.insertion_not_measured)
            : formatNanos(snapshot.getSessionDurationNanos());

        diagnosticsText.setText(getString(R.string.insertion_diagnostics,
            snapshot.getSubmittedActions(), snapshot.getTotalActions(),
            snapshot.getUnsubmittedActions(), currentAction, actionLast, actionAverage,
            actionMaximum, stateGap, callbackGap, pauseResponse, resumeResponse,
            cancelResponse, sessionDuration, formatRecentActionTimings(snapshot), actionStartGap));
    }

    private String formatRecentActionTimings(SessionProgressTracker.Snapshot snapshot) {
        List<SessionProgressTracker.ActionTiming> timings = snapshot.getRecentActionTimings();
        if (timings.isEmpty()) return getString(R.string.insertion_not_measured);
        StringBuilder formatted = new StringBuilder();
        int first = Math.max(0, timings.size() - 5);
        for (int index = first; index < timings.size(); index++) {
            if (formatted.length() > 0) formatted.append(", ");
            SessionProgressTracker.ActionTiming timing = timings.get(index);
            formatted.append('#').append(timing.getActionIndex() + 1).append('=')
                .append(formatNanos(timing.getDurationNanos()));
        }
        return formatted.toString();
    }

    private String formatOptionalNanos(long nanos) {
        return nanos == 0
            ? getString(R.string.insertion_not_measured)
            : formatNanos(nanos);
    }

    private String formatNanos(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    private void updateSessionControls() {
        if (session == null || startButton == null) return;
        boolean running = session.isRunning();
        boolean paused = session.isPaused();
        startButton.setEnabled(!running);
        speedSpinner.setEnabled(!running);
        pauseButton.setEnabled(running && !paused);
        resumeButton.setEnabled(running && paused);
        stopButton.setEnabled(running);
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
