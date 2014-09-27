package com.alexvasilkov.foldablelayout;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.alexvasilkov.foldablelayout.shading.FoldShading;
import com.alexvasilkov.foldablelayout.shading.SimpleFoldShading;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Foldable items list layout.
 * <p/>
 * It wraps views created by given BaseAdapter into FoldableItemLayouts and provides functionality to scroll
 * among them.
 */
public class FoldableListLayout extends FrameLayout implements GestureDetector.OnGestureListener {

    private static final long ANIMATION_DURATION_PER_ITEM = 600;
    private static final float MIN_FLING_VELOCITY = 600;
    private static final float DEFAULT_SCROLL_FACTOR = 1.33f;

    private static final LayoutParams PARAMS = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    private static final int CACHED_LAYOUTS_OFFSET = 2;

    private OnFoldRotationListener mFoldRotationListener;
    private BaseAdapter mAdapter;

    private float mFoldRotation;
    private float mMinRotation, mMaxRotation;

    private FoldableItemLayout mFirstLayout, mSecondLayout;
    private FoldShading mFoldShading;

    private SparseArray<FoldableItemLayout> mFoldableItemsMap = new SparseArray<FoldableItemLayout>();
    private Queue<FoldableItemLayout> mFoldableItemsCache = new LinkedList<FoldableItemLayout>();

    private boolean mIsGesturesEnabled = true;
    private ObjectAnimator mAnimator;
    private long mLastTouchEventTime;
    private int mLastTouchEventAction;
    private boolean mLastTouchEventResult;
    private GestureDetector mGestureDetector;

    private FlingAnimation mFlingAnimation;

    private float mMinDistanceBeforeScroll;
    private boolean mIsScrollDetected;
    private float mScrollFactor = DEFAULT_SCROLL_FACTOR;
    private float mScrollStartRotation;
    private float mScrollStartDistance;

    public FoldableListLayout(Context context) {
        super(context);
        init(context);
    }

    public FoldableListLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FoldableListLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setIsLongpressEnabled(false);
        mAnimator = ObjectAnimator.ofFloat(this, "foldRotation", 0);
        mMinDistanceBeforeScroll = ViewConfiguration.get(context).getScaledPagingTouchSlop();

        mFlingAnimation = new FlingAnimation();

