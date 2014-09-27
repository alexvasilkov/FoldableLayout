package com.alexvasilkov.foldablelayout.shading;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;

public class SimpleFoldShading implements FoldShading {

    private static final int SHADOW_COLOR = Color.BLACK;
    private static final int SHADOW_MAX_ALPHA = 192;

    private final Paint mSolidShadow;

    public SimpleFoldShading() {
        mSolidShadow = new Paint();
        mSolidShadow.setColor(SHADOW_COLOR);
    }

    @Override
    public void onPreDraw(Canvas canvas, Rect bounds, float rotation, int gravity) {
        // NO-OP
    }

    @Override
    public void onPostDraw(Canvas canvas, Rect bounds, float rotation, int gravity) {
        float intensity = getShadowIntensity(rotation, gravity);
        if (intensity > 0) {
            int alpha = (int) (SHADOW_MAX_ALPHA * intensity);
            mSolidShadow.setAlpha(alpha);
            canvas.drawRect(bounds, mSolidShadow);
        }
    }

    private float getShadowIntensity(float rotation, int gravity) {
        float intensity = 0;
        if (gravity == Gravity.TOP) {
            if (rotation > -90 && rotation < 0) { // (-90; 0) - rotation is applied
                intensity = -rotation / 90f;
            }
        } else {
            if (rotation > 0 && rotation < 90) { // (0; 90) - rotation is applied
                intensity = rotation / 90f;
            }
        }
        return intensity;
    }

}
