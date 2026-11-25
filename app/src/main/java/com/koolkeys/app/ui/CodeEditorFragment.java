package com.koolkeys.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import com.koolkeys.app.R;
import com.koolkeys.app.ui.widget.CodeEditorView;

public class CodeEditorFragment extends Fragment {
    public static final String EDITOR_TEXT = "koolkeys_editor_text";
    private static final String EDITOR_PREFS = "koolkeys_editor";
    private static String savedText = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_editor, container, false);
        CodeEditorView editor = view.findViewById(R.id.code_editor);
        savedText = requireContext().getSharedPreferences(EDITOR_PREFS, Context.MODE_PRIVATE)
            .getString(EDITOR_TEXT, savedText);
        editor.setText(savedText);
        editor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                savedText = s.toString();
                requireContext().getSharedPreferences(EDITOR_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(EDITOR_TEXT, savedText).apply();
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        TextView language = view.findViewById(R.id.language_label);
        language.setText("PLAIN TEXT");

        view.findViewById(R.id.paste_button).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    int start = editor.getSelectionStart();
                    int end = editor.getSelectionEnd();
                    if (start < 0) start = editor.getText().length();
                    if (end < 0) end = start;
                    editor.getText().replace(Math.min(start, end), Math.max(start, end), item.getText());
                }
            }
        });
        view.findViewById(R.id.clear_button).setOnClickListener(v -> editor.setText(""));
        view.findViewById(R.id.preview_button).setOnClickListener(v -> {
            savedText = editor.getText().toString();
            language.setText(editor.getText().length() + " characters");
            TextView preview = new TextView(requireContext());
            preview.setTypeface(Typeface.MONOSPACE);
            preview.setTextSize(13);
            preview.setTextColor(requireContext().getColor(R.color.text_primary));
            preview.setText(savedText);
            preview.setPadding(20, 16, 20, 16);
            preview.setHorizontallyScrolling(true);
            android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
            scroll.addView(preview);
            new AlertDialog.Builder(requireContext())
                .setTitle("Code preview — " + savedText.length() + " characters")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
        });
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        View view = getView();
        if (view != null) {
            CodeEditorView editor = view.findViewById(R.id.code_editor);
            savedText = editor.getText().toString();
        }
    }

    public static String getSavedText(Context context) {
        return context.getSharedPreferences(EDITOR_PREFS, Context.MODE_PRIVATE)
            .getString(EDITOR_TEXT, savedText);
    }
}