        mFoldShading = new SimpleFoldShading();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // We want manually draw only selected children
        if (mFirstLayout != null) mFirstLayout.draw(canvas);
        if (mSecondLayout != null) mSecondLayout.draw(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
        return getCount() > 0; // No touches for underlying views if we have items
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Listening for events but propagates them to children if no own gestures are detected
        return mIsGesturesEnabled && processTouch(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // We will be here if no children wants to handle current touches or if own gesture is detected
        return mIsGesturesEnabled && processTouch(event);
    }

    public void setOnFoldRotationListener(OnFoldRotationListener listener) {
        mFoldRotationListener = listener;
    }

    /**
     * Setting shading to use during fold rotation. Should be called before {@link #setAdapter(android.widget.BaseAdapter)}
     */
    public void setFoldShading(FoldShading shading) {
        mFoldShading = shading;
    }

    /**
     * Set whether gestures are enabled or not. Useful when layout content is scrollable.
     */
    public void setGesturesEnabled(boolean isGesturesEnabled) {
        mIsGesturesEnabled = isGesturesEnabled;
    }

    /**
     * Internal parameter. Defines scroll velocity when user scrolls list.
     */
    protected void setScrollFactor(float scrollFactor) {
        mScrollFactor = scrollFactor;
    }

    public void setAdapter(BaseAdapter adapter) {
        if (mAdapter != null) mAdapter.unregisterDataSetObserver(mDataObserver);
        mAdapter = adapter;
        if (mAdapter != null) mAdapter.registerDataSetObserver(mDataObserver);
        updateAdapterData();
    }

    public BaseAdapter getAdapter() {
        return mAdapter;
    }

    public int getCount() {
        return mAdapter == null ? 0 : mAdapter.getCount();
    }

    private void updateAdapterData() {
        int size = getCount();
        mMinRotation = 0;
        mMaxRotation = size == 0 ? 0 : 180 * (size - 1);

        freeAllLayouts(); // clearing old bindings

        // recalculating items
        setFoldRotation(mFoldRotation);
    }

    public final void setFoldRotation(float rotation) {
        setFoldRotation(rotation, false);
    }

    protected void setFoldRotation(float rotation, boolean isFromUser) {
        if (isFromUser) {
            mAnimator.cancel();
            mFlingAnimation.stop();
        }

        rotation = Math.min(Math.max(mMinRotation, rotation), mMaxRotation);

        mFoldRotation = rotation;

        int firstVisiblePosition = (int) (rotation / 180);
        float localRotation = rotation % 180;

        int size = getCount();
        boolean isHasFirst = firstVisiblePosition < size;
        boolean isHasSecond = firstVisiblePosition + 1 < size;

        FoldableItemLayout firstLayout = isHasFirst ? getLayoutForItem(firstVisiblePosition) : null;
        FoldableItemLayout secondLayout = isHasSecond ? getLayoutForItem(firstVisiblePosition + 1) : null;

        if (isHasFirst) {
            firstLayout.setFoldRotation(localRotation);
            onFoldRotationChanged(firstLayout, firstVisiblePosition);
        }

        if (isHasSecond) {
            secondLayout.setFoldRotation(localRotation - 180);
            onFoldRotationChanged(secondLayout, firstVisiblePosition + 1);
        }

        boolean isReversedOrder = localRotation <= 90;

        if (isReversedOrder) {
            mFirstLayout = secondLayout;
            mSecondLayout = firstLayout;
        } else {
            mFirstLayout = firstLayout;
            mSecondLayout = secondLayout;
        }

        if (mFoldRotationListener != null)
            mFoldRotationListener.onFoldRotation(rotation, isFromUser);

        invalidate(); // when hardware acceleration is enabled view may not be invalidated and redrawn, but we need it
    }

    protected void onFoldRotationChanged(FoldableItemLayout layout, int position) {
        // Subclasses can apply their transformations here
    }

    public float getFoldRotation() {
        return mFoldRotation;
    }

    private FoldableItemLayout getLayoutForItem(int position) {
        FoldableItemLayout layout = mFoldableItemsMap.get(position);
        if (layout != null) return layout; // we already have bound layout

        // trying to find cached layout
        layout = mFoldableItemsCache.poll();

        if (layout == null) {
            // trying to free used layout (far enough from currently requested)
            int farthestItem = position;

            int size = mFoldableItemsMap.size();
            for (int i = 0; i < size; i++) {
                int pos = mFoldableItemsMap.keyAt(i);
                if (Math.abs(position - pos) > Math.abs(position - farthestItem)) {
                    farthestItem = pos;
                }
            }

            if (Math.abs(farthestItem - position) > CACHED_LAYOUTS_OFFSET) {
                layout = mFoldableItemsMap.get(farthestItem);
                mFoldableItemsMap.remove(farthestItem);
                layout.getBaseLayout().removeAllViews(); // clearing old data
            }
        }

        if (layout == null) {
            // if still no suited layout - create it
            layout = new FoldableItemLayout(getContext());
            layout.setFoldShading(mFoldShading);
            addView(layout, PARAMS);
        }

        // binding layout to new data
        View view = mAdapter.getView(position, null, layout.getBaseLayout()); // TODO: use recycler
        layout.getBaseLayout().addView(view, PARAMS);

        mFoldableItemsMap.put(position, layout);

        return layout;
    }

    private void freeAllLayouts() {
        int size = mFoldableItemsMap.size();
        for (int i = 0; i < size; i++) {
            FoldableItemLayout layout = mFoldableItemsMap.valueAt(i);
            layout.getBaseLayout().removeAllViews();
            mFoldableItemsCache.offer(layout);
        }
        mFoldableItemsMap.clear();
    }

    public void scrollToPosition(int index) {
        index = Math.max(0, Math.min(index, getCount() - 1));

        float rotation = index * 180f;
        float current = getFoldRotation();
        long duration = (long) Math.abs(ANIMATION_DURATION_PER_ITEM * (rotation - current) / 180f);

        mFlingAnimation.stop();

        mAnimator.cancel();
        mAnimator.setFloatValues(current, rotation);
        mAnimator.setDuration(duration).start();
    }

    protected void scrollToNearestPosition() {
        float current = getFoldRotation();
        scrollToPosition((int) ((current + 90f) / 180f));
    }

    private boolean processTouch(MotionEvent event) {
        // Checking if that event was already processed (by onInterceptTouchEvent prior to onTouchEvent)
        long eventTime = event.getEventTime();
        int action = event.getActionMasked();

        if (mLastTouchEventTime == eventTime && mLastTouchEventAction == action)
            return mLastTouchEventResult;

        mLastTouchEventTime = eventTime;
        mLastTouchEventAction = action;

        if (getCount() > 0) {
            // Fixing event's Y position due to performed translation
            MotionEvent eventCopy = MotionEvent.obtain(event);
            eventCopy.offsetLocation(0, getTranslationY());
            mLastTouchEventResult = mGestureDetector.onTouchEvent(eventCopy);
            eventCopy.recycle();
        } else {
            mLastTouchEventResult = false;
        }

        if (action == MotionEvent.ACTION_UP) {
            if (!mFlingAnimation.isAnimating()) scrollToNearestPosition();
        }

        return mLastTouchEventResult;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        mIsScrollDetected = false;
        mAnimator.cancel();
        mFlingAnimation.stop();
        return false;
    }

    @Override
    public void onShowPress(MotionEvent event) {
        // NO-OP
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        // NO-OP
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        float distance = e1.getY() - e2.getY();
        int h = getHeight();

        if (!mIsScrollDetected && Math.abs(distance) > mMinDistanceBeforeScroll && h != 0) {
            mIsScrollDetected = true;
            mScrollStartRotation = getFoldRotation();
            mScrollStartDistance = distance;
        }

        if (mIsScrollDetected) {
            float rotation = 180f * mScrollFactor * (distance - mScrollStartDistance) / h;
            setFoldRotation(mScrollStartRotation + rotation, true);
        }

        return mIsScrollDetected;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        int h = getHeight();
        if (h == 0) return false;

        float velocity = -velocityY / h * 180f;
        velocity = Math.max(MIN_FLING_VELOCITY, Math.abs(velocity)) * Math.signum(velocity);

        return mFlingAnimation.fling(velocity);
    }

    private DataSetObserver mDataObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            updateAdapterData();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            updateAdapterData();
        }
    };

    public interface OnFoldRotationListener {
        void onFoldRotation(float rotation, boolean isFromUser);
    }


    private class FlingAnimation implements Runnable {

        private final Handler mHandler = new Handler();

        private boolean mIsAnimating;
        private long mLastTime;
        private float mVelocity;
        private float mMin, mMax;

        @Override
        public void run() {
            long now = System.currentTimeMillis();
            float delta = mVelocity / 1000f * (now - mLastTime);
            mLastTime = now;

            float rotation = getFoldRotation();
            rotation = Math.max(mMin, Math.min(rotation + delta, mMax));

            setFoldRotation(rotation);

            if (rotation != mMin && rotation != mMax) {
                startInternal();
            } else {
                stop();
            }
        }

        private void startInternal() {
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, 10); // small delay is required (sometimes runnable can be called immediately)
            mIsAnimating = true;
        }

        void stop() {
            mHandler.removeCallbacks(this);
            mIsAnimating = false;
        }

        boolean isAnimating() {
            return mIsAnimating;
        }

        boolean fling(float velocity) {
            float rotation = getFoldRotation();
            if (rotation % 180 == 0) return false;

            int position = (int) (rotation / 180f);
            mLastTime = System.currentTimeMillis();
            mVelocity = velocity;
            mMin = position * 180f;
            mMax = mMin + 180f;

            startInternal();

            return true;
        }

    }

}
