package com.alexvasilkov.foldablelayout;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.alexvasilkov.foldablelayout.shading.FoldShading;

/**
 * Provides basic functionality for fold animation: splitting view into 2 parts,
 * synchronous rotation of both parts and so on.
 */
public class FoldableItemLayout extends FrameLayout {

    private static final int CAMERA_DISTANCE = 48;
    private static final float CAMERA_DISTANCE_MAGIC_FACTOR = 8f / CAMERA_DISTANCE;

    private boolean mIsAutoScaleEnabled;

    private final BaseLayout mBaseLayout;
    private final PartView mTopPart, mBottomPart;

    private int mWidth, mHeight;
    private Bitmap mCacheBitmap;

    private boolean mIsInTransformation;

    private float mFoldRotation;
    private float mScale;
    private float mRollingDistance;

    public FoldableItemLayout(Context context) {
        super(context);

        mBaseLayout = new BaseLayout(this);

        mTopPart = new PartView(this, Gravity.TOP);
        mBottomPart = new PartView(this, Gravity.BOTTOM);

        setInTransformation(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBaseLayout.moveInflatedChildren(this, 3); // skipping mBaseLayout & mTopPart & mBottomPart views
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mFoldRotation != 0) {
            ensureCacheBitmap();
        }

        super.dispatchDraw(canvas);
    }

    private void ensureCacheBitmap() {
        mWidth = getWidth();
        mHeight = getHeight();

        // Check if correct cache bitmap is already created
        if (mCacheBitmap != null && mCacheBitmap.getWidth() == mWidth
                && mCacheBitmap.getHeight() == mHeight) return;

        if (mCacheBitmap != null) {
            mCacheBitmap.recycle();
            mCacheBitmap = null;
        }

        if (mWidth != 0 && mHeight != 0) {
            try {
                mCacheBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError outOfMemoryError) {
                mCacheBitmap = null;
            }
        }

        applyCacheBitmap(mCacheBitmap);
    }

    private void applyCacheBitmap(Bitmap bitmap) {
        mBaseLayout.setCacheCanvas(bitmap == null ? null : new Canvas(bitmap));
        mTopPart.setCacheBitmap(bitmap);
        mBottomPart.setCacheBitmap(bitmap);
    }

    /**
     * Fold rotation value in degrees
     */
    public void setFoldRotation(float rotation) {
        mFoldRotation = rotation;

        mTopPart.applyFoldRotation(rotation);
        mBottomPart.applyFoldRotation(rotation);

        setInTransformation(rotation != 0);

        if (mIsAutoScaleEnabled) {
            float viewScale = 1.0f;
            if (mWidth > 0) {
                float dW = (float) (mHeight * Math.abs(Math.sin(Math.toRadians(rotation)))) * CAMERA_DISTANCE_MAGIC_FACTOR;
                viewScale = mWidth / (mWidth + dW);
            }

            setScale(viewScale);
        }
    }

    public float getFoldRotation() {
        return mFoldRotation;
    }

    public void setScale(float scale) {
        mScale = scale;
        mTopPart.applyScale(scale);
        mBottomPart.applyScale(scale);
    }

    public float getScale() {
        return mScale;
    }

    /**
     * Translation preserving middle line splitting
     */
    public void setRollingDistance(float distance) {
        mRollingDistance = distance;
        mTopPart.applyRollingDistance(distance, mScale);
        mBottomPart.applyRollingDistance(distance, mScale);
    }

    public float getRollingDistance() {
        return mRollingDistance;
    }

    private void setInTransformation(boolean isInTransformation) {
        if (mIsInTransformation == isInTransformation) return;
        mIsInTransformation = isInTransformation;

        mBaseLayout.setDrawToCache(isInTransformation);
        mTopPart.setVisibility(isInTransformation ? VISIBLE : INVISIBLE);
        mBottomPart.setVisibility(isInTransformation ? VISIBLE : INVISIBLE);
    }

    public void setAutoScaleEnabled(boolean isAutoScaleEnabled) {
        mIsAutoScaleEnabled = isAutoScaleEnabled;
    }

    public FrameLayout getBaseLayout() {
        return mBaseLayout;
    }

    public void setLayoutVisibleBounds(Rect visibleBounds) {
        mTopPart.setVisibleBounds(visibleBounds);
        mBottomPart.setVisibleBounds(visibleBounds);
    }

    public void setFoldShading(FoldShading shading) {
        mTopPart.setFoldShading(shading);
        mBottomPart.setFoldShading(shading);
    }


    /**
     * View holder layout that can draw itself into given canvas
     */
    private static class BaseLayout extends FrameLayout {

        private Canvas mCacheCanvas;
        private boolean mIsDrawToCache;

        @SuppressWarnings("deprecation")
        private BaseLayout(FoldableItemLayout layout) {
            super(layout.getContext());

            int matchParent = ViewGroup.LayoutParams.MATCH_PARENT;
            LayoutParams params = new LayoutParams(matchParent, matchParent);
            layout.addView(this, params);

            // Moving background
            this.setBackgroundDrawable(layout.getBackground());
            layout.setBackgroundDrawable(null);

            setWillNotDraw(false);
        }

