package com.koolkeys.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.koolkeys.app.R;
import com.koolkeys.app.bluetooth.BluetoothManager;

public class TouchpadView extends View {
    private float lastX;
    private float lastY;
    private BluetoothManager bluetoothManager;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchpadView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(context.getColor(R.color.surface_highlight));
        paint.setStyle(Paint.Style.FILL);
    }

    public void setBluetoothManager(BluetoothManager manager) {
        bluetoothManager = manager;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 28, 28, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (bluetoothManager != null) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    bluetoothManager.mouseMove(Math.round(dx * 1.2f), Math.round(dy * 1.2f));
                }
                lastX = event.getX();
                lastY = event.getY();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
