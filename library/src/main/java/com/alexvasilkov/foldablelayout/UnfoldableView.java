package com.alexvasilkov.foldablelayout;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

/**
 * View that provides ability to switch between 2 different views (cover view & details view)
 * with fold animation.
 * <p/>
 * It is implemented as subclass of FoldableListLayout with only 2 views to scroll between.
 */
public class UnfoldableView extends FoldableListLayout {

    private static final float DEFAULT_SCROLL_FACTOR = 2f;

    private static final int STATE_FOLDED = 0;
    private static final int STATE_UNFOLDING = 1;
    private static final int STATE_UNFOLDED = 2;
    private static final int STATE_FOLDING = 3;


    private View defaultDetailsPlaceHolderView;
    private View defaultCoverPlaceHolderView;

    private View detailsView;
    private View coverView;

    private View scheduledCoverView;
    private View scheduledDetailsView;

    private View detailsPlaceHolderView;
    private View coverPlaceHolderView;
    private CoverHolderLayout coverHolderLayout;

    private boolean origClipChildren;
    private ViewGroup.LayoutParams detailsViewParams;
    private ViewGroup.LayoutParams coverViewParams;
    private int detailsViewParamWidth;
    private int detailsViewParamHeight;
    private int coverViewParamWidth;
    private int coverViewParamHeight;
    private Rect coverViewPosition;
    private Rect detailsViewPosition;

    private Adapter adapter;

    private float lastFoldRotation;
    private int state = STATE_FOLDED;

    private OnFoldingListener foldingListener;

    public UnfoldableView(Context context) {
        super(context);
        init(context);
    }

    public UnfoldableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UnfoldableView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        coverHolderLayout = new CoverHolderLayout(context);
        defaultDetailsPlaceHolderView = new View(context);
        defaultCoverPlaceHolderView = new View(context);
        adapter = new Adapter();

