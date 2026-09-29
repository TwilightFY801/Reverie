package com.twilight.zhiyu;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 开启 R18+ 前的免责声明。必须在这里停留满 10 秒，"同意"才可点。 */
public class R18Activity extends Activity {

    private static final int WAIT_SECONDS = 10;
    private int left = WAIT_SECONDS;
    private TextView agreeBtn;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        public void run() {
            left--;
            if (left > 0) {
                agreeBtn.setText("请先读完（" + left + " 秒）");
                agreeBtn.setBackground(UI.bg(UI.inputColor(), 22, R18Activity.this));
                agreeBtn.setTextColor(UI.SUB);
                h.postDelayed(this, 1000);
            } else {
                agreeBtn.setText("我已阅读并同意");
                agreeBtn.setBackground(UI.gradient(22, R18Activity.this));
                agreeBtn.setTextColor(UI.ON_ACCENT);
                agreeBtn.setClickable(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store store = Store.get(this);
        UI.theme(store.dark);
        UI.prepareGlass(this, store.imageFile(store.bgImage));
        UI.applyWindow(this);

        FrameLayout outer = UI.installBackground(this, store.imageFile(store.bgImage));
        LinearLayout root = UI.col(this);
        int p = UI.dp(this, 20);

        LinearLayout bar = UI.row(this);
        bar.setPadding(p, UI.dp(this, 14), p, UI.dp(this, 10));
        bar.addView(UI.title(this, "R18+ 内容免责声明"));
        root.addView(bar);
        UI.padTop(bar, 14);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = UI.col(this);
        col.setPadding(p, UI.dp(this, 6), p, UI.dp(this, 20));

        LinearLayout warn = UI.card(this, 18, 16);
        warn.addView(UI.tv(this, "⚠️  请认真读完再决定", 16, UI.DANGER, true));
        warn.addView(UI.space(this, 8));
        warn.addView(UI.tv(this, "开启后，聊天内容会跟随剧情走向出现成人向描写。", 14, UI.TEXT, false));
        col.addView(warn);
        col.addView(UI.space(this, 16));

        LinearLayout body = UI.card(this, 18, 18);
        body.addView(UI.tv(this, "开启前请确认以下全部内容：", 14.5f, UI.TEXT, true));
        body.addView(UI.space(this, 12));
        String[] lines = {
                "1. 我已年满 18 周岁，具备完全民事行为能力。",
                "2. 我理解这类内容可能不健康、不友好，也可能引起不适或沉迷，"
                        + "长期接触可能影响我对现实关系的判断。",
                "3. 我知道这里生成的一切都是虚构的 AI 角色扮演，不构成任何现实建议，"
                        + "也不代表任何真实人物的立场。",
                "4. 我承诺只在私人场合使用，不传播、不截图外发、不用于任何违法用途。",
                "5. 我会自行控制使用时长；如出现情绪困扰，我会立即停止使用并寻求现实中的帮助。",
                "6. 因本人使用本功能所产生的一切后果，由我本人承担。",
                "7. 我随时可以回到设置里关闭这个模式。",
        };
        for (String s : lines) {
            TextView t = UI.tv(this, s, 13.5f, UI.TEXT, false);
            t.setPadding(0, UI.dp(this, 5), 0, UI.dp(this, 5));
            body.addView(t);
        }
        body.addView(UI.space(this, 10));
        body.addView(UI.tv(this, "点击下面的按钮即表示你已逐条阅读并同意以上全部内容。",
                12.5f, UI.SUB, false));
        col.addView(body);

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = UI.row(this);
        foot.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 12));
        TextView cancel = UI.button(this, "取消", false, 22);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        agreeBtn = UI.button(this, "请先读完（" + WAIT_SECONDS + " 秒）", false, 22);
        agreeBtn.setClickable(false);
        agreeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (left > 0) return;
                setResult(RESULT_OK);
                finish();
            }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = UI.dp(this, 10);
        foot.addView(cancel, lp1);
        foot.addView(agreeBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        root.addView(foot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        UI.padBottom(foot, 12);

        UI.putContent(outer, root);
        setContentView(outer);

        h.postDelayed(tick, 1000);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }
}
