package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * 智能体的创建 / 编辑表单。创建页和"导入后修改"都用它。
 */
public class AgentFormView extends LinearLayout {

    public interface Callback {
        void onSaved(Model.Agent a);
    }

    private static final int REQ_PICK = 1001;      // 角色全身图
    private static final int REQ_AVATAR = 1003;    // 头像

    private final Activity act;
    private final Store store;
    private final Model.Agent agent;
    private final Callback cb;

    private ImageView avatarView;
    private TextView avatarHint;
    private android.widget.FrameLayout avatarBox;
    private EditText nameEt, personaEt, memoEt, bioEt, greetEt;
    private final TextView[] genderViews = new TextView[3];

    public AgentFormView(Activity a, Model.Agent src, Callback cb) {
        super(a);
        this.act = a;
        this.store = Store.get(a);
        this.agent = (src == null) ? new Model.Agent() : src.copy();
        this.cb = cb;
        setOrientation(VERTICAL);
        // 不要在这里铺不透明底色，否则会盖掉全屏背景图和毛玻璃
        setBackgroundColor(0x00000000);
        build();
    }

    private void build() {
        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        UI.padTop(sv, 6);       // 别让「角色图片」跑到状态栏底下去
        LinearLayout root = UI.col(act);
        int p = UI.dp(act, 18);
        root.setPadding(p, UI.dp(act, 8), p, UI.dp(act, 28));

        // ---- 头像（点头像单独换）+ 角色全身图 ----
        LinearLayout top = UI.row(act);
        int av = UI.dp(act, 92);

        avatarBox = new android.widget.FrameLayout(act);
        avatarBox.setBackground(UI.bg(UI.CARD2, 46, act));   // 92dp 的一半 = 正圆
        avatarView = new ImageView(act);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarBox.addView(avatarView, new android.widget.FrameLayout.LayoutParams(av, av));
        avatarHint = UI.tv(act, "✎", 30, UI.SUB, false);
        avatarHint.setGravity(Gravity.CENTER);
        avatarHint.setPadding(0, 0, 0, UI.dp(act, 4));
        avatarBox.addView(avatarHint, new android.widget.FrameLayout.LayoutParams(av, av));
        avatarBox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { pickAvatar(); }
        });
        top.addView(avatarBox, new LinearLayout.LayoutParams(av, av));
        top.addView(UI.hspace(act, 16));

        LinearLayout right = UI.col(act);
        right.addView(UI.tv(act, "角色图片", 15.5f, UI.TEXT, true));
        right.addView(UI.space(act, 4));
        right.addView(UI.tv(act, "全身图，随便选一张就行，不裁剪", 12.5f, UI.SUB, false));
        right.addView(UI.space(act, 3));
        right.addView(UI.tv(act, "点头像可以单独换头像", 12.5f, UI.SUB, false));
        right.addView(UI.space(act, 10));
        TextView pick = UI.button(act, "选择图片", false, 12);
        pick.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { pickImage(); }
        });
        LinearLayout wrapBtn = UI.row(act);
        wrapBtn.addView(pick);
        right.addView(wrapBtn);
        top.addView(right, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);
        root.addView(UI.space(act, 22));

        // ---- 昵称 ----
        root.addView(UI.label(act, "昵称"));
        nameEt = UI.input(act, "给你的智能体命名", 1);
        nameEt.setText(agent.name);
        root.addView(UI.wrapField(act, nameEt));
        root.addView(UI.space(act, 16));

        // ---- 性别 ----
        root.addView(UI.label(act, "性别"));
        LinearLayout genders = UI.row(act);
        for (int i = 0; i < Model.GENDERS.length; i++) {
            final String g = Model.GENDERS[i];
            TextView t = UI.tv(act, g, 14, UI.TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(UI.dp(act, 16), UI.dp(act, 9), UI.dp(act, 16), UI.dp(act, 9));
            t.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    agent.gender = g;
                    refreshGenders();
                }
            });
            genderViews[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UI.dp(act, 8);
            genders.addView(t, lp);
        }
        root.addView(genders);
        refreshGenders();
        root.addView(UI.space(act, 16));

        // ---- 智能体设定 ----
        root.addView(UI.label(act, "智能体设定（决定回复效果）   ·   只有你自己能看到"));
        personaEt = UI.input(act, "人设、性格、身份、背景经历、聊天风格、和你的关系……", 5);
        personaEt.setText(agent.persona);
        root.addView(UI.wrapField(act, personaEt));
        root.addView(UI.space(act, 16));

        // ---- 对外简介 ----
        root.addView(UI.label(act, "对外简介   ·   进入角色页时会显示"));
        bioEt = UI.input(act, "想让对方一眼知道的事：背景、身份、和你的关系", 3);
        bioEt.setText(agent.bio);
        root.addView(UI.wrapField(act, bioEt));
        root.addView(UI.space(act, 16));

        // ---- 开场白 ----
        root.addView(UI.label(act, "开场白   ·   进入对话时TA说的第一句；（）里写动作，括号外是台词"));
        greetEt = UI.input(act, "（抬头看你）你来啦。", 4);
        greetEt.setText(agent.greeting);
        root.addView(UI.wrapField(act, greetEt));
        root.addView(UI.space(act, 24));

        TextView save = UI.button(act, "完成", true, 22);
        save.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { save(); }
        });
        root.addView(save);

        sv.addView(root);
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        refreshAvatar();
    }

    private void refreshGenders() {
        for (int i = 0; i < genderViews.length; i++) {
            boolean on = Model.GENDERS[i].equals(agent.gender);
            genderViews[i].setTextColor(on ? UI.ON_ACCENT : UI.TEXT);
            genderViews[i].setBackground(on ? UI.gradient(20, act) : UI.bg(UI.CARD2, 20, act));
        }
    }

    private void refreshAvatar() {
        Bitmap b = UI.loadBitmap(store.imageFile(agent.avatar), 512);
        if (b != null) {
            avatarView.setImageBitmap(UI.circle(UI.square(b, 256)));
            avatarHint.setVisibility(View.GONE);
        } else {
            avatarView.setImageDrawable(null);
            avatarHint.setVisibility(View.VISIBLE);
        }
    }

    /** 角色全身图：只开文件管理器，选完直接用，不裁剪 */
    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        act.startActivityForResult(i, REQ_PICK);
    }

    /** 头像：同样只开文件管理器，选完直接用，不裁剪 */
    private void pickAvatar() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        act.startActivityForResult(i, REQ_AVATAR);
    }

    /** 由宿主 Activity 转发 */
    public void onActivityResult(int req, int res, Intent data) {
        if (res != Activity.RESULT_OK || data == null) return;
        if (req == REQ_AVATAR) {
            Uri uri = data.getData();
            if (uri == null) return;
            String name = store.importImage(uri);
            if (name.length() == 0) { UI.toast(act, "读取图片失败"); return; }
            if (agent.avatar.length() > 0) store.deleteImage(agent.avatar);
            agent.avatar = name;
            refreshAvatar();
        } else if (req == REQ_PICK) {
            Uri uri = data.getData();
            if (uri == null) return;
            String name = store.importImage(uri);
            if (name.length() == 0) { UI.toast(act, "读取图片失败"); return; }
            if (agent.image.length() > 0 && !agent.image.equals(name)) {
                store.deleteImage(agent.image);
            }
            agent.image = name;
        }
    }

    private void save() {
        agent.name = nameEt.getText().toString().trim();
        agent.persona = personaEt.getText().toString().trim();
        agent.bio = bioEt.getText().toString().trim();
        agent.memo = agent.bio;          // 对外简介同时就是聊天页顶部那张卡的内容
        agent.greeting = greetEt.getText().toString().trim();
        if (agent.name.length() == 0) { UI.toast(act, "先起个名字吧"); return; }
        store.putAgent(agent);
        if (cb != null) cb.onSaved(agent);
    }
}
