package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;

/** 设置：深浅色、自定义背景、API 管理 */
public class SettingsActivity extends Activity {

    private static final int REQ_BG = 3001;
    private static final int REQ_R18 = 3002;
    private Store store;
    private ImageView bgPreview;

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

        // 顶栏
        LinearLayout bar = UI.row(this);
        int p = UI.dp(this, 16);
        bar.setPadding(p, UI.dp(this, 14), p, UI.dp(this, 10));
        TextView back = UI.tv(this, "‹ 返回", 15.5f, UI.ACCENT, false);
        back.setPadding(0, UI.dp(this, 6), UI.dp(this, 10), UI.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(back);
        bar.addView(UI.tv(this, "设置", 18, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        UI.padTop(bar, 14);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = UI.col(this);
        col.setPadding(p, UI.dp(this, 6), p, UI.dp(this, 30));

        // 深色模式
        LinearLayout card1 = UI.card(this, 18, 4);
        LinearLayout r1 = UI.row(this);
        r1.setPadding(UI.dp(this, 12), UI.dp(this, 12), UI.dp(this, 12), UI.dp(this, 12));
        r1.addView(UI.tv(this, "深色模式", 15.5f, UI.TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(store.dark);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                store.dark = v;
                store.save();
                UI.theme(v);
                recreate();
            }
        });
        r1.addView(sw);
        card1.addView(r1);

        // R18+
        View line = new View(this);
        line.setBackgroundColor(UI.DIVIDER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 1));
        llp.leftMargin = UI.dp(this, 12);
        llp.rightMargin = UI.dp(this, 12);
        card1.addView(line, llp);

        LinearLayout r1c = UI.row(this);
        r1c.setPadding(UI.dp(this, 12), UI.dp(this, 12), UI.dp(this, 12), UI.dp(this, 12));
        LinearLayout rtext = UI.col(this);
        rtext.addView(UI.tv(this, "开启 R18+", 15.5f, UI.TEXT, false));
        rtext.addView(UI.tv(this, "开启后聊天会话会根据剧情出现18加内容", 12, UI.SUB, false));
        r1c.addView(rtext, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch r18sw = new Switch(this);
        r18sw.setChecked(store.r18);
        r18sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                if (v == store.r18) return;
                if (!v) {                       // 关掉：直接生效
                    store.r18 = false;
                    store.save();
                    return;
                }
                b.setChecked(false);            // 先退回去，读完声明再说
                startActivityForResult(new Intent(SettingsActivity.this, R18Activity.class), REQ_R18);
            }
        });
        r1c.addView(r18sw);
        // 整行都能点（开关本身太小不好按）
        r1c.setClickable(true);
        r1c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (store.r18) {
                    store.r18 = false;
                    store.save();
                    r18sw.setChecked(false);
                    UI.toast(SettingsActivity.this, "已关闭 R18+");
                } else {
                    startActivityForResult(
                            new Intent(SettingsActivity.this, R18Activity.class), REQ_R18);
                }
            }
        });
        card1.addView(r1c);

        col.addView(card1);
        col.addView(UI.space(this, 12));

        // 自定义背景
        LinearLayout card2 = UI.card(this, 18, 16);
        card2.addView(UI.tv(this, "聊天背景", 15.5f, UI.TEXT, true));
        card2.addView(UI.space(this, 4));
        card2.addView(UI.tv(this, "选一张图做整个软件的背景", 12.5f, UI.SUB, false));
        card2.addView(UI.space(this, 12));
        bgPreview = new ImageView(this);
        int h = UI.dp(this, 110);
        bgPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card2.addView(bgPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));
        card2.addView(UI.space(this, 12));
        LinearLayout btns = UI.row(this);
        TextView pick = UI.button(this, "选择图片", false, 12);
        pick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickBg(); }
        });
        TextView clear = UI.button(this, "清除", false, 12);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                store.bgImage = "";
                store.save();
                refreshBg();
            }
        });
        btns.addView(pick, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btns.addView(UI.hspace(this, 10));
        btns.addView(clear, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card2.addView(btns);
        col.addView(card2);
        col.addView(UI.space(this, 12));

        // API 管理
        LinearLayout card3 = UI.card(this, 18, 4);
        LinearLayout apiRow = UI.row(this);
        apiRow.setPadding(UI.dp(this, 12), UI.dp(this, 14), UI.dp(this, 12), UI.dp(this, 14));
        LinearLayout apiText = UI.col(this);
        apiText.addView(UI.tv(this, "API 管理", 15.5f, UI.TEXT, false));
        Model.Api cur = store.currentApi();
        apiText.addView(UI.tv(this, "当前：" + (cur == null ? "未配置" : cur.name),
                12.5f, UI.SUB, false));
        apiRow.addView(apiText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        apiRow.addView(UI.tv(this, "›", 20, UI.SUB, false));
        apiRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ApiActivity.class));
            }
        });
        card3.addView(apiRow);
        col.addView(card3);
        col.addView(UI.space(this, 12));

        // 关于
        LinearLayout card4 = UI.card(this, 18, 4);
        LinearLayout aboutRow = UI.row(this);
        aboutRow.setPadding(UI.dp(this, 12), UI.dp(this, 14), UI.dp(this, 12), UI.dp(this, 14));
        LinearLayout aboutText = UI.col(this);
        aboutText.addView(UI.tv(this, "关于", 15.5f, UI.TEXT, false));
        aboutText.addView(UI.tv(this, "遐语 · 本地优先的角色聊天", 12.5f, UI.SUB, false));
        aboutRow.addView(aboutText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        aboutRow.addView(UI.tv(this, "›", 20, UI.SUB, false));
        aboutRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
            }
        });
        card4.addView(aboutRow);
        col.addView(card4);

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        UI.putContent(outer, root);
        setContentView(outer);
        refreshBg();
    }

    private void refreshBg() {
        Bitmap b = UI.loadBitmap(store.imageFile(store.bgImage), 800);
        if (b != null) {
            bgPreview.setImageBitmap(b);
            bgPreview.setBackground(null);
        } else {
            bgPreview.setImageDrawable(null);
            bgPreview.setBackground(UI.bg(UI.CARD2, 14, this));
        }
    }

    private void pickBg() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_BG);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_R18) {
            if (res == RESULT_OK) {
                store.r18 = true;
                store.save();
                UI.toast(this, "已开启 R18+");
            } else {
                UI.toast(this, "没有开启");
            }
            recreate();
            return;
        }
        if (req == REQ_BG && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            String name = store.importImage(uri);
            if (name.length() == 0) { UI.toast(this, "读取图片失败"); return; }
            if (store.bgImage.length() > 0) store.deleteImage(store.bgImage);
            store.bgImage = name;
            store.save();
            refreshBg();
            UI.toast(this, "背景已更新");
        }
    }
}
