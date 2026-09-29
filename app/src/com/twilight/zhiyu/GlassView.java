package com.twilight.zhiyu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * 真·毛玻璃容器：按自己在屏幕上的位置，去采样「预模糊背景图」的对应区域，
 * 所以看起来就像这块面板把身后的背景糊掉了（backdrop blur）。
 * 关掉毛玻璃时它就是一个普通容器。
 */
public class GlassView extends LinearLayout {

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    private final int[] rootLoc = new int[2];

    private float radiusDp = 26f;
    private int tint = 0;
    private boolean frosted = true;

    public GlassView(Context c) {
        super(c);
        setWillNotDraw(false);
        paint.setAntiAlias(true);
    }

    public GlassView(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        paint.setAntiAlias(true);
    }

    public GlassView radius(float dp) {
        radiusDp = dp;
        invalidate();
        return this;
    }

    public GlassView tint(int color) {
        tint = color;
        invalidate();
        return this;
    }

    public GlassView frosted(boolean f) {
        frosted = f;
        invalidate();
        return this;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            super.onDraw(canvas);
            return;
        }
        float r = UI.dp(getContext(), radiusDp);
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(clip);

        boolean drew = false;
        if (frosted && UI.sGlass != null) {
            try {
                getLocationOnScreen(loc);
                getRootView().getLocationOnScreen(rootLoc);
                float k = UI.sGlassScale;
                int bx = (int) ((loc[0] - rootLoc[0]) / k);
                int by = (int) ((loc[1] - rootLoc[1]) / k);
                int bw = Math.max(1, (int) (w / k));
                int bh = Math.max(1, (int) (h / k));
                // 夹到图片范围内，越界就当成透明
                if (bx + bw > UI.sGlass.getWidth()) bw = UI.sGlass.getWidth() - bx;
                if (by + bh > UI.sGlass.getHeight()) bh = UI.sGlass.getHeight() - by;
                if (bx >= 0 && by >= 0 && bw > 0 && bh > 0) {
                    Rect src = new Rect(bx, by, bx + bw, by + bh);
                    Rect dst = new Rect(0, 0, w, h);
                    canvas.drawBitmap(UI.sGlass, src, dst, paint);
                    drew = true;
                }
            } catch (Throwable ignored) { }
        }
        int base = tint != 0 ? tint : UI.barColor();
        canvas.drawColor(base);
        canvas.restoreToCount(save);
        super.onDraw(canvas);
    }
}
