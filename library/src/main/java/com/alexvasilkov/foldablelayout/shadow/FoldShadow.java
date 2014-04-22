package com.alexvasilkov.foldablelayout.shadow;

import android.graphics.Canvas;
import android.graphics.Rect;

public interface FoldShadow {
    void onPreDraw(Canvas canvas, Rect bounds, float rotation, int gravity);

    void onPostDraw(Canvas canvas, Rect bounds, float rotation, int gravity);
}
