package com.alexvasilkov.foldablelayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.alexvasilkov.foldablelayout.shading.FoldShading;

/**
 * Provides basic functionality for fold animation: splitting view into 2 parts,
 * synchronous rotation of both parts and so on.
 */
class FoldableItemLayout extends FrameLayout {

    private static final int CAMERA_DISTANCE = 48;
    private static final float CAMERA_DISTANCE_MAGIC_FACTOR = 8f / CAMERA_DISTANCE;

    private boolean isAutoScaleEnabled;

    private final BaseLayout baseLayout;
    private final PartView topPart;
    private final PartView bottomPart;

    private int width;
    private int height;
    private Bitmap cacheBitmap;

    private boolean isInTransformation;

    private float foldRotation;
    private float scale = 1f;
    private float scaleFactor = 1f;
    private float scaleFactorY = 1f;

    FoldableItemLayout(Context context) {
        super(context);

        baseLayout = new BaseLayout(this);

        topPart = new PartView(this, Gravity.TOP);
        bottomPart = new PartView(this, Gravity.BOTTOM);

        setInTransformation(false);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return !isInTransformation && super.dispatchTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (foldRotation != 0f) {
            ensureCacheBitmap();
        }

        super.dispatchDraw(canvas);
    }

    private void ensureCacheBitmap() {
        width = getWidth();
        height = getHeight();

        // Check if correct cache bitmap is already created
        if (cacheBitmap != null && cacheBitmap.getWidth() == width
                && cacheBitmap.getHeight() == height) {
            return;
        }

        if (cacheBitmap != null) {
            cacheBitmap.recycle();
            cacheBitmap = null;
        }

        if (width != 0 && height != 0) {
            try {
                cacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError outOfMemoryError) {
                cacheBitmap = null;
            }
        }

        applyCacheBitmap(cacheBitmap);
    }

    private void applyCacheBitmap(Bitmap bitmap) {
        baseLayout.setCacheCanvas(bitmap == null ? null : new Canvas(bitmap));
        topPart.setCacheBitmap(bitmap);
        bottomPart.setCacheBitmap(bitmap);
    }

    /**
     * Fold rotation value in degrees.
     */
    public void setFoldRotation(float rotation) {
        foldRotation = rotation;

        topPart.applyFoldRotation(rotation);
        bottomPart.applyFoldRotation(rotation);

        setInTransformation(rotation != 0f);

        scaleFactor = 1f;

        if (isAutoScaleEnabled && width > 0) {
            double sin = Math.abs(Math.sin(Math.toRadians(rotation)));
            float dw = (float) (height * sin) * CAMERA_DISTANCE_MAGIC_FACTOR;
            scaleFactor = width / (width + dw);

            setScale(scale);
        }
    }

    public void setScale(float scale) {
        this.scale = scale;

        final float scaleX = scale * scaleFactor;
        final float scaleY = scale * scaleFactor * scaleFactorY;

        baseLayout.setScaleY(scaleFactorY);
        topPart.setScaleX(scaleX);
        topPart.setScaleY(scaleY);
        bottomPart.setScaleX(scaleX);
        bottomPart.setScaleY(scaleY);
    }

    public void setScaleFactorY(float scaleFactorY) {
        this.scaleFactorY = scaleFactorY;
        setScale(scale);
    }

    /**
     * Translation preserving middle line splitting.
     */
    public void setRollingDistance(float distance) {
        final float scaleY = scale * scaleFactor * scaleFactorY;
        topPart.applyRollingDistance(distance, scaleY);
        bottomPart.applyRollingDistance(distance, scaleY);
    }

    private void setInTransformation(boolean isInTransformation) {
        if (this.isInTransformation == isInTransformation) {
            return;
        }
        this.isInTransformation = isInTransformation;

        baseLayout.setDrawToCache(isInTransformation);
        topPart.setVisibility(isInTransformation ? VISIBLE : INVISIBLE);
        bottomPart.setVisibility(isInTransformation ? VISIBLE : INVISIBLE);
    }

    public void setAutoScaleEnabled(boolean isAutoScaleEnabled) {
        this.isAutoScaleEnabled = isAutoScaleEnabled;
    }

    public FrameLayout getBaseLayout() {
        return baseLayout;
    }

    public void setLayoutVisibleBounds(Rect visibleBounds) {
        topPart.setVisibleBounds(visibleBounds);
        bottomPart.setVisibleBounds(visibleBounds);
    }

    public void setFoldShading(FoldShading shading) {
        topPart.setFoldShading(shading);
        bottomPart.setFoldShading(shading);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Helping GC to faster clean up bitmap memory.
        // See issue #10: https://github.com/alexvasilkov/FoldableLayout/issues/10.
        if (cacheBitmap != null) {
            cacheBitmap.recycle();
            applyCacheBitmap(cacheBitmap = null);
        }
    }

    /**
     * View holder layout that can draw itself into given canvas.
     */
    @SuppressLint("ViewConstructor")
    private static class BaseLayout extends FrameLayout {

        private Canvas cacheCanvas;
        private boolean isDrawToCache;

