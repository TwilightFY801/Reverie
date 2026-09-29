package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 在一张大图里框选 1:1 头像。
 *  入参：EXTRA_IMAGE = 图片文件名
 *  出参：RESULT 里返回 EXTRA_OUT = 裁剪出的头像文件名
 */
public class CropActivity extends Activity {

    public static final String EXTRA_IMAGE = "image";
    public static final String EXTRA_OUT = "out";
    private static final int OUT_SIZE = 512;

    private CropView cropView;
    private Bitmap src;
    private String srcName;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UI.theme(Store.get(this).dark);
        UI.glass = Store.get(this).glass;
        UI.applyWindow(this);

        srcName = getIntent().getStringExtra(EXTRA_IMAGE);
        File f = Store.get(this).imageFile(srcName);
        src = UI.loadBitmap(f, 1600);

        LinearLayout root = UI.col(this);
        root.setBackgroundColor(UI.BG);

        // 顶部条
        LinearLayout bar = UI.row(this);
        int p = UI.dp(this, 14);
        bar.setPadding(p, UI.dp(this, 14), p, UI.dp(this, 10));
        TextView cancel = UI.tv(this, "取消", 16, UI.SUB, false);
        cancel.setPadding(UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6));
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(cancel);
        TextView title = UI.tv(this, "框选头像", 17, UI.TEXT, true);
        title.setGravity(android.view.Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ok = UI.tv(this, "确定", 16, UI.ACCENT, true);
        ok.setPadding(UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6));
        bar.addView(ok);
        root.addView(bar);

        if (src == null) {
            TextView t = UI.tv(this, "图片读取失败", 15, UI.SUB, false);
            t.setGravity(android.view.Gravity.CENTER);
            root.addView(t, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            FrameLayout holder = new FrameLayout(this);
            cropView = new CropView(this, src);
            holder.addView(cropView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(holder, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            TextView hint = UI.tv(this, "拖动方框选位置，拉四角改大小", 13, UI.SUB, false);
            hint.setGravity(android.view.Gravity.CENTER);
            hint.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 18));
            root.addView(hint);

            ok.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { doCrop(); }
            });
        }
        setContentView(root);
    }

    private void doCrop() {
        if (cropView == null || src == null) { finish(); return; }
        Rect r = cropView.sourceRect();
        if (r == null || r.width() <= 0 || r.height() <= 0) { finish(); return; }
        try {
            Bitmap cut = Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height());
            Bitmap out = Bitmap.createScaledBitmap(cut, OUT_SIZE, OUT_SIZE, true);
            String name = Store.newImageName();
            File dst = new File(Store.get(this).imagesDir(), name);
            FileOutputStream os = new FileOutputStream(dst);
            out.compress(Bitmap.CompressFormat.JPEG, 92, os);
            os.flush();
            os.close();
            Intent data = new Intent();
            data.putExtra(EXTRA_OUT, name);
            setResult(RESULT_OK, data);
            finish();
        } catch (Throwable t) {
            UI.toast(this, "裁剪失败：" + t.getMessage());
        }
    }

    /** 显示大图并在上面盖一个可拖拽/缩放的 1:1 方框 */
    static class CropView extends View {

        private final Bitmap bmp;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dim = new Paint();
        private final RectF box = new RectF();
        private final RectF startBox = new RectF();
        private float scale = 1f, dx = 0f, dy = 0f;
        private int mode = 0;   // 0 无 1 移动 2~5 四角
        private float lastX, lastY;
        private final float handle;

        CropView(Context c, Bitmap b) {
            super(c);
            bmp = b;
            handle = UI.dp(c, 28);
            dim.setColor(0x99000000);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(UI.dp(c, 2));
            paint.setColor(Color.WHITE);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float s = Math.min((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
            scale = s;
            dx = (w - bmp.getWidth() * s) / 2f;
            dy = (h - bmp.getHeight() * s) / 2f;
            float side = Math.min(w, h) * 0.78f;
            box.set((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f);
        }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawColor(0xFF000000);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postScale(scale, scale);
            m.postTranslate(dx, dy);
            cv.drawBitmap(bmp, m, null);

            // 四周压暗
            float w = getWidth(), h = getHeight();
            cv.drawRect(0, 0, w, box.top, dim);
            cv.drawRect(0, box.bottom, w, h, dim);
            cv.drawRect(0, box.top, box.left, box.bottom, dim);
            cv.drawRect(box.right, box.top, w, box.bottom, dim);

            cv.drawRect(box, paint);
            // 四角加粗
            float L = Math.min(UI.dp(getContext(), 22), box.width() / 3f);
            Paint p2 = new Paint(paint);
            p2.setStrokeWidth(UI.dp(getContext(), 4));
            cv.drawLine(box.left, box.top, box.left + L, box.top, p2);
            cv.drawLine(box.left, box.top, box.left, box.top + L, p2);
            cv.drawLine(box.right, box.top, box.right - L, box.top, p2);
            cv.drawLine(box.right, box.top, box.right, box.top + L, p2);
            cv.drawLine(box.left, box.bottom, box.left + L, box.bottom, p2);
            cv.drawLine(box.left, box.bottom, box.left, box.bottom - L, p2);
            cv.drawLine(box.right, box.bottom, box.right - L, box.bottom, p2);
            cv.drawLine(box.right, box.bottom, box.right, box.bottom - L, p2);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    lastX = x; lastY = y;
                    startBox.set(box);
                    mode = hitTest(x, y);
                    return mode != 0;
                }
                case MotionEvent.ACTION_MOVE: {
                    float ddx = x - lastX, ddy = y - lastY;
                    if (mode == 1) {
                        box.offset(ddx, ddy);
                    } else if (mode >= 2) {
                        float nx1 = startBox.left, ny1 = startBox.top,
                                nx2 = startBox.right, ny2 = startBox.bottom;
                        if (mode == 2) { nx1 += ddx; ny1 += ddy; }
                        else if (mode == 3) { nx2 += ddx; ny1 += ddy; }
                        else if (mode == 4) { nx1 += ddx; ny2 += ddy; }
                        else { nx2 += ddx; ny2 += ddy; }
                        float side = Math.max(UI.dp(getContext(), 60),
                                Math.min(Math.abs(nx2 - nx1), Math.abs(ny2 - ny1)));
                        boolean left = (mode == 2 || mode == 4);
                        boolean top = (mode == 2 || mode == 3);
                        box.set(left ? nx2 - side : nx1, top ? ny2 - side : ny1,
                                left ? nx2 : nx1 + side, top ? ny2 : ny1 + side);
                    }
                    clamp();
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mode = 0;
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private int hitTest(float x, float y) {
            float t = handle;
            if (near(x, y, box.left, box.top, t)) return 2;
            if (near(x, y, box.right, box.top, t)) return 3;
            if (near(x, y, box.left, box.bottom, t)) return 4;
            if (near(x, y, box.right, box.bottom, t)) return 5;
            if (x > box.left && x < box.right && y > box.top && y < box.bottom) return 1;
            return 0;
        }

        private boolean near(float x, float y, float cx, float cy, float t) {
            return Math.abs(x - cx) < t && Math.abs(y - cy) < t;
        }

        private void clamp() {
            float w = getWidth(), h = getHeight();
            float side = Math.min(box.width(), box.height());
            if (box.left < 0) { box.left = 0; box.right = side; }
            if (box.top < 0) { box.top = 0; box.bottom = side; }
            if (box.right > w) { box.right = w; box.left = w - side; }
            if (box.bottom > h) { box.bottom = h; box.top = h - side; }
        }

        /** 把视图坐标里的方框换算成原图上的像素区域 */
        Rect sourceRect() {
            int l = Math.round((box.left - dx) / scale);
            int t = Math.round((box.top - dy) / scale);
            int r = Math.round((box.right - dx) / scale);
            int bo = Math.round((box.bottom - dy) / scale);
            l = Math.max(0, l);
            t = Math.max(0, t);
            r = Math.min(bmp.getWidth(), r);
            bo = Math.min(bmp.getHeight(), bo);
            if (r - l < 8 || bo - t < 8) return null;
            int side = Math.min(r - l, bo - t);
            return new Rect(l, t, l + side, t + side);
        }
    }
}
