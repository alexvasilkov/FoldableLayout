package com.alexvasilkov.foldablelayout;

import android.os.Build;
import android.view.View;

class Utils {

    private static final long FRAME_TIME = 10L;

    private Utils() {}

    static void postOnAnimation(View view, Runnable action) {
        view.removeCallbacks(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.postOnAnimationDelayed(action, FRAME_TIME);
        } else {
            view.postDelayed(action, FRAME_TIME);
        }
    }

}
