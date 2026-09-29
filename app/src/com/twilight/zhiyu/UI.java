package com.twilight.zhiyu;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** 主题 + 毛玻璃 + 沉浸式 + 常用控件工厂。全自绘，不依赖 AndroidX。 */
public class UI {

    public static boolean dark = false;
    public static boolean glass = false;   // 毛玻璃

    public static int BG, CARD, CARD2, TEXT, SUB, ACCENT, ACCENT2,
            DIVIDER, INPUT_BG, ON_ACCENT, DANGER;

    static {
        theme(false);
    }

    public static void theme(boolean d) {
        dark = d;
        if (d) {
            BG = 0xFF101114;
            CARD = 0xFF1B1D21;
            CARD2 = 0xFF272A30;
            TEXT = 0xFFF1F2F5;
            SUB = 0xFF9BA1AA;
            ACCENT = 0xFFD8AE6A;
            ACCENT2 = 0xFFBE9147;
            DIVIDER = 0x1FFFFFFF;
            INPUT_BG = 0xFF272A30;
            ON_ACCENT = 0xFFFFFFFF;
            DANGER = 0xFFFF6B6B;
        } else {
            BG = 0xFFF6F7F9;
            CARD = 0xFFFFFFFF;
            CARD2 = 0xFFF0F1F5;
            TEXT = 0xFF17181B;
            SUB = 0xFF888E98;
            ACCENT = 0xFFB98C3E;
            ACCENT2 = 0xFF9C7331;
            DIVIDER = 0x14000000;
            INPUT_BG = 0xFFF0F1F5;
            ON_ACCENT = 0xFFFFFFFF;
            DANGER = 0xFFE5484D;
        }
    }

    /**
     * 面板底色：不做模糊了，统一「30% 黑」（浅色模式 30% 白）。
     * 星野的深色面板也是这个路子，简单统一、不挑背景。
     */
    public static int cardColor() {
        return dark ? 0x4D000000 : 0x4DFFFFFF;
    }

    /** 输入框 / 次级底色 */
    public static int inputColor() {
        return dark ? 0x4D000000 : 0x4DFFFFFF;
    }

    /** 浮层（胶囊导航、输入栏、顶栏）底色 */
    public static int barColor() {
        return dark ? 0x4D000000 : 0x4DFFFFFF;
    }

    /** 弹层（重说面板等）底色 */
    public static int sheetColor() {
        return dark ? 0xF20F1013 : 0xF2FFFFFF;
    }

    /** 弹层里比面板略亮的块 */
    public static int blockColor() {
        return dark ? 0xFF25262A : 0xFFF2F3F6;
    }

    /** 我自己的气泡：星野同款琥珀黄 */
    public static final int BUBBLE_ME = 0xFFFFCF86;
    public static final int BUBBLE_ME_TEXT = 0xFF2B2010;

    /** 毛玻璃已砍掉，这里保留接口但一律走普通圆角块 */
    public static LinearLayout frosted(Context c, float radiusDp, boolean vertical) {
        LinearLayout l = new LinearLayout(c);
        l.setBackground(bg(cardColor(), radiusDp, c));
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        return l;
    }

    // ---------------- 单位 ----------------

