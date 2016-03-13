package com.alexvasilkov.foldablelayout.shading;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;

public class GlanceFoldShading implements FoldShading {

    private static final int SHADOW_COLOR = Color.BLACK;
    private static final int SHADOW_MAX_ALPHA = 192;

    private final Paint solidShadow;

    private final Paint glancePaint;
    private final Bitmap glance;
    private final Rect glanceFrom;
    private final Rect glanceTo;

    public GlanceFoldShading(Bitmap glance) {
        solidShadow = new Paint();
        solidShadow.setColor(SHADOW_COLOR);

        this.glance = glance;
        glancePaint = new Paint();
        glancePaint.setDither(true);
        glancePaint.setFilterBitmap(true);
        glanceFrom = new Rect();
        glanceTo = new Rect();
    }

    /**
     * @deprecated Use {@link #GlanceFoldShading(Bitmap)} instead.
     */
    @SuppressWarnings({ "UnusedParameters", "unused" }) // Public API
    @Deprecated
    public GlanceFoldShading(Context context, Bitmap glance) {
        this(glance);
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

        boolean isDrawGlance = computeGlance(bounds, rotation, gravity);
        if (isDrawGlance) {
            canvas.drawBitmap(glance, glanceFrom, glanceTo, glancePaint);
        }
    }

    private float getShadowIntensity(float rotation, int gravity) {
        float intensity = 0f;
        if (gravity == Gravity.TOP) {
            if (rotation > -90f && rotation < 0f) { // (-90; 0) - Rotation is applied
                intensity = -rotation / 90f;
            }
        }
        return intensity;
    }

    private boolean computeGlance(Rect bounds, float rotation, int gravity) {
        if (gravity == Gravity.BOTTOM) {
            if (rotation > 0f && rotation < 90f) { // (0; 90) - Rotation is applied
                final float aspect = (float) glance.getWidth() / (float) bounds.width();

                // Computing glance offset
                final int distance = (int) (bounds.height() * ((rotation - 60f) / 15f));
                final int distanceOnGlance = (int) (distance * aspect);

                // Computing "to" bounds
                int scaledGlanceHeight = (int) (glance.getHeight() / aspect);
                glanceTo.set(bounds.left, bounds.top + distance,
                        bounds.right, bounds.top + distance + scaledGlanceHeight);

                if (!glanceTo.intersect(bounds)) {
                    // Glance is not visible
                    return false;
                }

                // Computing "from" bounds
                int scaledBoundsHeight = (int) (bounds.height() * aspect);
                glanceFrom.set(0, -distanceOnGlance, glance.getWidth(),
                        -distanceOnGlance + scaledBoundsHeight);

                return glanceFrom.intersect(0, 0, glance.getWidth(), glance.getHeight());
            }
        }

        return false;
    }

}
