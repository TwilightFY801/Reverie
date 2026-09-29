package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** 更改角色全局设定：用户自己的头像 / 姓名 / 性别 / 简介 */
public class ProfileEditActivity extends Activity {

    /** 带上这个 = 编辑「这个角色专用的」用户设定；不带 = 编辑全局 */
    public static final String EXTRA_AGENT = "agent";
    private static final int REQ_PICK = 4001;

    private Store store;
    private Model.Agent agent;
    private Model.Profile p;
    private EditText nameEt, bioEt;
    private ImageView avatarView;
    private TextView avatarHint;
    private android.widget.FrameLayout avatarBox;
    private final TextView[] genderViews = new TextView[3];

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

        Model.Profile src = store.profile;
        if (agent != null) {
            if (agent.userProfile == null) agent.userProfile = Model.copyProfile(store.profile);
            src = agent.userProfile;
        }
        p = new Model.Profile();
        p.name = src.name;
        p.gender = src.gender;
        p.bio = src.bio;
        p.avatar = src.avatar;
        p.image = src.image;
        build();
    }

    private void build() {
        FrameLayout outer = UI.installBackground(this, store.imageFile(store.bgImage));
        LinearLayout root = UI.col(this);

        LinearLayout bar = UI.row(this);
        int pad = UI.dp(this, 16);
        bar.setPadding(pad, UI.dp(this, 14), pad, UI.dp(this, 10));
        TextView back = UI.tv(this, "‹ 返回", 15.5f, UI.ACCENT, false);
        back.setPadding(0, UI.dp(this, 6), UI.dp(this, 10), UI.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(back);
        bar.addView(UI.tv(this, agent == null ? "角色全局设定" : "角色专用设定", 18,
                        UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        UI.padTop(bar, 14);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = UI.col(this);
        col.setPadding(pad, UI.dp(this, 6), pad, UI.dp(this, 30));

        // 头像（点头像换，只开文件管理器，不裁剪）
        LinearLayout head = UI.col(this);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        int size = UI.dp(this, 104);
        avatarBox = new android.widget.FrameLayout(this);
        avatarBox.setBackground(UI.bg(UI.CARD2, 52, this));
        avatarView = new ImageView(this);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarBox.addView(avatarView, new android.widget.FrameLayout.LayoutParams(size, size));
        avatarHint = UI.tv(this, "✎", 34, UI.SUB, false);
        avatarHint.setGravity(Gravity.CENTER);
        avatarHint.setPadding(0, 0, 0, UI.dp(this, 4));
        avatarBox.addView(avatarHint, new android.widget.FrameLayout.LayoutParams(size, size));
        avatarBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickImage(); }
        });
        head.addView(avatarBox, new LinearLayout.LayoutParams(size, size));
        head.addView(UI.space(this, 12));
        TextView pick = UI.button(this, "选择图片", false, 13);
        pick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickImage(); }
        });
        LinearLayout wrap = UI.row(this);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.addView(pick);
        head.addView(wrap);
        head.addView(UI.space(this, 8));
        head.addView(UI.tv(this, "这是「你」在对话里的身份，所有角色都会看到", 12, UI.SUB, false));
        col.addView(head);
        col.addView(UI.space(this, 22));

        col.addView(UI.label(this, "你的名字"));
        nameEt = UI.input(this, "别人怎么叫你", 1);
        nameEt.setText(p.name);
        col.addView(nameEt);
        col.addView(UI.space(this, 16));

        col.addView(UI.label(this, "性别"));
        LinearLayout genders = UI.row(this);
        for (int i = 0; i < Model.GENDERS.length; i++) {
            final String g = Model.GENDERS[i];
            TextView t = UI.tv(this, g, 14, UI.TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(UI.dp(this, 16), UI.dp(this, 9), UI.dp(this, 16), UI.dp(this, 9));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    p.gender = g;
                    refreshGenders();
                }
            });
            genderViews[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UI.dp(this, 8);
            genders.addView(t, lp);
        }
        col.addView(genders);
        refreshGenders();
        col.addView(UI.space(this, 16));

        col.addView(UI.label(this, "简介"));
        bioEt = UI.input(this, "让角色知道你是谁、你怎么说话", 4);
        bioEt.setText(p.bio);
        col.addView(bioEt);
        col.addView(UI.space(this, 24));

        TextView save = UI.button(this, "保存", true, 16);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
        col.addView(save);

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        UI.putContent(outer, root);
        setContentView(outer);
        refreshAvatar();
    }

    private void refreshGenders() {
        for (int i = 0; i < genderViews.length; i++) {
            boolean on = Model.GENDERS[i].equals(p.gender);
            genderViews[i].setTextColor(on ? UI.ON_ACCENT : UI.TEXT);
            genderViews[i].setBackground(on ? UI.gradient(20, this) : UI.bg(UI.CARD2, 20, this));
        }
    }

    private void refreshAvatar() {
        Bitmap b = UI.loadBitmap(store.imageFile(p.avatar), 512);
        if (b != null) {
            avatarView.setImageBitmap(UI.circle(UI.square(b, 256)));
            avatarHint.setVisibility(View.GONE);
        } else {
            avatarView.setImageDrawable(null);
            avatarHint.setVisibility(View.VISIBLE);
        }
    }

    /** 只开文件管理器，选完直接当头像，不裁剪 */
    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_PICK) {
            Uri uri = data.getData();
            if (uri == null) return;
            String name = store.importImage(uri);
            if (name.length() == 0) { UI.toast(this, "读取图片失败"); return; }
            if (p.avatar.length() > 0) store.deleteImage(p.avatar);
            p.avatar = name;
            p.image = name;
            refreshAvatar();
        }
    }

    private void save() {
        p.name = nameEt.getText().toString().trim();
        p.bio = bioEt.getText().toString().trim();
        if (p.name.length() == 0) { UI.toast(this, "先写个名字"); return; }
        if (agent != null) {
            agent.userProfile = p;
            agent.useGlobalUser = false;
            store.putAgent(agent);
        } else {
            store.profile.name = p.name;
            store.profile.gender = p.gender;
            store.profile.bio = p.bio;
            store.profile.avatar = p.avatar;
            store.profile.image = p.image;
            store.save();
        }
        UI.toast(this, "已保存");
        finish();
    }
}
