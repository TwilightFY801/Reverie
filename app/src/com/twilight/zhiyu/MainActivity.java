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
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/** 外壳：底部三个入口 —— 创建 / 导入 / 我的 */
public class MainActivity extends Activity {

    private static final int REQ_IMPORT = 2001;
    private static final int REQ_EXPORT = 2002;
    private static final int REQ_EXPORT_CHAT = 2003;

    private Store store;
    private FrameLayout content;
    private ImageView bgView;
    private LinearLayout navBar;
    private GlassView navPillRef;
    private final TextView[] nav = new TextView[3];
    private AgentFormView formView;
    private int tab = 2;
    private Model.Agent exporting;
    /** 当前界面是按哪个状态建的（用来判断要不要重建，别拿全局 UI.glass 比） */
    private boolean builtDark, builtGlass;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = Store.get(this);
        UI.theme(store.dark);
        UI.glass = store.glass;
        builtDark = store.dark;
        builtGlass = store.glass;
        UI.applyWindow(this);
        // 必须「先」从背景图提主题色，再建界面 —— 否则导航栏会用旧的默认色画完
        UI.prepareGlass(this, store.imageFile(store.bgImage));
        buildShell();
        refreshBackground();
        showTab(2);
    }

    private void buildShell() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(UI.BG);

        // 背景图：铺满整屏（含状态栏/导航栏后面），居中裁剪不拉伸
        bgView = new ImageView(this);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        outer.addView(bgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = UI.col(this);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部导航：悬浮胶囊
        FrameLayout navWrap = new FrameLayout(this);
        navWrap.setPadding(UI.dp(this, 20), 0, UI.dp(this, 20), 0);
        GlassView navPill = null;
        LinearLayout navPillL = UI.row(this);
        navPillL.setBackground(UI.bg(UI.barColor(), 30, this));
        navBar = navPillL;
        navBar.setPadding(UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6));
        String[] labels = {"创建", "导入", "我的"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = UI.tv(this, labels[i], 14.5f, UI.SUB, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 11), 0, UI.dp(this, 11));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (idx == 1) openImport();
                    else showTab(idx);
                }
            });
            nav[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = UI.dp(this, 3);
            lp.rightMargin = UI.dp(this, 3);
            navBar.addView(t, lp);
        }
        UI.padBottom(navWrap, 12);
        navWrap.addView(navBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        outer.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 内容顶部让开状态栏（背景图仍然铺到状态栏后面）
        UI.padTop(root, 0);
        setContentView(outer);
    }

    /** 背景图（保持清晰；模糊只做在胶囊面板上） */
    private void refreshBackground() {
        File f = store.imageFile(store.bgImage);
        if (f != null && f.exists()) {
            UI.fillBackground(bgView, f, false);
            UI.prepareGlass(this, f);
        } else {
            bgView.setImageDrawable(null);
            bgView.setBackgroundColor(UI.BG);
            UI.clearBlur(bgView);
            UI.prepareGlass(this, null);
        }
    }

    private void paintNav() {
        for (int i = 0; i < 3; i++) {
            boolean on = (i == tab);
            nav[i].setTextColor(on ? UI.ON_ACCENT : UI.SUB);
            nav[i].setBackground(on ? UI.gradient(24, this)
                    : UI.bg(0x00000000, 24, this));
        }
    }

    private void showTab(int i) {
        tab = i;
        paintNav();
        content.removeAllViews();
        View v;
        if (i == 0) {
            if (formView == null) {
                formView = new AgentFormView(this, null, new AgentFormView.Callback() {
                    public void onSaved(Model.Agent a) {
                        formView = null;
                        UI.toast(MainActivity.this, "已创建「" + a.name + "」");
                        showTab(2);
                    }
                });
            }
            v = formView;
        } else {
            formView = null;
            v = buildMine();
        }
        content.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- 我的 ----------------

    private View buildMine() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = UI.col(this);
        int p = UI.dp(this, 18);
        root.setPadding(p, UI.dp(this, 16), p, UI.dp(this, 28));

        // 顶栏：标题 + 设置
        LinearLayout bar = UI.row(this);
        bar.addView(UI.title(this, "我的"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView settings = UI.tv(this, "设置", 14, UI.TEXT, true);
        settings.setPadding(UI.dp(this, 16), UI.dp(this, 8), UI.dp(this, 16), UI.dp(this, 8));
        settings.setBackground(UI.bg(UI.cardColor(), 18, this));
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        bar.addView(settings);
        root.addView(bar);
        root.addView(UI.space(this, 18));

        // 用户卡片
        LinearLayout card = UI.card(this, 18, 18);
        LinearLayout top = UI.row(this);
        ImageView av = new ImageView(this);
        int size = UI.dp(this, 64);
        top.addView(av, new LinearLayout.LayoutParams(size, size));
        top.addView(UI.hspace(this, 14));
        LinearLayout info = UI.col(this);
        String uname = store.profile.name.length() == 0 ? "还没设置名字" : store.profile.name;
        info.addView(UI.tv(this, uname, 17, UI.TEXT, true));
        info.addView(UI.space(this, 3));
        String sub = store.profile.gender;
        if (store.profile.bio.length() > 0) sub = sub + " · " + store.profile.bio;
        TextView subTv = UI.tv(this, sub, 12.5f, UI.SUB, false);
        subTv.setMaxLines(2);
        info.addView(subTv);
        top.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        Bitmap ab = UI.loadBitmap(store.imageFile(store.profile.avatar), 256);
        if (ab != null) av.setImageBitmap(UI.circle(UI.square(ab, 200)));
        else av.setBackground(UI.bg(UI.inputColor(), 32, this));

        card.addView(UI.space(this, 16));
        TextView editProfile = UI.button(this, "更改角色全局设定", false, 14);
        editProfile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ProfileEditActivity.class));
            }
        });
        card.addView(editProfile);
        root.addView(card);
        root.addView(UI.space(this, 24));

        // 我的智能体
        LinearLayout head = UI.row(this);
        head.addView(UI.tv(this, "我的智能体", 15.5f, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(UI.tv(this, store.agents.size() + " 个", 12.5f, UI.SUB, false));
        root.addView(head);
        root.addView(UI.space(this, 12));

        if (store.agents.isEmpty()) {
            LinearLayout empty = UI.card(this, 18, 22);
            TextView t = UI.tv(this, "还没有角色。\n去「创建」捏一个，或者用「导入」读别人分享的压缩包。",
                    13.5f, UI.SUB, false);
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            root.addView(empty);
        } else {
            for (final Model.Agent a : store.agents) {
                root.addView(agentRow(a));
                root.addView(UI.space(this, 10));
            }
        }

        sv.addView(root);
        return sv;
    }

    private View agentRow(final Model.Agent a) {
        LinearLayout row = UI.frosted(this, 22, false);
        int p = UI.dp(this, 14);
        row.setPadding(p, p, p, p);

        ImageView av = new ImageView(this);
        int size = UI.dp(this, 54);
        Bitmap b = UI.loadBitmap(store.imageFile(a.avatar), 256);
        if (b != null) av.setImageBitmap(UI.circle(UI.square(b, 200)));
        else av.setBackground(UI.bg(UI.inputColor(), 27, this));
        row.addView(av, new LinearLayout.LayoutParams(size, size));
        row.addView(UI.hspace(this, 13));

        LinearLayout info = UI.col(this);
        info.addView(UI.tv(this, a.name, 15.5f, UI.TEXT, true));
        String line = a.bio.length() > 0 ? a.bio : (a.memo.length() > 0 ? a.memo : a.gender);
        TextView s = UI.tv(this, line, 12.5f, UI.SUB, false);
        s.setMaxLines(2);
        info.addView(s);
        row.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_AGENT, a.id);
                startActivity(i);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                agentMenu(a);
                return true;
            }
        });
        return row;
    }

    private void agentMenu(final Model.Agent a) {
        LinearLayout box = UI.col(this);
        box.addView(UI.title(this, a.name));
        box.addView(UI.space(this, 14));
        String[] items = {"编辑", "导出为压缩包", "导出聊天记录", "删除"};
        View.OnClickListener[] acts = new View.OnClickListener[]{
                new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent i = new Intent(MainActivity.this, CreateActivity.class);
                        i.putExtra(CreateActivity.EXTRA_AGENT, a.id);
                        startActivity(i);
                    }
                },
                new View.OnClickListener() {
                    public void onClick(View v) { startExport(a, false); }
                },
                new View.OnClickListener() {
                    public void onClick(View v) { startExport(a, true); }
                },
                new View.OnClickListener() {
                    public void onClick(View v) {
                        UI.confirm(MainActivity.this, "删除「" + a.name + "」？",
                                "只删本机上的这一份，不可恢复。", "删除",
                                new UI.OnConfirm() {
                                    public void run() {
                                        store.removeAgent(a.id);
                                        showTab(2);
                                    }
                                });
                    }
                }
        };
        final android.app.Dialog d = UI.dialog(this, box);
        for (int i = 0; i < items.length; i++) {
            final View.OnClickListener act = acts[i];
            TextView t = UI.tv(this, items[i], 15.5f,
                    i == items.length - 1 ? UI.DANGER : UI.TEXT, false);
            t.setPadding(UI.dp(this, 4), UI.dp(this, 13), UI.dp(this, 4), UI.dp(this, 13));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    d.dismiss();
                    act.onClick(v);
                }
            });
            box.addView(t);
        }
        d.show();
    }

    // ---------------- 导入 / 导出 ----------------

    private void openImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed",
                        "application/octet-stream"});
        startActivityForResult(i, REQ_IMPORT);
    }

    private boolean exportingChat = false;

    private void startExport(Model.Agent a, boolean chatOnly) {
        exporting = a;
        exportingChat = chatOnly;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        String base = a.name.length() == 0 ? "agent" : a.name;
        i.putExtra(Intent.EXTRA_TITLE, chatOnly ? base + "-聊天记录.zip" : base + ".zip");
        startActivityForResult(i, chatOnly ? REQ_EXPORT_CHAT : REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (formView != null) formView.onActivityResult(req, res, data);

        if (req == REQ_IMPORT && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                ZipPack.Result r = ZipPack.read(this, store, in);
                if (in != null) in.close();
                if (r.error != null) { UI.toast(this, r.error); return; }
                Model.Agent a = r.agent;
                store.putAgent(a);
                Intent i = new Intent(this, CreateActivity.class);
                i.putExtra(CreateActivity.EXTRA_AGENT, a.id);
                i.putExtra(CreateActivity.EXTRA_JUST_IMPORTED, true);
                startActivity(i);
            } catch (Throwable t) {
                UI.toast(this, "导入失败：" + t.getMessage());
            }
        } else if ((req == REQ_EXPORT || req == REQ_EXPORT_CHAT)
                && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null || exporting == null) return;
            try {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (exportingChat) {
                    int n = ZipPack.exportChat(this, store, exporting, os);
                    UI.toast(this, n == 0 ? "还没有聊天记录" : ("已导出 " + n + " 条对话"));
                } else {
                    ZipPack.export(this, store, exporting, os);
                    UI.toast(this, "已导出「" + exporting.name + "」");
                }
                if (os != null) { os.flush(); os.close(); }
            } catch (Throwable t) {
                UI.toast(this, "导出失败：" + t.getMessage());
            } finally {
                exporting = null;
                exportingChat = false;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 拿「建界面时的状态」跟当前比，不能用 UI.glass（设置页会把它改掉）
        boolean needRebuild = (builtDark != store.dark) || (builtGlass != store.glass);
        if (needRebuild) {
            UI.theme(store.dark);
            UI.glass = store.glass;
            builtDark = store.dark;
            builtGlass = store.glass;
            UI.applyWindow(this);
            UI.prepareGlass(this, store.imageFile(store.bgImage));
            formView = null;
            buildShell();
            refreshBackground();
            showTab(tab);
        } else {
            refreshBackground();
            if (tab == 2) showTab(2);
        }
    }
}