        private void moveInflatedChildren(FoldableItemLayout layout, int firstSkippedItems) {
            while (layout.getChildCount() > firstSkippedItems) {
                View view = layout.getChildAt(firstSkippedItems);
                LayoutParams params = (LayoutParams) view.getLayoutParams();
                layout.removeViewAt(firstSkippedItems);
                addView(view, params);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (mIsDrawToCache) {
                if (mCacheCanvas != null) {
                    mCacheCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    super.draw(mCacheCanvas);
                }
            } else {
                super.draw(canvas);
            }
        }

        private void setCacheCanvas(Canvas cacheCanvas) {
            mCacheCanvas = cacheCanvas;
        }

        private void setDrawToCache(boolean drawToCache) {
            if (mIsDrawToCache == drawToCache) return;
            mIsDrawToCache = drawToCache;
            invalidate();
        }

    }

    /**
     * Splat part view. It will draw top or bottom part of cached bitmap and overlay shadows.
     * Also it contains main logic for all transformations (fold rotation, scale, "rolling distance").
     */
    private static class PartView extends View {

        private final int mGravity;

        private Bitmap mBitmap;
        private final Rect mBitmapBounds = new Rect();

        private float mClippingFactor = 0.5f;

        private final Paint mBitmapPaint;

        private Rect mVisibleBounds;

        private int mInternalVisibility;
        private int mExternalVisibility;

        private float mLocalFoldRotation;
        private FoldShading mShading;

        public PartView(FoldableItemLayout parent, int gravity) {
            super(parent.getContext());
            mGravity = gravity;

            final int matchParent = LayoutParams.MATCH_PARENT;
            parent.addView(this, new LayoutParams(matchParent, matchParent));
            setCameraDistance(CAMERA_DISTANCE * getResources().getDisplayMetrics().densityDpi);

            mBitmapPaint = new Paint();
            mBitmapPaint.setDither(true);
            mBitmapPaint.setFilterBitmap(true);

            setWillNotDraw(false);
        }

        private void setCacheBitmap(Bitmap bitmap) {
            mBitmap = bitmap;
            calculateBitmapBounds();
        }

        private void setVisibleBounds(Rect visibleBounds) {
            mVisibleBounds = visibleBounds;
            calculateBitmapBounds();
        }

        private void setFoldShading(FoldShading shading) {
            mShading = shading;
        }

        private void calculateBitmapBounds() {
            if (mBitmap == null) {
                mBitmapBounds.set(0, 0, 0, 0);
            } else {
                int h = mBitmap.getHeight();
                int w = mBitmap.getWidth();

                int top = mGravity == Gravity.TOP ? 0 : (int) (h * (1 - mClippingFactor) - 0.5f);
                int bottom = mGravity == Gravity.TOP ? (int) (h * mClippingFactor + 0.5f) : h;

                mBitmapBounds.set(0, top, w, bottom);
                if (mVisibleBounds != null) {
                    if (!mBitmapBounds.intersect(mVisibleBounds)) {
                        mBitmapBounds.set(0, 0, 0, 0); // no intersection
                    }
                }
            }

            invalidate();
        }

        private void applyFoldRotation(float rotation) {
            float position = rotation;
            while (position < 0) position += 360;
            position %= 360;
            if (position > 180) position -= 360; // now position within (-180; 180]

            float rotationX = 0;
            boolean isVisible = true;

            if (mGravity == Gravity.TOP) {
                if (position <= -90 || position == 180) { // (-180; -90] || {180} - will not show
                    isVisible = false;
                } else if (position < 0) { // (-90; 0) - applying rotation
                    rotationX = position;
                }
                // [0; 180) - holding still
            } else {
                if (position >= 90) { // [90; 180] - will not show
                    isVisible = false;
                } else if (position > 0) { // (0; 90) - applying rotation
                    rotationX = position;
                }
                // else: (-180; 0] - holding still
            }

            setRotationX(rotationX);

            mInternalVisibility = isVisible ? VISIBLE : INVISIBLE;
            applyVisibility();

            mLocalFoldRotation = position;

            invalidate(); // needed to draw shadow overlay
        }

        private void applyScale(float scale) {
            setScaleX(scale);
            setScaleY(scale);
        }

        private void applyRollingDistance(float distance, float scale) {
            // applying translation
            setTranslationY((int) (distance * scale + 0.5f));

            // computing clipping for top view (bottom clipping will be 1 - topClipping)
            final int h = getHeight() / 2;
            final float topClipping = h == 0 ? 0.5f : (h - distance) / h / 2;

            mClippingFactor = mGravity == Gravity.TOP ? topClipping : 1f - topClipping;

            calculateBitmapBounds();
        }

        @Override
        public void setVisibility(int visibility) {
            mExternalVisibility = visibility;
            applyVisibility();
        }

        private void applyVisibility() {
            super.setVisibility(mExternalVisibility == VISIBLE ? mInternalVisibility : mExternalVisibility);
        }

        @Override
        public void draw(Canvas canvas) {
            if (mShading != null)
                mShading.onPreDraw(canvas, mBitmapBounds, mLocalFoldRotation, mGravity);
            if (mBitmap != null)
                canvas.drawBitmap(mBitmap, mBitmapBounds, mBitmapBounds, mBitmapPaint);
            if (mShading != null)
                mShading.onPostDraw(canvas, mBitmapBounds, mLocalFoldRotation, mGravity);
        }

    }

}
