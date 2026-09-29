package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** API 管理：选当前通道 + 添加自定义 API */
public class ApiActivity extends Activity {

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
        bar.addView(UI.tv(this, "API 管理", 18, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        UI.padTop(bar, 14);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = UI.col(this);
        col.setPadding(p, UI.dp(this, 6), p, UI.dp(this, 30));

        col.addView(UI.tv(this, "选一个通道", 13, UI.SUB, false));
        col.addView(UI.space(this, 10));
        for (final Model.Api a : store.apis) {
            col.addView(apiRow(a));
            col.addView(UI.space(this, 10));
        }

        col.addView(UI.space(this, 6));
        TextView add = UI.button(this, "＋ 添加 API", true, 15);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ApiActivity.this, ApiEditActivity.class));
            }
        });
        col.addView(add);
        col.addView(UI.space(this, 16));

        LinearLayout tips = UI.card(this, 16, 16);
        tips.addView(UI.tv(this, "说明", 14, UI.TEXT, true));
        tips.addView(UI.space(this, 6));
        tips.addView(UI.tv(this, "· 免费公共接口：不用填 key，直接能用，但可能不稳定、效果一般。\n"
                + "· 自定义 API：任何兼容 OpenAI 的接口都行，填 Base URL（到 /v1 为止）、Key、模型名。\n"
                + "· 所有配置只存在本机。", 12.5f, UI.SUB, false));
        col.addView(tips);

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        UI.putContent(outer, root);
        setContentView(outer);
    }

    private View apiRow(final Model.Api a) {
        boolean on = a.id.equals(store.currentApiId);
        LinearLayout row = UI.col(this);
        int p = UI.dp(this, 15);
        row.setPadding(p, p, p, p);
        row.setBackground(on ? UI.bgStroke(UI.CARD, 16, UI.ACCENT, 1.6f, this)
                : UI.bg(UI.CARD, 16, this));

        LinearLayout line = UI.row(this);
        line.addView(UI.tv(this, a.name, 15.5f, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (on) line.addView(UI.tv(this, "使用中", 12, UI.ACCENT, true));
        row.addView(line);
        row.addView(UI.space(this, 4));

        String desc;
        if (a.experimental) {
            desc = "稍慢但效果好，不花钱；只在特定时段可用，持续时间不定";
        } else if (a.free) {
            desc = "内置免费公共通道 · 免 key · 可能不稳定、效果较差";
        } else {
            desc = a.baseUrl + (a.model.length() > 0 ? "  ·  " + a.model : "");
        }
        row.addView(UI.tv(this, desc, 12.5f, UI.SUB, false));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (a.experimental && a.baseUrl.length() == 0) {
                    UI.confirm(ApiActivity.this, a.name,
                            "这条通道还没接入具体接口。",
                            "知道了", null);
                    return;
                }
                store.currentApiId = a.id;
                store.save();
                build();
            }
        });
        // 长按任意一条（包括内置的）都能编辑 / 删除
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                apiMenu(a);
                return true;
            }
        });
        return row;
    }

    private void apiMenu(final Model.Api a) {
        LinearLayout box = UI.col(this);
        box.addView(UI.title(this, a.name));
        box.addView(UI.space(this, 12));
        final android.app.Dialog d = UI.dialog(this, box);

        TextView edit = UI.tv(this, "编辑", 15.5f, UI.TEXT, false);
        edit.setPadding(UI.dp(this, 4), UI.dp(this, 13), UI.dp(this, 4), UI.dp(this, 13));
        edit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                android.content.Intent i = new android.content.Intent(ApiActivity.this,
                        ApiEditActivity.class);
                i.putExtra(ApiEditActivity.EXTRA_ID, a.id);
                startActivity(i);
            }
        });
        box.addView(edit);

        if (!a.free) {
            TextView del = UI.tv(this, "删除", 15.5f, UI.DANGER, false);
            del.setPadding(UI.dp(this, 4), UI.dp(this, 13), UI.dp(this, 4), UI.dp(this, 13));
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    d.dismiss();
                    UI.confirm(ApiActivity.this, "删除「" + a.name + "」？", null, "删除",
                            new UI.OnConfirm() {
                                public void run() {
                                    store.apis.remove(a);
                                    if (a.id.equals(store.currentApiId) && !store.apis.isEmpty()) {
                                        store.currentApiId = store.apis.get(0).id;
                                    }
                                    store.save();
                                    build();
                                }
                            });
                }
            });
            box.addView(del);
        }
        d.show();
    }
}