        BaseLayout(FoldableItemLayout layout) {
            super(layout.getContext());

            final int matchParent = ViewGroup.LayoutParams.MATCH_PARENT;
            LayoutParams params = new LayoutParams(matchParent, matchParent);
            layout.addView(this, params);
            setWillNotDraw(false);
        }

        @Override
        public void draw(Canvas canvas) {
            if (isDrawToCache) {
                if (cacheCanvas != null) {
                    cacheCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    super.draw(cacheCanvas);
                }
            } else {
                super.draw(canvas);
            }
        }

        void setCacheCanvas(Canvas cacheCanvas) {
            this.cacheCanvas = cacheCanvas;
        }

        void setDrawToCache(boolean drawToCache) {
            if (isDrawToCache != drawToCache) {
                isDrawToCache = drawToCache;
                invalidate();
            }
        }

    }

    /**
     * Splat part view. It will draw top or bottom part of cached bitmap and overlay shadows.
     * Also it contains main logic for all transformations (fold rotation, scale, "rolling
     * distance").
     */
    @SuppressLint("ViewConstructor")
    private static class PartView extends View {

        private final int gravity;

        private Bitmap bitmap;
        private final Rect bitmapBounds = new Rect();

        private float clippingFactor = 0.5f;

        private final Paint bitmapPaint;

        private Rect visibleBounds;

        private int intVisibility;
        private int extVisibility;

        private float localFoldRotation;
        private FoldShading shading;

        PartView(FoldableItemLayout parent, int gravity) {
            super(parent.getContext());
            this.gravity = gravity;

            final int matchParent = LayoutParams.MATCH_PARENT;
            parent.addView(this, new LayoutParams(matchParent, matchParent));
            setCameraDistance(CAMERA_DISTANCE * getResources().getDisplayMetrics().densityDpi);

            bitmapPaint = new Paint();
            bitmapPaint.setDither(true);
            bitmapPaint.setFilterBitmap(true);

            setWillNotDraw(false);
        }

        void setCacheBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            calculateBitmapBounds();
        }

        void setVisibleBounds(Rect visibleBounds) {
            this.visibleBounds = visibleBounds;
            calculateBitmapBounds();
        }

        void setFoldShading(FoldShading shading) {
            this.shading = shading;
        }

        private void calculateBitmapBounds() {
            if (bitmap == null) {
                bitmapBounds.set(0, 0, 0, 0);
            } else {
                int bh = bitmap.getHeight();
                int bw = bitmap.getWidth();

                int top = gravity == Gravity.TOP ? 0 : (int) (bh * (1f - clippingFactor) - 0.5f);
                int bottom = gravity == Gravity.TOP ? (int) (bh * clippingFactor + 0.5f) : bh;

                bitmapBounds.set(0, top, bw, bottom);
                if (visibleBounds != null) {
                    if (!bitmapBounds.intersect(visibleBounds)) {
                        bitmapBounds.set(0, 0, 0, 0); // No intersection
                    }
                }
            }

            invalidate();
        }

        void applyFoldRotation(float rotation) {
            float position = rotation;
            while (position < 0f) {
                position += 360f;
            }
            position %= 360f;
            if (position > 180f) {
                position -= 360f; // Now position is within (-180; 180]
            }

            float rotationX = 0f;
            boolean isVisible = true;

            if (gravity == Gravity.TOP) {
                if (position <= -90f || position == 180f) { // (-180; -90] || {180} - Will not show
                    isVisible = false;
                } else if (position < 0f) { // (-90; 0) - Applying rotation
                    rotationX = position;
                }
                // [0; 180) - Holding still
            } else {
                if (position >= 90f) { // [90; 180] - Will not show
                    isVisible = false;
                } else if (position > 0f) { // (0; 90) - Applying rotation
                    rotationX = position;
                }
                // (-180; 0] - Holding still
            }

            setRotationX(rotationX);

            intVisibility = isVisible ? VISIBLE : INVISIBLE;
            applyVisibility();

            localFoldRotation = position;

            invalidate(); // Needed to draw shadow overlay
        }

        void applyRollingDistance(float distance, float scaleY) {
            // Applying translation
            setTranslationY((int) (distance * scaleY + 0.5f));

            // Computing clipping for top view (bottom clipping will be 1 - topClipping)
            final int h = getHeight() / 2;
            final float topClipping = h == 0 ? 0.5f : 0.5f * (h - distance) / h;

            clippingFactor = gravity == Gravity.TOP ? topClipping : 1f - topClipping;

            calculateBitmapBounds();
        }

        @Override
        public void setVisibility(int visibility) {
            extVisibility = visibility;
            applyVisibility();
        }

        @SuppressLint("WrongConstant")
        private void applyVisibility() {
            super.setVisibility(extVisibility == VISIBLE ? intVisibility : extVisibility);
        }

        @SuppressLint("MissingSuperCall")
        @Override
        public void draw(Canvas canvas) {
            if (shading != null) {
                shading.onPreDraw(canvas, bitmapBounds, localFoldRotation, gravity);
            }
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, bitmapBounds, bitmapBounds, bitmapPaint);
            }
            if (shading != null) {
                shading.onPostDraw(canvas, bitmapBounds, localFoldRotation, gravity);
            }
        }

    }

}
