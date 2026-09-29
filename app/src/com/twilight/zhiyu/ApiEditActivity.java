package com.twilight.zhiyu;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.UUID;

/** 添加 / 编辑一条自定义 API */
public class ApiEditActivity extends Activity {

    public static final String EXTRA_ID = "id";
    private Store store;
    private Model.Api api;
    private EditText nameEt, urlEt, keyEt, modelEt, tempEt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = Store.get(this);
        UI.theme(store.dark);
        UI.glass = store.glass;
        UI.prepareGlass(this, store.imageFile(store.bgImage));
        UI.applyWindow(this);

        String id = getIntent().getStringExtra(EXTRA_ID);
        Model.Api found = id == null ? null : store.findApi(id);
        api = found == null ? new Model.Api() : found;
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
        bar.addView(UI.tv(this, api.id.length() == 0 ? "添加 API" : "编辑 API", 18,
                        UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        UI.padTop(bar, 14);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = UI.col(this);
        col.setPadding(p, UI.dp(this, 6), p, UI.dp(this, 30));

        col.addView(UI.label(this, "名称"));
        nameEt = UI.input(this, "例如 我的中转站", 1);
        nameEt.setText(api.name);
        col.addView(nameEt);
        col.addView(UI.space(this, 14));

        col.addView(UI.label(this, "Base URL   ·   填到 /v1 为止"));
        urlEt = UI.input(this, "https://api.example.com/v1", 1);
        urlEt.setText(api.baseUrl);
        col.addView(urlEt);
        col.addView(UI.space(this, 14));

        col.addView(UI.label(this, "API Key"));
        keyEt = UI.input(this, "sk-...（免费公共接口可以留空）", 1);
        keyEt.setText(api.apiKey);
        col.addView(keyEt);
        col.addView(UI.space(this, 14));

        col.addView(UI.label(this, "模型名"));
        modelEt = UI.input(this, "例如 gpt-4o-mini / deepseek-chat", 1);
        modelEt.setText(api.model);
        col.addView(modelEt);
        col.addView(UI.space(this, 14));

        col.addView(UI.label(this, "温度   ·   0.1 稳重 ~ 1.5 放飞，默认 0.9"));
        tempEt = UI.input(this, "0.9", 1);
        tempEt.setText(String.valueOf(api.temperature));
        col.addView(tempEt);
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
    }

    private void save() {
        String n = nameEt.getText().toString().trim();
        String u = urlEt.getText().toString().trim();
        if (n.length() == 0) { UI.toast(this, "起个名字"); return; }
        if (u.length() == 0) { UI.toast(this, "填一下 Base URL"); return; }
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);

        api.name = n;
        api.baseUrl = u;
        api.apiKey = keyEt.getText().toString().trim();
        api.model = modelEt.getText().toString().trim();
        try {
            api.temperature = Float.parseFloat(tempEt.getText().toString().trim());
        } catch (Exception e) {
            api.temperature = 0.9f;
        }
        api.builtin = false;
        api.free = false;
        api.experimental = false;
        if (api.id.length() == 0) {
            api.id = UUID.randomUUID().toString();
            store.apis.add(api);
            store.currentApiId = api.id;
        }
        store.save();
        finish();
    }
}