    public static int dp(Context c, float v) {
        return (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()) + 0.5f);
    }

    // ---------------- 背景 ----------------

    public static GradientDrawable bg(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable bgStroke(int color, float radiusDp, int strokeColor,
                                            float strokeDp, Context c) {
        GradientDrawable d = bg(color, radiusDp, c);
        d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }

    public static GradientDrawable gradient(float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT, ACCENT2});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /**
     * 调系统自带的裁剪界面（1:1）。返回 false 说明这台机器没有裁剪器，
     * 调用方应该退回自己的 CropActivity。
     *
     * 注意：这里**不能**用 resolveActivity() 来判断有没有裁剪器 ——
     * Android 11 以后不声明 &lt;queries&gt; 的话，它能看见也返回 null。
     * 直接开，开不起来就抛 ActivityNotFoundException，catch 掉即可。
     */
    public static boolean startSystemCrop(android.app.Activity a, android.net.Uri src, int req) {
        try {
            android.content.Intent i = new android.content.Intent("com.android.camera.action.CROP");
            i.setDataAndType(src, "image/*");
            i.putExtra("crop", "true");
            i.putExtra("aspectX", 1);
            i.putExtra("aspectY", 1);
            i.putExtra("outputX", 360);
            i.putExtra("outputY", 360);
            i.putExtra("return-data", true);
            i.putExtra("scale", true);
            i.putExtra("scaleUpIfNeeded", true);
            i.putExtra("noFaceDetection", true);
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivityForResult(i, req);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 从系统裁剪的返回里取出 Bitmap */
    public static Bitmap cropResult(android.content.Intent data) {
        if (data == null) return null;
        try {
            return data.getParcelableExtra("data");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 调系统自带的「头像选择器」（com.android.avatarpicker，AOSP 从 Android 14 起
     * 就带这个包，绝大部分机器都有）。它自带 拍照 / 选图 / 框选 一整套，
     * 返回一张 1:1 的头像。返回 false 说明这台机器没有这个组件。
     */
    public static boolean startAvatarPicker(android.app.Activity a, int req) {
        try {
            android.content.Intent i = new android.content.Intent(AVATAR_PICKER_ACTION);
            i.setComponent(new android.content.ComponentName(AVATAR_PICKER_PKG,
                    AVATAR_PICKER_PKG + ".ui.AvatarPickerActivity"));
            a.startActivityForResult(i, req);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static final String AVATAR_PICKER_ACTION = "com.android.avatarpicker.FULL_SCREEN_ACTIVITY";
    public static final String AVATAR_PICKER_PKG = "com.android.avatarpicker";

    /** 只圆上面两个角（底部弹层用） */    public static GradientDrawable bgTop(int color, float radiusDp, Context c) {
        float r = dp(c, radiusDp);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    // ---------------- 布局 ----------------

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View space(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        return v;
    }

    public static View hspace(Context c, int widthDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), 1));
        return v;
    }

    // ---------------- 文本 ----------------

    public static TextView tv(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    public static TextView title(Context c, CharSequence s) {
        return tv(c, s, 19, TEXT, true);
    }

    public static TextView label(Context c, CharSequence s) {
        TextView t = tv(c, s, 13, SUB, false);
        t.setPadding(dp(c, 2), 0, 0, dp(c, 6));
        return t;
    }

    // ---------------- 卡片 ----------------

    public static LinearLayout card(Context c, float radiusDp, int padDp) {
        LinearLayout l = frosted(c, radiusDp, true);
        int p = dp(c, padDp);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static TextView button(Context c, String text, boolean primary, float radiusDp) {
        TextView b = tv(c, text, 15.5f, primary ? ON_ACCENT : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        b.setBackground(primary ? gradient(radiusDp, c) : bg(inputColor(), radiusDp, c));
        b.setClickable(true);
        return b;
    }

    public static EditText input(Context c, String hint, int lines) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(TEXT);
        e.setHintTextColor(SUB);
        e.setBackground(bg(inputColor(), 14, c));
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        e.setLineSpacing(dp(c, 3), 1f);
        if (lines <= 1) {
            e.setSingleLine(true);
        } else {
            e.setMinLines(lines);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        return e;
    }

    /** 把输入框包进一个毛玻璃胶囊里（创建页/设置页的输入都用它） */
    public static LinearLayout wrapField(Context c, EditText e) {
        LinearLayout box = frosted(c, 18, true);
        e.setBackgroundColor(0x00000000);
        int p = dp(c, 13);
        box.setPadding(p, dp(c, 5), p, dp(c, 5));
        box.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    // ---------------- 对话框 ----------------

    public static Dialog dialog(Context c, View content) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = col(c);
        wrap.setBackground(bg(dark ? 0xFF1B1D21 : 0xFFFFFFFF, 20, c));
        int p = dp(c, 18);
        wrap.setPadding(p, p, p, p);
        wrap.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.88f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    public interface OnOk {
        void run(String text);
    }

    public static void prompt(Context c, String title, String hint,
                              String initial, final OnOk ok) {
        LinearLayout box = col(c);
        box.addView(title(c, title));
        box.addView(space(c, 12));
        final EditText e = input(c, hint, 3);
        e.setText(initial == null ? "" : initial);
        box.addView(e);
        box.addView(space(c, 16));
        LinearLayout btns = row(c);
        TextView cancel = button(c, "取消", false, 14);
        TextView confirm = button(c, "确定", true, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(c, 8);
        btns.addView(cancel, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(c, 8);
        btns.addView(confirm, lp2);
        box.addView(btns);

        final Dialog d = dialog(c, box);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); }
        });
        confirm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                if (ok != null) ok.run(e.getText().toString());
            }
        });
        d.show();
    }

    public interface OnConfirm {
        void run();
    }

    public static void confirm(Context c, String title, String message,
                               String okText, final OnConfirm ok) {
        LinearLayout box = col(c);
        box.addView(title(c, title));
        if (message != null && message.length() > 0) {
            box.addView(space(c, 8));
            box.addView(tv(c, message, 14, SUB, false));
        }
        box.addView(space(c, 16));
        LinearLayout btns = row(c);
        TextView cancel = button(c, "取消", false, 14);
        TextView confirm = button(c, okText, true, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(c, 8);
        btns.addView(cancel, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(c, 8);
        btns.addView(confirm, lp2);
        box.addView(btns);

        final Dialog d = dialog(c, box);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); }
        });
        confirm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                if (ok != null) ok.run();
            }
        });
        d.show();
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------- 图片 ----------------

    public static Bitmap loadBitmap(File f, int maxSize) {
        if (f == null || !f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            int big = Math.max(o.outWidth, o.outHeight);
            while (big / sample > maxSize * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (bmp == null) return null;
            int rot = 0;
            try {
                ExifInterface ex = new ExifInterface(f.getAbsolutePath());
                int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            } catch (Throwable ignored) { }
            if (rot != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (r != bmp) bmp.recycle();
                bmp = r;
            }
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap circle(Bitmap src) {
        if (src == null) return null;
        int s = Math.min(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP));
        float r = s / 2f;
        cv.drawCircle(r, r, r, p);
        return out;
    }

    public static Bitmap rounded(Bitmap src, float radiusPx) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(0, 0, w, h), radiusPx, radiusPx, p);
        return out;
    }

    public static Bitmap square(Bitmap src, int size) {
        if (src == null) return null;
        int s = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - s) / 2, y = (src.getHeight() - s) / 2;
        Bitmap c = Bitmap.createBitmap(src, x, y, s, s);
        if (c.getWidth() != size) {
            Bitmap r = Bitmap.createScaledBitmap(c, size, size, true);
            if (r != c) c.recycle();
            c = r;
        }
        return c;
    }

    // ---------------- 毛玻璃 / 沉浸式 ----------------

    /** 给任意 View 加高斯模糊（API 31+ 原生；低版本忽略） */
    public static void blur(View v, float radiusDp) {
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                float r = dp(v.getContext(), radiusDp);
                v.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) { }
        }
    }

    public static void clearBlur(View v) {
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 31) {
            try { v.setRenderEffect(null); } catch (Throwable ignored) { }
        }
    }

    /**
     * 让弹层背后做「真·实时模糊」。
     * 这是 Android 唯一公开的 backdrop blur 能力（只对 Window 有效，所以底部弹层能用）。
     */
    public static void blurBehind(Dialog d, int radiusDp) {
        if (d == null || Build.VERSION.SDK_INT < 31) return;
        Window w = d.getWindow();
        if (w == null) return;
        try {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            w.setBackgroundBlurRadius(Math.max(1, dp(d.getContext(), radiusDp)));
        } catch (Throwable ignored) { }
    }

    /** 让内容延伸到状态栏/导航栏后面，系统栏透明 */
    public static void applyWindow(Activity a) {
        Window w = a.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        View decor = w.getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= 0x00000010; // LIGHT_NAVIGATION_BAR
        }
        decor.setSystemUiVisibility(flags);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
    }

    /**
     * 把系统栏高度作为内边距加到 View 上，避免被系统栏压住。
     * useTop / useBottom 分别控制要不要吃状态栏 / 导航栏的高度。
     */
    public static void padSystemBars(final View v, final boolean useTop, final boolean useBottom,
                                     final int extraTopDp, final int extraBottomDp) {
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top = (useTop ? insets.getSystemWindowInsetTop() : 0)
                        + dp(view.getContext(), extraTopDp);
                int bottom = (useBottom ? insets.getSystemWindowInsetBottom() : 0)
                        + dp(view.getContext(), extraBottomDp);
                view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
                return insets;
            }
        });
        v.requestApplyInsets();
    }

    /** 只要顶部状态栏内边距 */
    public static void padTop(View v, int extraTopDp) {
        padSystemBars(v, true, false, extraTopDp, 0);
    }

    /** 只要底部导航栏内边距 */
    public static void padBottom(View v, int extraBottomDp) {
        padSystemBars(v, false, true, 0, extraBottomDp);
    }

    /** 图片背景：居中裁剪、不拉伸 */
    public static android.widget.ImageView backgroundImage(Context c, String name) {
        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    public static void fillBackground(android.widget.ImageView iv, File f, boolean blurIt) {
        Bitmap b = loadBitmap(f, 1200);
        if (b == null) {
            iv.setImageDrawable(null);
            clearBlur(iv);
            return;
        }
        // 加一层蒙版，保证字看得清
        Bitmap mut = b.copy(Bitmap.Config.ARGB_8888, true);
        if (mut != null) {
            Canvas cv = new Canvas(mut);
            cv.drawColor(dark ? (glass ? 0x66000000 : 0x99000000) : (glass ? 0x4DFFFFFF : 0x88FFFFFF));
            b = mut;
        }
        iv.setImageBitmap(b);
        if (blurIt) blur(iv, glass ? 22 : 0);
        else clearBlur(iv);
    }

    /**
     * 给一个页面挂上全屏背景图（含状态栏/导航栏后面），返回承载内容的 FrameLayout。
     * 用法：FrameLayout outer = UI.installBackground(this, bgFile); ... outer.addView(root);
     */
    public static FrameLayout installBackground(Context c, File f) {
        FrameLayout outer = new FrameLayout(c);
        outer.setBackgroundColor(BG);
        if (f != null && f.exists()) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            outer.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // 背景保持清晰；模糊只作用在胶囊面板上
            fillBackground(iv, f, false);
        }
        return outer;
    }

    public static void putContent(FrameLayout outer, View content) {
        outer.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- 玻璃采样源 ----------------

    /** 预先算好的小尺寸模糊背景，GlassView 从这里采样 */
    public static Bitmap sGlass = null;
    /** 屏幕像素 / 采样图像素 的比例 */
    public static float sGlassScale = 6f;

    /** 准备玻璃采样源（顺带从背景图里提取主题色） */
    public static void prepareGlass(Context c, File f) {
        sGlass = null;
        if (f == null || !f.exists()) return;
        try {
            Bitmap b = loadBitmap(f, 800);
            if (b == null) return;

            // 从「大致模糊后的背景图」里挑出现最多的颜色当主题色
            extractAccent(b);

            int sw = c.getResources().getDisplayMetrics().widthPixels;
            int sh = c.getResources().getDisplayMetrics().heightPixels;
            float target = (float) sw / sh;
            int bw = b.getWidth(), bh = b.getHeight();
            float aspect = (float) bw / bh;
            int cw, ch;
            if (aspect > target) { ch = bh; cw = Math.max(1, (int) (bh * target)); }
            else { cw = bw; ch = Math.max(1, (int) (bw / target)); }
            Bitmap crop = Bitmap.createBitmap(b, (bw - cw) / 2, (bh - ch) / 2, cw, ch);
            int dw = Math.max(120, sw / 3);
            int dh = Math.max(120, sh / 3);
            sGlass = boxBlur(Bitmap.createScaledBitmap(crop, dw, dh, true), 9, 2);
            sGlassScale = (float) sw / sGlass.getWidth();
        } catch (Throwable t) {
            sGlass = null;
        }
    }

    /** 主题色 = 背景图缩小模糊后出现最多的那个颜色 */
    public static void extractAccent(Bitmap src) {
        try {
            Bitmap small = Bitmap.createScaledBitmap(src, 40, 40, true);
            int w = small.getWidth(), h = small.getHeight();
            int[] pix = new int[w * h];
            small.getPixels(pix, 0, w, 0, 0, w, h);
            java.util.HashMap<Integer, Integer> count = new java.util.HashMap<Integer, Integer>();
            for (int c : pix) {
                int r = ((c >> 16) & 0xFF) & 0xF8;
                int g = ((c >> 8) & 0xFF) & 0xF8;
                int b = (c & 0xFF) & 0xF8;
                int lum = (r * 30 + g * 59 + b * 11) / 100;
                if (lum < 40 || lum > 225) continue;      // 太黑太白的不算
                int key = (r << 16) | (g << 8) | b;
                Integer n = count.get(key);
                count.put(key, n == null ? 1 : n + 1);
            }
            int best = 0, bestN = 0;
            for (java.util.Map.Entry<Integer, Integer> e : count.entrySet()) {
                if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
            }
            if (bestN > 0) {
                ACCENT = 0xFF000000 | best;
                ACCENT2 = 0xFF000000 | darken(best, 0.78f);
            }
        } catch (Throwable ignored) { }
    }

    private static int darken(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 0xFF) * k);
        int g = (int) (((rgb >> 8) & 0xFF) * k);
        int b = (int) ((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** 简单的可分离盒式模糊，跑在很小的图上，开销可忽略 */
    private static Bitmap boxBlur(Bitmap src, int radius, int passes) {
        int w = src.getWidth(), h = src.getHeight();
        int[] pix = new int[w * h];
        src.getPixels(pix, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];
        for (int p = 0; p < passes; p++) {
            blurPass(pix, tmp, w, h, radius);
            blurPass(tmp, pix, h, w, radius);   // 转置跑一遍 = 纵向
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pix, 0, w, 0, 0, w, h);
        return out;
    }

    /** 横向模糊；transposed 时把 (x,y) 读成 (y,x)，等于纵向 */
    private static void blurPass(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int a = 0, rr = 0, gg = 0, bb = 0;
            for (int i = -r; i <= r; i++) {
                int x = Math.max(0, Math.min(w - 1, i));
                int c = in[y * w + x];
                a += (c >>> 24) & 0xFF; rr += (c >> 16) & 0xFF;
                gg += (c >> 8) & 0xFF; bb += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[y * w + x] = ((a / div) << 24) | ((rr / div) << 16)
                        | ((gg / div) << 8) | (bb / div);
                int addX = Math.min(w - 1, x + r + 1);
                int subX = Math.max(0, x - r);
                int ca = in[y * w + addX], cs = in[y * w + subX];
                a += ((ca >>> 24) & 0xFF) - ((cs >>> 24) & 0xFF);
                rr += ((ca >> 16) & 0xFF) - ((cs >> 16) & 0xFF);
                gg += ((ca >> 8) & 0xFF) - ((cs >> 8) & 0xFF);
                bb += (ca & 0xFF) - (cs & 0xFF);
            }
        }
    }
}
