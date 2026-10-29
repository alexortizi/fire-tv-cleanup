package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Material-ish focus ring: soft fill tinted to the focused app, bright rim, faint outer glow.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float density;
    private float radius;
    private int tint = 0xFFFFFFFF;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(3f * density);
        glow.setStyle(Paint.Style.STROKE);
        applyTint();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    void setTint(int color) {
        tint = color == 0 ? 0xFFFFFFFF : color;
        applyTint();
        invalidate();
    }

    private void applyTint() {
        int c = tint | 0xFF000000;
        rim.setColor(0xCCFFFFFF);
        int top = Color.argb(0x55, Color.red(c), Color.green(c), Color.blue(c));
        int bot = Color.argb(0x22, Color.red(c), Color.green(c), Color.blue(c));
        fill.setShader(new LinearGradient(0, 0, 0, Math.max(1, getHeight()), top, bot, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, Math.max(1, getHeight()) * 0.4f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyTint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        int c = tint | 0xFF000000;
        for (int i = 1; i <= 3; i++) {
            glow.setStrokeWidth(i * 2.2f * density);
            glow.setColor(Color.argb(0x28 / i, Color.red(c), Color.green(c), Color.blue(c)));
            float o = i * density;
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o,
                    radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.4f, specular);
        canvas.restore();

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
