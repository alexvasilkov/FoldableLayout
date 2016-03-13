package com.alexvasilkov.foldablelayout.shading;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;

public class SimpleFoldShading implements FoldShading {

    private static final int SHADOW_COLOR = Color.BLACK;
    private static final int SHADOW_MAX_ALPHA = 192;

    private final Paint solidShadow;

    public SimpleFoldShading() {
        solidShadow = new Paint();
        solidShadow.setColor(SHADOW_COLOR);
    }

    @Override
    public void onPreDraw(Canvas canvas, Rect bounds, float rotation, int gravity) {
        // No-op
    }

    @Override
    public void onPostDraw(Canvas canvas, Rect bounds, float rotation, int gravity) {
        float intensity = getShadowIntensity(rotation, gravity);
        if (intensity > 0f) {
            int alpha = (int) (SHADOW_MAX_ALPHA * intensity);
            solidShadow.setAlpha(alpha);
            canvas.drawRect(bounds, solidShadow);
        }
    }

    private float getShadowIntensity(float rotation, int gravity) {
        float intensity = 0f;
        if (gravity == Gravity.TOP) {
            if (rotation > -90f && rotation < 0f) { // (-90; 0) - Rotation is applied
                intensity = -rotation / 90f;
            }
        } else {
            if (rotation > 0f && rotation < 90f) { // (0; 90) - Rotation is applied
                intensity = rotation / 90f;
            }
        }
        return intensity;
    }

}
