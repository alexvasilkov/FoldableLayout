package com.alexvasilkov.foldablelayout;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.alexvasilkov.foldablelayout.shading.FoldShading;
import com.alexvasilkov.foldablelayout.shading.SimpleFoldShading;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Foldable items list layout.
 * <p/>
 * It wraps views created by given BaseAdapter into FoldableItemLayouts and provides functionality
 * to scroll among them.
 */
public class FoldableListLayout extends FrameLayout {

    private static final long ANIMATION_DURATION_PER_ITEM = 600L;
    private static final float MIN_FLING_VELOCITY = 600f;
    private static final float DEFAULT_SCROLL_FACTOR = 1.33f;

    private static final LayoutParams PARAMS =
            new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    private static final int MAX_CHILDREN_COUNT = 3;

    private OnFoldRotationListener foldRotationListener;
    private BaseAdapter adapter;

    private float foldRotation;
    private float minRotation;
    private float maxRotation;

    private FoldableItemLayout backLayout;
    private FoldableItemLayout frontLayout;
    private FoldShading foldShading;
    private boolean isAutoScaleEnabled;

    private final SparseArray<FoldableItemLayout> foldableItemsMap = new SparseArray<>();
    private final Queue<FoldableItemLayout> foldableItemsCache = new LinkedList<>();

    private final SparseArray<Queue<View>> recycledViews = new SparseArray<>();
    private final Map<View, Integer> viewsTypesMap = new HashMap<>();

    private boolean isGesturesEnabled = true;
    private ObjectAnimator animator;
    private long lastTouchEventTime;
    private int lastTouchEventAction;
    private boolean lastTouchEventResult;
    private GestureDetector gestureDetector;

    private FlingAnimation flingAnimation;

    private float minDistanceBeforeScroll;
    private boolean isScrollDetected;
    private float scrollFactor = DEFAULT_SCROLL_FACTOR;
    private float scrollStartRotation;
    private float scrollStartY;

    private final DataSetObserver dataObserver = new DataSetObserver() {
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
        gestureDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return FoldableListLayout.this.onDown();
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distX, float distY) {
                return FoldableListLayout.this.onScroll(e1, e2);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velX, float velY) {
                return FoldableListLayout.this.onFling(velY);
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
        animator = ObjectAnimator.ofFloat(this, "foldRotation", 0f);
        minDistanceBeforeScroll = ViewConfiguration.get(context).getScaledTouchSlop();

        flingAnimation = new FlingAnimation();

        foldShading = new SimpleFoldShading();

        setChildrenDrawingOrderEnabled(true);
    }

