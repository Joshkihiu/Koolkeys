package com.koolkeys.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;

import com.koolkeys.app.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorView extends AppCompatEditText {
    private final Paint gutterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int GUTTER_WIDTH = 44;

    public CodeEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(14);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setHorizontallyScrolling(true);
        setGravity(android.view.Gravity.TOP);
        setTextColor(context.getColor(R.color.text_primary));
        setBackgroundColor(context.getColor(R.color.surface_elevated));
        setPadding(56, 16, 16, 16);
        gutterPaint.setTextAlign(Paint.Align.RIGHT);
        gutterPaint.setTypeface(Typeface.MONOSPACE);
        gutterPaint.setTextSize(28);
        gutterPaint.setColor(context.getColor(R.color.editor_line));
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { highlight(); invalidate(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void highlight() {
        Editable text = getText();
        if (text == null) return;
        for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class)) {
            text.removeSpan(span);
        }
        highlightPattern(text, Pattern.compile("\\b(class|def|public|private|protected|static|void|int|String|boolean|return|for|while|if|else|import|from|in|try|catch|package|new|this|true|false|null)\\b"),
            getContext().getColor(R.color.accent));
        highlightPattern(text, Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"),
            getContext().getColor(R.color.success));
        highlightPattern(text, Pattern.compile("//.*|#.*|/\\*.*?\\*/"),
            getContext().getColor(R.color.editor_line));
        highlightPattern(text, Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b"),
            getContext().getColor(R.color.warning));
    }

    private void highlightPattern(Editable text, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            text.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int lineHeight = getLineHeight();
        int firstLine = getLayout() == null ? 0 : getLayout().getLineForVertical(getScrollY());
        int lastLine = getLayout() == null ? 0 : getLayout().getLineForVertical(getScrollY() + getHeight());
        for (int line = firstLine; line <= lastLine; line++) {
            int baseline = getPaddingTop() + (line + 1) * lineHeight - getScrollY();
            canvas.drawText(String.valueOf(line + 1), GUTTER_WIDTH, baseline - 4, gutterPaint);
        }
        super.onDraw(canvas);
    }
}
