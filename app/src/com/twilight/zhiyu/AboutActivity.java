package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 关于：图标 + 名字 + 版本 + 制作人 + 三个小链接 */
public class AboutActivity extends Activity {

    private static final String URL_BILI = "https://b23.tv/1lRGpQY";
    private static final String URL_GITHUB = "https://github.com/TwilightFY801/Reverie";
    private static final String URL_QQ_WEB = "https://qm.qq.com/q/ZcnrnHSLaW";

    private static final String PKG_BILI = "tv.danmaku.bili";
    private static final String PKG_QQ = "com.tencent.mobileqq";

    /** QQ 不认 qm.qq.com 这个网页地址，只能走它自己的协议；这个群卡片链接实测能直接唤起 QQ */
    private static final String URI_QQ_APP =
            "mqqapi://card/show_pslcard?src_type=internal&version=1"
                    + "&uin=808148160&card_type=group&source=qrcode";

    private Store store;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = Store.get(this);
        UI.theme(store.dark);
        UI.glass = store.glass;
        UI.prepareGlass(this, store.imageFile(store.bgImage));
        UI.applyWindow(this);
        build();
    }

    private void build() {
        FrameLayout outer = UI.installBackground(this, store.imageFile(store.bgImage));
        LinearLayout root = UI.col(this);

        // ---- 顶栏 ----
        LinearLayout bar = UI.row(this);
        int p = UI.dp(this, 16);
        bar.setPadding(p, UI.dp(this, 14), p, UI.dp(this, 10));
        TextView back = UI.tv(this, "‹ 返回", 15.5f, UI.ACCENT, false);
        back.setPadding(0, UI.dp(this, 6), UI.dp(this, 10), UI.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(back);
        bar.addView(UI.tv(this, "关于", 18, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        UI.padTop(bar, 14);

        // ---- 中间：图标 / 名字 / 版本 / 制作人 ----
        LinearLayout mid = UI.col(this);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        mid.setPadding(p, 0, p, 0);
        // 上面留一点、下面留多一点 —— 整块往上挪，不然太居中了
        mid.addView(UI.space(this, 0), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.62f));

        LinearLayout panel = UI.col(this);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackground(UI.bg(UI.cardColor(), 24, this));
        panel.setPadding(UI.dp(this, 34), UI.dp(this, 26),
                UI.dp(this, 34), UI.dp(this, 26));

        int iconPx = UI.dp(this, 104);
        Bitmap icon = appIcon(iconPx);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(icon);
            panel.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
            panel.addView(UI.space(this, 18));
        }

        TextView name = UI.tv(this, "遐语", 26, UI.TEXT, true);
        name.setGravity(Gravity.CENTER);
        panel.addView(name);
        panel.addView(UI.space(this, 7));

        TextView ver = UI.tv(this, "版本 " + versionName(), 13, UI.SUB, false);
        ver.setGravity(Gravity.CENTER);
        panel.addView(ver);
        panel.addView(UI.space(this, 24));

        TextView maker = UI.tv(this, "制作人：Twilight飞友", 14, UI.SUB, false);
        maker.setGravity(Gravity.CENTER);
        panel.addView(maker);

        // 宽度必须给 MATCH_PARENT：wrap_content 会按图标宽度定死，
        // 下面那行「制作人：Twilight飞友」就被裁掉了。
        mid.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mid.addView(UI.space(this, 0), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(mid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- 最底下三个很小的字 ----
        LinearLayout links = UI.row(this);
        links.setGravity(Gravity.CENTER);
        links.setBackground(UI.bg(UI.cardColor(), 999, this));
        links.setPadding(UI.dp(this, 14), UI.dp(this, 7),
                UI.dp(this, 14), UI.dp(this, 7));
        links.addView(tinyLink("哔哩哔哩", PKG_BILI, URL_BILI, URL_BILI));
        links.addView(UI.hspace(this, 22));
        links.addView(tinyLink("Github", null, URL_GITHUB, URL_GITHUB));
        links.addView(UI.hspace(this, 22));
        links.addView(tinyLink("QQ", PKG_QQ, URI_QQ_APP, URL_QQ_WEB));

        LinearLayout bottom = UI.col(this);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(p, UI.dp(this, 12), p, UI.dp(this, 14));
        bottom.addView(links);
        root.addView(bottom);
        UI.padBottom(bottom, 8);

        UI.putContent(outer, root);
        setContentView(outer);
    }

    /** 又小又不起眼，但点得着 */
    private TextView tinyLink(final String label, final String pkg,
                              final String appUri, final String webUri) {
        TextView t = UI.tv(this, label, 11, UI.SUB, false);
        int hx = UI.dp(this, 8), hy = UI.dp(this, 6);
        t.setPadding(hx, hy, hx, hy);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { open(pkg, appUri, webUri); }
        });
        return t;
    }

    /**
     * 装了对应 App 就在 App 里打开，没装（或者 App 不认这个地址）就丢给浏览器。
     * pkg 传 null 表示这条永远走浏览器。
     */
    private void open(String pkg, String appUri, String webUri) {
        if (pkg != null) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(appUri));
                i.setPackage(pkg);
                startActivity(i);
                return;
            } catch (Throwable ignored) {
                // App 没装 / 不认这个地址，往下走浏览器
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUri)));
        } catch (Throwable t) {
            UI.toast(this, "手机上找不到能打开这个链接的应用");
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "—";
        }
    }

    /** 软件图标 → 圆角正方形位图 */
    private Bitmap appIcon(int size) {
        try {
            Drawable d = getPackageManager().getApplicationIcon(getPackageName());
            if (d == null) return null;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(cv);
            return UI.rounded(bmp, size * 0.22f);
        } catch (Throwable t) {
            return null;
        }
    }
}