    /**
     * Internal parameter. Defines scroll velocity when user scrolls list.
     */
    protected void setScrollFactor(float scrollFactor) {
        this.scrollFactor = scrollFactor;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // We want to manually draw selected children
        if (backLayout != null) {
            backLayout.draw(canvas);
        }
        if (frontLayout != null) {
            frontLayout.draw(canvas);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
        return getCount() > 0; // No touches for underlying views if we have items
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int index) {
        if (frontLayout == null) {
            return index; // Default order
        }

        // We need to return front view as last item for correct touches handling
        int front = indexOfChild(frontLayout);
        return index == childCount - 1 ? front : (index >= front ? index + 1 : index);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Listening for events but propagates them to children if no own gestures are detected
        return isGesturesEnabled && processTouch(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // We will be here if no children wants to handle touches or if own gesture detected
        return isGesturesEnabled && processTouch(event);
    }

    @SuppressWarnings("unused") // Public API
    public void setOnFoldRotationListener(OnFoldRotationListener listener) {
        foldRotationListener = listener;
    }

    /**
     * Sets shading to use during fold rotation. Should be called before
     * {@link #setAdapter(android.widget.BaseAdapter)}
     */
    public void setFoldShading(FoldShading shading) {
        foldShading = shading;
    }

    /**
     * Sets whether gestures are enabled or not. Useful when layout content is scrollable.
     */
    @SuppressWarnings("unused") // Public API
    public void setGesturesEnabled(boolean isGesturesEnabled) {
        this.isGesturesEnabled = isGesturesEnabled;
    }

    /**
     * Sets whether view should scale down to fit moving part into screen.
     */
    @SuppressWarnings("unused") // Public API
    public void setAutoScaleEnabled(boolean isAutoScaleEnabled) {
        this.isAutoScaleEnabled = isAutoScaleEnabled;
        for (int i = 0, size = foldableItemsMap.size(); i < size; i++) {
            foldableItemsMap.valueAt(i).setAutoScaleEnabled(isAutoScaleEnabled);
        }
    }


    public void setAdapter(BaseAdapter adapter) {
        if (this.adapter != null) {
            this.adapter.unregisterDataSetObserver(dataObserver);
        }
        this.adapter = adapter;
        if (this.adapter != null) {
            this.adapter.registerDataSetObserver(dataObserver);
        }
        updateAdapterData();
    }

    @SuppressWarnings("unused") // Public API
    public BaseAdapter getAdapter() {
        return adapter;
    }

    public int getCount() {
        return adapter == null ? 0 : adapter.getCount();
    }

    private void updateAdapterData() {
        int count = getCount();
        minRotation = 0f;
        maxRotation = count == 0 ? 0f : 180f * (count - 1);

        freeAllLayouts(); // Clearing old bindings
        recycledViews.clear();
        viewsTypesMap.clear();

        // Recalculating items
        setFoldRotation(foldRotation);
    }

    public float getFoldRotation() {
        return foldRotation;
    }

    public final void setFoldRotation(float rotation) {
        setFoldRotation(rotation, false);
    }

    protected void setFoldRotation(float rotation, boolean isFromUser) {
        if (isFromUser) {
            animator.cancel();
            flingAnimation.stop();
        }

        rotation = Math.min(Math.max(minRotation, rotation), maxRotation);

        foldRotation = rotation;

        final int firstVisiblePosition = (int) (rotation / 180f);
        final float localRotation = rotation % 180f;
        final int totalCount = getCount();

        FoldableItemLayout firstLayout = null;
        FoldableItemLayout secondLayout = null;

        if (firstVisiblePosition < totalCount) {
            firstLayout = getLayoutForItem(firstVisiblePosition);
            firstLayout.setFoldRotation(localRotation);
            onFoldRotationChanged(firstLayout, firstVisiblePosition);
        }

        if (firstVisiblePosition + 1 < totalCount) {
            secondLayout = getLayoutForItem(firstVisiblePosition + 1);
            secondLayout.setFoldRotation(localRotation - 180f);
            onFoldRotationChanged(secondLayout, firstVisiblePosition + 1);
        }

        boolean isReversedOrder = localRotation <= 90f;

        if (isReversedOrder) {
            backLayout = secondLayout;
            frontLayout = firstLayout;
        } else {
            backLayout = firstLayout;
            frontLayout = secondLayout;
        }

        if (foldRotationListener != null) {
            foldRotationListener.onFoldRotation(rotation, isFromUser);
        }

        // When hardware acceleration is enabled view may not be invalidated and redrawn,
        // but we need it to properly draw animation
        invalidate();
    }

    protected void onFoldRotationChanged(FoldableItemLayout layout, int position) {
        // Subclasses can apply their transformations here
    }

    private FoldableItemLayout getLayoutForItem(int position) {
        FoldableItemLayout layout = foldableItemsMap.get(position);
        if (layout != null) {
            return layout; // We already have layout for this position
        }

        // Trying to free used layout (far enough from currently requested)
        int farthestItem = position;

        int size = foldableItemsMap.size();
        for (int i = 0; i < size; i++) {
            int pos = foldableItemsMap.keyAt(i);
            if (Math.abs(position - pos) > Math.abs(position - farthestItem)) {
                farthestItem = pos;
            }
        }

        if (Math.abs(farthestItem - position) >= MAX_CHILDREN_COUNT) {
            layout = foldableItemsMap.get(farthestItem);
            foldableItemsMap.remove(farthestItem);
            recycleAdapterView(layout);
        }

        if (layout == null) {
            // Trying to find cached layout
            layout = foldableItemsCache.poll();
        }

        if (layout == null) {
            // If still no suited layout - create it
            layout = new FoldableItemLayout(getContext());
            layout.setFoldShading(foldShading);
            addView(layout, PARAMS);
        }

        layout.setAutoScaleEnabled(isAutoScaleEnabled);
        setupAdapterView(layout, position);
        foldableItemsMap.put(position, layout);

        return layout;
    }

    private void setupAdapterView(FoldableItemLayout layout, int position) {
        // Binding layout to new data
        int type = adapter.getItemViewType(position);

        View recycledView = null;
        if (type != Adapter.IGNORE_ITEM_VIEW_TYPE) {
            Queue<View> cache = recycledViews.get(type);
            recycledView = cache == null ? null : cache.poll();
        }

        View view = adapter.getView(position, recycledView, layout.getBaseLayout());

        if (type != Adapter.IGNORE_ITEM_VIEW_TYPE) {
            viewsTypesMap.put(view, type);
        }

        layout.getBaseLayout().addView(view, PARAMS);
    }

    private void recycleAdapterView(FoldableItemLayout layout) {
        if (layout.getBaseLayout().getChildCount() == 0) {
            return; // Nothing to recycle
        }

        View view = layout.getBaseLayout().getChildAt(0);
        layout.getBaseLayout().removeAllViews();

        Integer type = viewsTypesMap.remove(view);
        if (type != null) {
            Queue<View> cache = recycledViews.get(type);
            if (cache == null) {
                recycledViews.put(type, cache = new LinkedList<>());
            }
            cache.offer(view);
        }
    }

    private void freeAllLayouts() {
        int size = foldableItemsMap.size();
        for (int i = 0; i < size; i++) {
            FoldableItemLayout layout = foldableItemsMap.valueAt(i);
            layout.getBaseLayout().removeAllViews(); // Clearing old data
            foldableItemsCache.offer(layout);
        }
        foldableItemsMap.clear();
    }

    /**
     * Returns position of the main visible item.
     */
    @SuppressWarnings("unused") // Public API
    public int getPosition() {
        return Math.round(foldRotation / 180f);
    }

    public void scrollToPosition(int index) {
        index = Math.max(0, Math.min(index, getCount() - 1));
        animateFold(index * 180f);
    }

    protected void scrollToNearestPosition() {
        scrollToPosition((int) ((getFoldRotation() + 90f) / 180f));
    }

    protected void animateFold(float to) {
        final float from = getFoldRotation();
        final long duration = (long) Math.abs(ANIMATION_DURATION_PER_ITEM * (to - from) / 180f);

        flingAnimation.stop();

        animator.cancel();
        animator.setFloatValues(from, to);
        animator.setDuration(duration);
        animator.start();
    }


    private boolean processTouch(MotionEvent event) {
        // Checking if that event was already processed
        // (by onInterceptTouchEvent prior to onTouchEvent)
        long eventTime = event.getEventTime();
        int action = event.getActionMasked();

        if (lastTouchEventTime == eventTime && lastTouchEventAction == action) {
            return lastTouchEventResult;
        }

        lastTouchEventTime = eventTime;
        lastTouchEventAction = action;

        if (getCount() > 0) {
            // Fixing event's Y position due to performed translation
            MotionEvent eventCopy = MotionEvent.obtain(event);
            eventCopy.offsetLocation(0, getTranslationY());
            lastTouchEventResult = gestureDetector.onTouchEvent(eventCopy);
            eventCopy.recycle();
        } else {
            lastTouchEventResult = false;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            onUpOrCancel();
        }

        return lastTouchEventResult;
    }

    private boolean onDown() {
        isScrollDetected = false;
        animator.cancel();
        flingAnimation.stop();
        return false;
    }

    private void onUpOrCancel() {
        if (!flingAnimation.isAnimating()) {
            scrollToNearestPosition();
        }
    }

    private boolean onScroll(MotionEvent firstEvent, MotionEvent moveEvent) {
        if (!isScrollDetected && getHeight() != 0
                && Math.abs(firstEvent.getY() - moveEvent.getY()) > minDistanceBeforeScroll) {
            isScrollDetected = true;
            scrollStartRotation = getFoldRotation();
            scrollStartY = moveEvent.getY();
        }

        if (isScrollDetected) {
            float distance = scrollStartY - moveEvent.getY();
            float rotation = 180f * scrollFactor * distance / getHeight();
            setFoldRotation(scrollStartRotation + rotation, true);
        }

        return isScrollDetected;
    }

    private boolean onFling(float velocityY) {
        if (getHeight() == 0) {
            return false;
        }

        float velocity = -velocityY / getHeight() * 180f;
        velocity = Math.max(MIN_FLING_VELOCITY, Math.abs(velocity)) * Math.signum(velocity);

        return flingAnimation.fling(velocity);
    }


    private class FlingAnimation implements Runnable {

        private boolean isAnimating;
        private long lastTime;
        private float velocity;
        private float min;
        private float max;

        @Override
        public void run() {
            long now = System.currentTimeMillis();
            float delta = velocity / 1000f * (now - lastTime);
            lastTime = now;

            float rotation = getFoldRotation();
            rotation = Math.max(min, Math.min(rotation + delta, max));

            setFoldRotation(rotation);

            if (rotation != min && rotation != max) {
                startInternal();
            } else {
                stop();
            }
        }

        private void startInternal() {
            Utils.postOnAnimation(FoldableListLayout.this, this);
            isAnimating = true;
        }

        void stop() {
            FoldableListLayout.this.removeCallbacks(this);
            isAnimating = false;
        }

        boolean isAnimating() {
            return isAnimating;
        }

        boolean fling(float velocity) {
            float rotation = getFoldRotation();
            if (rotation % 180f == 0f) {
                return false;
            }

            int position = (int) (rotation / 180f);
            lastTime = System.currentTimeMillis();
            this.velocity = velocity;
            min = position * 180f;
            max = min + 180f;

            startInternal();

            return true;
        }

    }

    public interface OnFoldRotationListener {
        void onFoldRotation(float rotation, boolean isFromUser);
    }

}
