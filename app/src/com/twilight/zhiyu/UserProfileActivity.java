package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 用户设定：全局设定 / 这个角色专用的设定 */
public class UserProfileActivity extends Activity {

    public static final String EXTRA_AGENT = "agent";
    private Store store;
    private Model.Agent agent;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = Store.get(this);
        UI.theme(store.dark);
        UI.glass = store.glass;
        UI.prepareGlass(this, store.imageFile(store.bgImage));
        UI.applyWindow(this);

        String id = getIntent().getStringExtra(EXTRA_AGENT);
        agent = id == null ? null : store.findAgent(id);
        if (agent == null) { finish(); return; }
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        FrameLayout outer = UI.installBackground(this, store.imageFile(store.bgImage));
        LinearLayout root = UI.col(this);

        LinearLayout bar = UI.row(this);
        int p = UI.dp(this, 16);
        bar.setPadding(p, UI.dp(this, 14), p, UI.dp(this, 10));
        TextView back = UI.tv(this, "‹ 返回", 15.5f, UI.ACCENT, false);
        back.setPadding(0, UI.dp(this, 6), UI.dp(this, 10), UI.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(back);
        bar.addView(UI.tv(this, "用户设定", 18, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        UI.padTop(bar, 14);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = UI.col(this);
        col.setPadding(p, UI.dp(this, 6), p, UI.dp(this, 30));

        col.addView(UI.tv(this, "和「" + agent.name + "」聊天时，「你」是谁", 13, UI.SUB, false));
        col.addView(UI.space(this, 12));

        // ---- 全局设定 ----
        boolean usingGlobal = agent.useGlobalUser;
        LinearLayout g = UI.card(this, 20, 16);
        LinearLayout gh = UI.row(this);
        gh.addView(UI.tv(this, "全局设定", 15.5f, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (usingGlobal) gh.addView(UI.tv(this, "已使用", 13, UI.ACCENT, true));
        g.addView(gh);
        g.addView(UI.space(this, 10));
        g.addView(profilePreview(store.profile));
        g.addView(UI.space(this, 12));
        LinearLayout gbtns = UI.row(this);
        TextView useBtn = UI.button(this, usingGlobal ? "已使用" : "使用", !usingGlobal, 16);
        useBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                agent.useGlobalUser = true;
                store.putAgent(agent);
                UI.toast(UserProfileActivity.this, "已切换为全局设定");
                build();
            }
        });
        gbtns.addView(useBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        gbtns.addView(UI.hspace(this, 10));
        TextView editGlobal = UI.button(this, "编辑全局", false, 16);
        editGlobal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(UserProfileActivity.this, ProfileEditActivity.class));
            }
        });
        gbtns.addView(editGlobal, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        g.addView(gbtns);
        col.addView(g);
        col.addView(UI.space(this, 14));

        // ---- 角色专用设定 ----
        LinearLayout s = UI.card(this, 20, 16);
        LinearLayout sh = UI.row(this);
        sh.addView(UI.tv(this, "角色专用设定", 15.5f, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!usingGlobal) sh.addView(UI.tv(this, "已使用", 13, UI.ACCENT, true));
        s.addView(sh);
        s.addView(UI.space(this, 10));

        if (agent.userProfile == null) {
            s.addView(UI.tv(this, "还没有为这个角色单独设置过「你」。点了创建之后，"
                    + "只有和他聊天时才会用这份设定。", 12.5f, UI.SUB, false));
            s.addView(UI.space(this, 12));
            TextView create = UI.button(this, "创建", true, 16);
            create.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    agent.userProfile = Model.copyProfile(store.profile);
                    agent.useGlobalUser = false;
                    store.putAgent(agent);
                    Intent i = new Intent(UserProfileActivity.this, ProfileEditActivity.class);
                    i.putExtra(ProfileEditActivity.EXTRA_AGENT, agent.id);
                    startActivity(i);
                }
            });
            s.addView(create);
        } else {
            s.addView(profilePreview(agent.userProfile));
            s.addView(UI.space(this, 12));
            LinearLayout btns = UI.row(this);
            TextView edit = UI.button(this, "编辑", true, 16);
            edit.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(UserProfileActivity.this, ProfileEditActivity.class);
                    i.putExtra(ProfileEditActivity.EXTRA_AGENT, agent.id);
                    startActivity(i);
                }
            });
            btns.addView(edit, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btns.addView(UI.hspace(this, 10));
            TextView copy = UI.button(this, "复制全局设定", false, 16);
            copy.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    UI.confirm(UserProfileActivity.this, "用全局设定覆盖这份？",
                            "当前这份角色专用设定会被覆盖掉。", "覆盖", new UI.OnConfirm() {
                                public void run() {
                                    agent.userProfile = Model.copyProfile(store.profile);
                                    store.putAgent(agent);
                                    build();
                                    UI.toast(UserProfileActivity.this, "已复制全局设定");
                                }
                            });
                }
            });
            btns.addView(copy, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            s.addView(btns);
        }
        col.addView(s);

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        UI.putContent(outer, root);
        setContentView(outer);
    }

    private View profilePreview(Model.Profile pf) {
        LinearLayout row = UI.row(this);
        ImageView av = new ImageView(this);
        int size = UI.dp(this, 52);
        Bitmap b = UI.loadBitmap(store.imageFile(pf.avatar), 256);
        if (b != null) av.setImageBitmap(UI.circle(UI.square(b, 200)));
        else av.setBackground(UI.bg(UI.inputColor(), 26, this));
        row.addView(av, new LinearLayout.LayoutParams(size, size));
        row.addView(UI.hspace(this, 12));
        LinearLayout info = UI.col(this);
        String nm = pf.name.length() == 0 ? "还没设置名字" : pf.name;
        info.addView(UI.tv(this, nm, 15.5f, UI.TEXT, true));
        info.addView(UI.space(this, 3));
        info.addView(UI.tv(this, pf.gender, 12.5f, UI.SUB, false));
        if (pf.bio.length() > 0) {
            TextView b2 = UI.tv(this, pf.bio, 12.5f, UI.SUB, false);
            b2.setMaxLines(2);
            info.addView(b2);
        }
        row.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }
}