        setScrollFactor(DEFAULT_SCROLL_FACTOR);
    }

    public void setOnFoldingListener(OnFoldingListener listener) {
        this.foldingListener = listener;
    }

    @SuppressWarnings("unused") // Public API
    public void changeCoverView(View coverView) {
        if (this.coverView == null || this.coverView == coverView) {
            return; // Nothing to do
        }
        clearCoverViewInternal();
        setCoverViewInternal(coverView);
    }

    protected View createDetailsPlaceHolderView() {
        return defaultDetailsPlaceHolderView;
    }

    protected View createCoverPlaceHolderView() {
        return defaultCoverPlaceHolderView;
    }

    /**
     * Starting unfold animation for given views.
     */
    public void unfold(View coverView, View detailsView) {
        if (this.coverView == coverView && this.detailsView == detailsView) {
            scrollToPosition(1); // Starting unfold animation
            return;
        }

        if ((this.coverView != null && this.coverView != coverView)
                || (this.detailsView != null && this.detailsView != detailsView)) {
            // Cover or details view is differ - closing details and schedule reopening
            scheduledCoverView = coverView;
            scheduledDetailsView = detailsView;
            foldBack();
            return;
        }

        // Enabling children clipping, it will be needed if cover view is bigger then half
        // of details view, see CoverHolderLayout#onMeasyre() method.
        ViewGroup parent = (ViewGroup) getParent();
        // In old versions we can't know if children clipping is enabled, we'll assume it's enabled
        origClipChildren = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            origClipChildren = parent.getClipChildren();
        }
        parent.setClipChildren(false);

        // Initializing foldable views
        setCoverViewInternal(coverView);
        setDetailsViewInternal(detailsView);
        setAdapter(adapter);

        setState(STATE_UNFOLDING);
        scrollToPosition(1); // starting unfold animation
    }

    public void foldBack() {
        scrollToPosition(0);
    }

    private void onFoldedBack() {
        // Clearing all foldable views and reverting to initial state
        setAdapter(null);

        ((ViewGroup) getParent()).setClipChildren(origClipChildren);
        clearCoverViewInternal();
        clearDetailsViewInternal();

        // Clearing translations
        setTranslationX(0f);
        setTranslationY(0f);

        Utils.postOnAnimation(this, new Runnable() {
            @Override
            public void run() {
                if (scheduledCoverView != null
                        && scheduledDetailsView != null
                        && scheduledCoverView.getParent() != null
                        && scheduledDetailsView.getParent() != null) {
                    unfold(scheduledCoverView, scheduledDetailsView);
                    scheduledCoverView = scheduledDetailsView = null;
                }
            }
        });
    }

    public boolean isUnfolding() {
        return state == STATE_UNFOLDING;
    }

    @SuppressWarnings("unused") // Public API
    public boolean isFoldingBack() {
        return state == STATE_FOLDING;
    }

    public boolean isUnfolded() {
        return state == STATE_UNFOLDED;
    }

    private void setState(int state) {
        if (this.state != state) {
            this.state = state;

            if (state == STATE_FOLDED) {
                onFoldedBack();
            }

            if (foldingListener != null) {
                switch (state) {
                    case STATE_UNFOLDING:
                        foldingListener.onUnfolding(this);
                        break;
                    case STATE_FOLDING:
                        foldingListener.onFoldingBack(this);
                        break;
                    case STATE_UNFOLDED:
                        foldingListener.onUnfolded(this);
                        break;
                    case STATE_FOLDED:
                        foldingListener.onFoldedBack(this);
                        break;
                    default:
                        // Nothing
                }
            }
        }

    }

    @Override
    protected void setFoldRotation(float rotation, boolean isFromUser) {
        super.setFoldRotation(rotation, isFromUser);
        if (coverView == null || detailsView == null) {
            return; // Nothing we can do here
        }

        rotation = getFoldRotation(); // Parent view will correctly keep rotation in bounds for us

        // Translating from cover position to details position
        float stage = rotation / 180f; // From 0 = only cover view, to 1 = only details view

        float fromX = coverViewPosition.centerX();
        float toX = detailsViewPosition.centerX();

        float fromY = coverViewPosition.top;
        float toY = detailsViewPosition.centerY();

        setTranslationX((fromX - toX) * (1f - stage));
        setTranslationY((fromY - toY) * (1f - stage));

        // Tracking states

        final float lastRotation = lastFoldRotation;
        lastFoldRotation = rotation;

        if (foldingListener != null) {
            foldingListener.onFoldProgress(this, stage);
        }

        if (rotation > lastRotation) {
            setState(STATE_UNFOLDING);
        }

        if (rotation < lastRotation) {
            setState(STATE_FOLDING);
        }

        if (rotation == 180f) {
            setState(STATE_UNFOLDED);
        }

        if (rotation == 0f && state == STATE_FOLDING) {
            setState(STATE_FOLDED);
        }

        // On old versions invalidation is done incorrectly if clipChildren is set to false,
        // so we have to invalidate entire parent to prevent animation artifacts
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            ((ViewGroup) getParent()).invalidate();
        }
    }

    @Override
    protected void onFoldRotationChanged(FoldableItemLayout layout, int position) {
        super.onFoldRotationChanged(layout, position);

        float stage = getFoldRotation() / 180f; // From 0 = only cover view, to 1 = only details

        final float scale = detailsViewPosition.width() / (float) coverViewPosition.width();

        if (position == 0) { // Cover view
            // Scaling cover view from origin size to the size (width) of the details view
            float coverScale = 1f - (1f - scale) * stage;
            layout.setScale(coverScale);
        } else { // Details view
            // Scaling details view from cover size to the original size
            float detailsScale = 1f - (1f - 1f / scale) * (1f - stage);
            layout.setScale(detailsScale);

            float dh = coverViewPosition.height() * scale - 0.5f * detailsViewPosition.height();
            float translationY = stage < 0.5f ? dh * (1f - 2f * stage) : 0f;

            layout.setRollingDistance(translationY);
        }
    }

    @Override
    protected void animateFold(float to) {
        super.animateFold(to);

        if (to <= getFoldRotation() && state != STATE_FOLDED) {
            setState(STATE_FOLDING);
        }
    }


    private void setDetailsViewInternal(View detailsView) {
        // Saving details view data
        this.detailsView = detailsView;
        detailsViewParams = detailsView.getLayoutParams();
        detailsViewParamWidth = detailsViewParams.width;
        detailsViewParamHeight = detailsViewParams.height;

        // Getting details view positions on screen
        detailsViewPosition = getViewGlobalPosition(detailsView);

        // Creating placeholder to show in place of details view
        detailsPlaceHolderView = createDetailsPlaceHolderView();

        // Setting precise width/height params and switching details view with it's placeholder
        detailsViewParams.width = detailsViewPosition.width();
        detailsViewParams.height = detailsViewPosition.height();
        switchViews(detailsView, detailsPlaceHolderView, detailsViewParams);
    }

    private void clearDetailsViewInternal() {
        if (detailsView == null) {
            return; // Nothing to do
        }

        // Restoring original width & height params and adding cover view back to it's place
        detailsViewParams.width = detailsViewParamWidth;
        detailsViewParams.height = detailsViewParamHeight;
        switchViews(detailsPlaceHolderView, detailsView, detailsViewParams);

        // Clearing references
        detailsView = null;
        detailsViewParams = null;
        detailsViewPosition = null;
        detailsPlaceHolderView = null;
    }

    private void setCoverViewInternal(View coverView) {
        // Saving cover view data
        this.coverView = coverView;
        coverViewParams = coverView.getLayoutParams();
        coverViewParamWidth = coverViewParams.width;
        coverViewParamHeight = coverViewParams.height;

        // Getting cover view positions on screen
        coverViewPosition = getViewGlobalPosition(coverView);

        // Creating placeholder to show in place of cover view
        coverPlaceHolderView = createCoverPlaceHolderView();

        // Setting precise width & height params and switching cover view with it's placeholder
        coverViewParams.width = coverViewPosition.width();
        coverViewParams.height = coverViewPosition.height();
        switchViews(coverView, coverPlaceHolderView, coverViewParams);

        // Moving cover view into special cover view holder (for unfold animation)
        coverHolderLayout.setView(coverView, coverViewPosition.width(),
                coverViewPosition.height());
    }

    private void clearCoverViewInternal() {
        if (coverView == null) {
            return; // Nothing to do
        }

        // Freeing coverView so we can add it back to it's place
        coverHolderLayout.clearView();

        // Restoring original width & height params and adding cover view back to it's place
        coverViewParams.width = coverViewParamWidth;
        coverViewParams.height = coverViewParamHeight;
        switchViews(coverPlaceHolderView, coverView, coverViewParams);

        // Clearing references
        coverView = null;
        coverViewParams = null;
        coverViewPosition = null;
        coverPlaceHolderView = null;
    }

    private void switchViews(View origin, View replacement, ViewGroup.LayoutParams params) {
        if (params == null) {
            params = origin.getLayoutParams();
        }

        final ViewGroup parent = (ViewGroup) origin.getParent();
        // Original view can be removed from parent externally, in this case we can do nothing.
        if (parent != null) {
            int index = parent.indexOfChild(origin);
            parent.removeViewAt(index);
            parent.addView(replacement, index, params);
        }
    }

    private Rect getViewGlobalPosition(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1],
                location[0] + view.getWidth(), location[1] + view.getHeight());
    }


    /**
     * Simple adapter that will alternate between cover view holder layout and details layout.
     */
    private class Adapter extends BaseAdapter {

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycledView, ViewGroup parent) {
            return position == 0 ? coverHolderLayout : detailsView;
        }
    }


    /**
     * Cover view holder layout. It can contain at most one child which will be positioned
     * in the top|center_horizontal location of bottom half of the view.
     */
    private static class CoverHolderLayout extends FrameLayout {

        private final Rect visibleBounds = new Rect();
        private float origPivotY;

        CoverHolderLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            final int half = getMeasuredHeight() / 2;

            // Cover view should occupy bottom half of the foldable item view
            setPadding(0, half, 0, 0);

            // If cover view is bigger than half of details view then we need to apply a hack:
            // we will scale cover view down in Y direction so that it fits half of details view,
            // and then we will scale foldable item view up to restore original scale
            View view = getView();
            float scaleY = view.getMeasuredHeight() > half
                    ? half / (float) view.getMeasuredHeight() : 1f;
            view.setScaleY(scaleY);
            getParentFoldableItem().setScaleFactorY(1f / scaleY);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            // Collecting visible bounds of child view,
            // it will be used to correctly draw shadows and to improve drawing performance
            View view = getView();
            visibleBounds.set(view.getLeft(), view.getTop(),
                    view.getLeft() + view.getWidth(), view.getTop() + view.getHeight());
            getParentFoldableItem().setLayoutVisibleBounds(visibleBounds);
        }

        private FoldableItemLayout getParentFoldableItem() {
            ViewGroup parent = this;
            while (parent != null) {
                parent = (ViewGroup) parent.getParent();
                if (parent instanceof FoldableItemLayout) {
                    return (FoldableItemLayout) parent;
                }
            }
            throw new AssertionError("CoverHolderLayout is not descendant of FoldableItemLayout");
        }

        void setView(View view, int width, int height) {
            LayoutParams params = new LayoutParams(width, height, Gravity.CENTER_HORIZONTAL);
            addView(view, params);

            // Setting temporary pivotal point, see #onMeasure()
            origPivotY = view.getPivotY();
            view.setPivotY(0f);
        }

        void clearView() {
            // Restoring original scale and pivot point
            View view = getView();
            view.setScaleY(1f);
            view.setPivotY(origPivotY);
            removeAllViews();
        }

        private View getView() {
            if (getChildCount() == 1) {
                return getChildAt(0);
            } else {
                throw new AssertionError("CoverHolderLayout should have exactly one child");
            }
        }

    }


    public interface OnFoldingListener {
        void onUnfolding(UnfoldableView unfoldableView);

        void onUnfolded(UnfoldableView unfoldableView);

        void onFoldingBack(UnfoldableView unfoldableView);

        void onFoldedBack(UnfoldableView unfoldableView);

        void onFoldProgress(UnfoldableView unfoldableView, float progress);
    }

    public static class SimpleFoldingListener implements OnFoldingListener {
        @Override
        public void onUnfolding(UnfoldableView unfoldableView) {
        }

        @Override
        public void onUnfolded(UnfoldableView unfoldableView) {
        }

        @Override
        public void onFoldingBack(UnfoldableView unfoldableView) {
        }

        @Override
        public void onFoldedBack(UnfoldableView unfoldableView) {
        }

        @Override
        public void onFoldProgress(UnfoldableView unfoldableView, float progress) {
        }
    }

}
