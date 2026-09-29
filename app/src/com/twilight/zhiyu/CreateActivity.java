package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.OutputStream;

/** 编辑已有角色（也用于"导入完成后确认信息"） */
public class CreateActivity extends Activity {

    public static final String EXTRA_AGENT = "agent";
    public static final String EXTRA_JUST_IMPORTED = "imported";
    private static final int REQ_EXPORT = 5001;

    private Store store;
    private Model.Agent agent;
    private AgentFormView form;

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
        final boolean imported = getIntent().getBooleanExtra(EXTRA_JUST_IMPORTED, false);
        final String agentId = agent.id;

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
        bar.addView(UI.tv(this, imported ? "确认角色信息" : "编辑角色", 18, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chat = UI.tv(this, "去聊天", 14.5f, UI.ACCENT, true);
        chat.setPadding(UI.dp(this, 8), UI.dp(this, 6), 0, UI.dp(this, 6));
        chat.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(CreateActivity.this, ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_AGENT, agentId);
                startActivity(i);
                finish();
            }
        });
        bar.addView(chat);
        root.addView(bar);
        UI.padTop(bar, 14);

        if (imported) {
            TextView tip = UI.tv(this, "导入完成，信息已填好。不需要改就直接点下面的「完成」。",
                    12.5f, UI.SUB, false);
            tip.setPadding(p, 0, p, UI.dp(this, 6));
            root.addView(tip);
        }

        form = new AgentFormView(this, agent, new AgentFormView.Callback() {
            public void onSaved(Model.Agent saved) {
                UI.toast(CreateActivity.this, "已保存");
                finish();
            }
        });
        FrameLayout holder = new FrameLayout(this);
        holder.addView(form);
        root.addView(holder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        UI.putContent(outer, root);
        setContentView(outer);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (form != null) form.onActivityResult(req, res, data);
    }
}
