package com.twilight.zhiyu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 聊天页 */
public class ChatActivity extends Activity {

    public static final String EXTRA_AGENT = "agent";
    private static final int MAX_TURNS = 24;
    private static final int CAND_MAX = 10;      // 重说候选上限
    private static final int CAND_BATCH = 4;     // 每批只生成这么多
    private static final int REQ_IMG = 6001;
    private static final int REQ_CHATLOG = 6002;

    private Store store;
    private Model.Agent agent;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private TextView sendBtn;
    private ImageView bgView;
    private LinearLayout pagerBar;
    private TextView pagerText;
    private LinearLayout lastActionRow;   // 最后一条对方消息底下的操作条
    private boolean busy = false;

    private final List<Msg> history = new ArrayList<Msg>();
    private final List<String> cands = new ArrayList<String>();
    private int candIdx = 0;

    /** 一条消息 */
    static class Msg {
        String role;      // user / assistant
        String text;
        String image;     // 图片文件名，可空
        boolean greeting; // 是不是开场白（导出聊天记录时要跳过）
        Msg(String r, String t) { role = r; text = t; }
        Msg(String r, String t, String img) { role = r; text = t; image = img; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = Store.get(this);
        UI.theme(store.dark);
        UI.glass = store.glass;
        UI.applyWindow(this);

        String id = getIntent().getStringExtra(EXTRA_AGENT);
        agent = id == null ? null : store.findAgent(id);
        if (agent == null) { finish(); return; }

        build();
        loadHistory();
        if (history.isEmpty()) {
            showMemoCard();
            if (agent.greeting.length() > 0) {
                Msg m = new Msg("assistant", agent.greeting);
                m.greeting = true;
                history.add(m);
                addBubble(m, false);
                saveHistory();
            }
        } else {
            for (Msg m : history) addBubble(m, false);
        }
        scrollDown();
    }

    // ---------------- 界面 ----------------

    private void build() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(UI.BG);

        // 角色主图当聊天背景
        bgView = new ImageView(this);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        outer.addView(bgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        File f = store.imageFile(agent.image);
        if (f == null || !f.exists()) f = store.imageFile(store.bgImage);
        if (f != null && f.exists()) {
            UI.fillBackground(bgView, f, false);   // 背景保持清晰
            UI.prepareGlass(this, f);              // 模糊只给胶囊面板用
        }

        LinearLayout root = UI.col(this);

        // 顶栏：也是悬浮胶囊
        FrameLayout topWrap = new FrameLayout(this);
        topWrap.setPadding(UI.dp(this, 12), 0, UI.dp(this, 12), 0);
        LinearLayout bar = UI.row(this);
        bar.setBackground(UI.bg(UI.barColor(), 26, this));
        bar.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        TextView back = UI.tv(this, "‹", 24, UI.ACCENT, false);
        back.setPadding(0, 0, UI.dp(this, 12), UI.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(back);

        ImageView av = new ImageView(this);
        int s = UI.dp(this, 36);
        Bitmap bmp = UI.loadBitmap(store.imageFile(agent.avatar), 128);
        if (bmp != null) av.setImageBitmap(UI.circle(UI.square(bmp, 128)));
        else av.setBackground(UI.bg(UI.inputColor(), 18, this));
        bar.addView(av, new LinearLayout.LayoutParams(s, s));
        bar.addView(UI.hspace(this, 10));

        LinearLayout nameBox = UI.col(this);
        nameBox.addView(UI.tv(this, agent.name, 16, UI.TEXT, true));
        nameBox.addView(UI.tv(this, agent.gender, 11.5f, UI.SUB, false));
        bar.addView(nameBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView more = UI.tv(this, "⋯", 22, UI.SUB, false);
        more.setPadding(UI.dp(this, 10), 0, UI.dp(this, 4), UI.dp(this, 6));
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { menu(); }
        });
        bar.addView(more);
        bar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { infoSheet(); }
        });
        topWrap.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(topWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        UI.padTop(topWrap, 8);

        // 消息区
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = UI.col(this);
        int lp = UI.dp(this, 14);
        list.setPadding(lp, lp, lp, lp);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 重说改成底部弹层了，这里不再放内嵌分页条

        // 输入区：悬浮胶囊
        FrameLayout bottomWrap = new FrameLayout(this);
        LinearLayout bottom = UI.row(this);
        bottom.setBackground(UI.bg(UI.barColor(), 28, this));
        bottom.setPadding(UI.dp(this, 10), UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 8));
        bottomWrap.setPadding(UI.dp(this, 14), 0, UI.dp(this, 14), 0);

        // 左边的图片圆钮
        TextView imgBtn = UI.tv(this, "＋", 20, UI.ON_ACCENT, true);
        imgBtn.setGravity(Gravity.CENTER);
        imgBtn.setBackground(UI.gradient(24, this));
        int bs = UI.dp(this, 40);
        imgBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickImage(); }
        });
        bottom.addView(imgBtn, new LinearLayout.LayoutParams(bs, bs));
        bottom.addView(UI.hspace(this, 8));

        // 输入框：不限长度，最多显示 5 行
        input = new EditText(this);
        input.setHint("说点什么…（括号里写动作）");
        input.setTextSize(15);
        input.setTextColor(UI.TEXT);
        input.setHintTextColor(UI.SUB);
        input.setBackground(UI.bg(UI.inputColor(), 20, this));
        input.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        input.setMaxLines(5);
        input.setSingleLine(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setVerticalScrollBarEnabled(true);
        bottom.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(UI.hspace(this, 8));

        sendBtn = UI.button(this, "发送", true, 20);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { send(); }
        });
        bottom.addView(sendBtn);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomWrap.addView(bottom, blp);
        root.addView(bottomWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        UI.padBottom(bottomWrap, 10);

        outer.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(outer);

        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { scrollDown(); }
        });
    }

    // ---------------- 气泡 ----------------

    private CharSequence styled(String text) {
        SpannableString sp = new SpannableString(text);
        int i = 0, n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '（' || c == '(') {
                char close = (c == '（') ? '）' : ')';
                int j = text.indexOf(close, i + 1);
                if (j < 0) break;
                sp.setSpan(new ForegroundColorSpan(UI.SUB), i, j + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(Typeface.ITALIC), i, j + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = j + 1;
            } else {
                i++;
            }
        }
        return sp;
    }

    private void addBubble(Msg m, boolean animate) {
        boolean me = "user".equals(m.role);
        LinearLayout row = UI.row(this);
        row.setGravity(me ? Gravity.RIGHT : Gravity.LEFT);

        if (!me) {
            ImageView av = new ImageView(this);
            int s = UI.dp(this, 32);
            Bitmap bmp = UI.loadBitmap(store.imageFile(agent.avatar), 128);
            if (bmp != null) av.setImageBitmap(UI.circle(UI.square(bmp, 128)));
            else av.setBackground(UI.bg(UI.inputColor(), 16, this));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(s, s);
            alp.topMargin = UI.dp(this, 2);
            row.addView(av, alp);
            row.addView(UI.hspace(this, 8));
        }

        LinearLayout stack = UI.col(this);
        if (m.image != null && m.image.length() > 0) {
            ImageView pic = new ImageView(this);
            Bitmap ib = UI.loadBitmap(store.imageFile(m.image), 900);
            if (ib != null) {
                pic.setImageBitmap(UI.rounded(ib, UI.dp(this, 16)));
                int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.58f);
                int w = Math.min(maxw(ib), maxW);
                int h = (int) ((float) w * ib.getHeight() / ib.getWidth());
                stack.addView(pic, new LinearLayout.LayoutParams(w, h));
            }
        }
        if (m.text != null && m.text.length() > 0) {
            TextView bubble = new TextView(this);
            bubble.setText(styled(m.text));
            final Msg mm = m;
            View.OnLongClickListener longPress = new View.OnLongClickListener() {
                public boolean onLongClick(View v) { msgMenu(mm); return true; }
            };
            bubble.setOnLongClickListener(longPress);
            bubble.setTextSize(15);
            bubble.setTextColor(me ? UI.BUBBLE_ME_TEXT : UI.TEXT);
            bubble.setLineSpacing(UI.dp(this, 4), 1f);
            int pad = UI.dp(this, 13);
            bubble.setPadding(pad, pad, pad, pad);
            bubble.setBackground(me ? UI.bg(UI.BUBBLE_ME, 18, this)
                    : UI.bg(UI.cardColor(), 18, this));
            bubble.setTextIsSelectable(true);
            bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.76f));
            if (m.image != null && m.image.length() > 0) {
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = UI.dp(this, 6);
                stack.addView(bubble, blp);
            } else {
                stack.addView(bubble);
            }
        }

        row.addView(stack);
        if (me) row.addView(UI.hspace(this, 2));

        LinearLayout wrap = UI.col(this);
        wrap.setPadding(0, UI.dp(this, 5), 0, UI.dp(this, 5));
        wrap.addView(row);

        // 底下不再挂按钮条了，全部改成长按消息调出
        list.addView(wrap);
        if (animate) scrollDown();
    }

    private int maxw(Bitmap b) {
        return b.getWidth();
    }

    /** 胶囊里的一个线框图标按钮 */
    private TextView iconBtn(String glyph, String label, View.OnClickListener l) {
        TextView t = UI.tv(this, glyph, 15, UI.TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(UI.dp(this, 13), UI.dp(this, 7), UI.dp(this, 13), UI.dp(this, 7));
        t.setOnClickListener(l);
        t.setContentDescription(label);
        return t;
    }

    /** 长按某条消息：复制 / 改写 / （智能体还有）继续说 / 重新说 / 撤回 */
    private void msgMenu(final Msg m) {
        final int idx = history.indexOf(m);
        if (idx < 0) return;
        final boolean isAgent = !"user".equals(m.role);
        // 只有「各自最后一条」才给扩展菜单；更早的只能复制/撤回
        final boolean isLastAgent = isAgent && idx == lastAssistantIndex();
        final boolean isLastUser = !isAgent && idx == lastUserIndex();
        final boolean extended = isLastAgent || isLastUser;
        LinearLayout box = UI.col(this);
        box.addView(UI.title(this, isAgent ? agent.name : "你说的"));
        box.addView(UI.space(this, 12));
        final android.app.Dialog d = UI.dialog(this, box);

        java.util.List<String> items = new java.util.ArrayList<String>();
        java.util.List<View.OnClickListener> acts = new java.util.ArrayList<View.OnClickListener>();

        items.add("复制");
        acts.add(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("zhiyu", m.text));
                }
                UI.toast(ChatActivity.this, "已复制");
            }
        });

        if (extended) {
            items.add("改写");
            acts.add(new View.OnClickListener() {
                public void onClick(View v) {
                    UI.prompt(ChatActivity.this, isAgent ? "改写这条回复" : "改写你说的这句",
                            "改完直接生效", m.text, new UI.OnOk() {
                                public void run(String text) {
                                    if (text.trim().length() == 0) return;
                                    m.text = text.trim();
                                    saveHistory();
                                    recreate();
                                }
                            });
                }
            });
        }

        if (isLastAgent) {
            items.add("继续说");
            acts.add(new View.OnClickListener() {
                public void onClick(View v) { continueAt(m); }
            });
        }

        items.add("撤回");
        acts.add(new View.OnClickListener() {
            public void onClick(View v) {
                UI.confirm(ChatActivity.this, "撤回这条？",
                        "这条以及它下面的所有对话都会被删掉（上面的保留）。",
                        "撤回", new UI.OnConfirm() {
                            public void run() {
                                while (history.size() > idx) {
                                    history.remove(history.size() - 1);
                                }
                                cands.clear();
                                candIdx = 0;
                                saveHistory();
                                recreate();
                            }
                        });
            }
        });

        for (int i = 0; i < items.size(); i++) {
            final View.OnClickListener act = acts.get(i);
            boolean danger = "撤回".equals(items.get(i));
            TextView t = UI.tv(this, items.get(i), 15.5f, danger ? UI.DANGER : UI.TEXT, false);
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

    /** 从某条消息开始重新生成 */
    private void regenAt(Msg m) {
        if (busy) return;
        int idx = history.indexOf(m);
        if (idx < 0) return;
        while (history.size() > idx) history.remove(history.size() - 1);
        cands.clear();
        candIdx = 0;
        requestReply("请换一种说法重新回答上一句，不要重复之前的措辞。");
    }

    /** 接着说：单独再冒一条气泡出来 */
    private void continueAt(Msg m) {
        if (busy) return;
        int idx = history.indexOf(m);
        if (idx < 0) return;
        while (history.size() > idx + 1) history.remove(history.size() - 1);
        requestReply("请顺着你上一句话继续说下去，两三句即可，不要重复已经说过的内容。");
    }


    private void showMemoCard() {
        if (agent.bio.length() == 0) return;
        LinearLayout card = UI.col(this);
        int p = UI.dp(this, 14);
        card.setPadding(p, p, p, p);
        card.setBackground(UI.bgStroke(UI.cardColor(), 16, UI.ACCENT, 1f, this));
        card.addView(UI.tv(this, "简介", 11.5f, UI.ACCENT, true));
        card.addView(UI.space(this, 5));
        card.addView(UI.tv(this, agent.bio, 13.5f, UI.TEXT, false));
        LinearLayout wrap = UI.col(this);
        wrap.setPadding(0, UI.dp(this, 4), 0, UI.dp(this, 12));
        wrap.addView(card);
        list.addView(wrap);
    }

    private void scrollDown() {
        scroll.post(new Runnable() {
            public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    // ---------------- 发送 ----------------

    private Msg lastUser() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role)) return history.get(i);
        }
        return null;
    }

    private Msg lastAssistant() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equals(history.get(i).role)) return history.get(i);
        }
        return null;
    }

    private int lastAssistantIndex() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equals(history.get(i).role)) return i;
        }
        return -1;
    }

    private int lastUserIndex() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role)) return i;
        }
        return -1;
    }

    private void send() {
        if (busy) return;
        sendWithImage(null);
    }

    /** 发送：text 取自输入框；img 可选（图文一起发） */
    private void sendWithImage(String img) {
        final String text = input.getText().toString().trim();
        if (text.length() == 0 && (img == null || img.length() == 0)) return;
        input.setText("");
        if (lastActionRow != null) {
            lastActionRow.setVisibility(View.GONE);
            lastActionRow = null;
        }
        Msg m = new Msg("user", text, img);
        history.add(m);
        addBubble(m, true);
        saveHistory();
        cands.clear();
        candIdx = 0;
        updatePager();
        requestReply(null);
    }

    /**
     * 请求回复。
     * extraHint：追加一条 system 提示（继续说 / 换一种说法 用）
     * appendToLast：把结果追加到上一条 assistant 消息后面
     */
    private void requestReply(final String extraHint) {
        requestReply(extraHint, null);
    }

    private void requestReply(final String extraHint, final Runnable done) {
        busy = true;
        sendBtn.setText("…");
        final LinearLayout typing = UI.col(this);
        typing.setPadding(UI.dp(this, 44), UI.dp(this, 6), 0, UI.dp(this, 6));
        typing.addView(UI.tv(this, agent.name + " 正在输入…", 12.5f, UI.SUB, false));
        list.addView(typing);
        scrollDown();

        List<Net.Msg> msgs = new ArrayList<Net.Msg>();
        msgs.add(new Net.Msg("system",
                Net.systemPrompt(agent, store.effectiveUser(agent), store.r18)));
        int from = Math.max(0, history.size() - MAX_TURNS);
        for (int i = from; i < history.size(); i++) {
            Msg m = history.get(i);
            String t = m.text == null ? "" : m.text;
            if (m.image != null && m.image.length() > 0) {
                t = (t.length() > 0 ? t + " " : "") + "[发来一张图片]";
            }
            if (t.length() > 0) msgs.add(new Net.Msg(m.role, t));
        }
        if (extraHint != null) msgs.add(new Net.Msg("system", extraHint));

        Net.chat(store.currentApi(), msgs, new Net.Cb() {
            public void ok(String reply) {
                busy = false;
                sendBtn.setText("发送");
                list.removeView(typing);
                String clean = reply.trim();
                Msg m = new Msg("assistant", clean);
                history.add(m);
                addBubble(m, true);
                cands.clear();
                cands.add(clean);
                candIdx = 0;
                updatePager();
                saveHistory();
                if (done != null) done.run();
            }

            public void fail(String message) {
                busy = false;
                sendBtn.setText("发送");
                list.removeView(typing);
                showError(message);
                if (done != null) done.run();
            }
        });
    }

    private void showError(String message) {
        LinearLayout err = UI.col(this);
        int p = UI.dp(this, 12);
        err.setPadding(p, p, p, p);
        err.setBackground(UI.bg(UI.inputColor(), 12, this));
        err.addView(UI.tv(this, "✕ " + message, 12.5f, UI.DANGER, false));
        LinearLayout w = UI.col(this);
        w.setPadding(0, UI.dp(this, 6), 0, UI.dp(this, 6));
        w.addView(err);
        list.addView(w);
        scrollDown();
    }

    // ---------------- 改写 / 重说 / 继续说 ----------------

    private void rewrite() {
        final Msg u = lastUser();
        if (u == null) { UI.toast(this, "还没有你的消息"); return; }
        UI.prompt(this, "改写你说的那句", "改完会重新生成回复", u.text, new UI.OnOk() {
            public void run(String text) {
                if (text.trim().length() == 0) return;
                u.text = text.trim();
                // 砍掉这条之后的所有消息，重新生成
                int idx = history.indexOf(u);
                while (history.size() > idx + 1) history.remove(history.size() - 1);
                cands.clear();
                candIdx = 0;
                updatePager();
                saveHistory();
                recreate();
            }
        });
    }

    private void regenerate() {
        if (busy) return;
        Msg u = lastUser();
        if (u == null) { UI.toast(this, "还没有你的消息"); return; }
        // 砍掉最后一条 assistant
        if (!history.isEmpty() && "assistant".equals(history.get(history.size() - 1).role)) {
            history.remove(history.size() - 1);
        }
        requestReply("请换一种说法重新回答上一句，不要重复之前的措辞。");
    }

    private void continueTalking() {
        if (busy) return;
        Msg a = lastAssistant();
        if (a == null) return;
        requestReply("请顺着你上一句话继续说下去，两三句即可，不要重复已经说过的内容。");
    }

    // ---------------- 重说：底部弹层（照星野） ----------------

    private android.app.Dialog regenSheet;
    private LinearLayout regenList;
    private TextView regenPager;

    private boolean generating = false;

    private void showRegenSheet() {
        if (lastUser() == null) { UI.toast(this, "还没有你的消息"); return; }
        // 弹层里要的是「全新生成」的候选，不能把原来那句放进去
        cands.clear();
        candIdx = 0;
        buildRegenSheet();
        if (!generating) fetchMoreCands();
    }

    private void buildRegenSheet() {
        if (regenSheet != null && regenSheet.isShowing()) regenSheet.dismiss();

        LinearLayout sheet = UI.col(this);
        sheet.setBackground(UI.bg(UI.sheetColor(), 22, this));
        int p = UI.dp(this, 18);
        sheet.setPadding(p, UI.dp(this, 16), p, UI.dp(this, 16));

        // 标题行
        LinearLayout head = UI.row(this);
        head.addView(UI.tv(this, "重说", 18, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = UI.tv(this, "✕", 18, UI.SUB, false);
        close.setPadding(UI.dp(this, 10), UI.dp(this, 4), UI.dp(this, 4), UI.dp(this, 4));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (regenSheet != null) regenSheet.dismiss(); }
        });
        head.addView(close);
        sheet.addView(head);
        sheet.addView(UI.space(this, 4));
        sheet.addView(UI.tv(this, "选择以下重说内容", 12.5f, UI.SUB, false));
        sheet.addView(UI.space(this, 14));

        // 候选列表（一页 4 个）
        regenList = UI.col(this);
        sheet.addView(regenList);
        fillRegenPage();

        sheet.addView(UI.space(this, 14));

        // 底部只留居中的分页胶囊（不要 ✎ 改写）
        FrameLayout foot = new FrameLayout(this);
        LinearLayout pager = UI.row(this);
        pager.setBackground(UI.bg(UI.blockColor(), 20, this));
        pager.setPadding(UI.dp(this, 4), UI.dp(this, 2), UI.dp(this, 4), UI.dp(this, 2));
        TextView prev = UI.tv(this, "‹", 18, UI.TEXT, true);
        prev.setPadding(UI.dp(this, 16), UI.dp(this, 2), UI.dp(this, 16), UI.dp(this, 2));
        prev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { stepCand(-1); }
        });
        regenPager = UI.tv(this, (candIdx + 1) + "/" + CAND_MAX, 13.5f, UI.TEXT, true);
        regenPager.setPadding(UI.dp(this, 10), 0, UI.dp(this, 10), 0);
        TextView next = UI.tv(this, "›", 18, UI.TEXT, true);
        next.setPadding(UI.dp(this, 16), UI.dp(this, 2), UI.dp(this, 16), UI.dp(this, 2));
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { stepCand(1); }
        });
        pager.addView(prev);
        pager.addView(regenPager);
        pager.addView(next);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
        foot.addView(pager, plp);
        sheet.addView(foot);

        // 整张卡片可以左右滑动翻页
        final float[] downX = new float[1];
        sheet.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    downX[0] = e.getX();
                    return false;
                }
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dx = e.getX() - downX[0];
                    if (Math.abs(dx) > UI.dp(ChatActivity.this, 60)) {
                        stepCand(dx < 0 ? 1 : -1);
                        return true;
                    }
                }
                return false;
            }
        });

        android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(sheet);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.CENTER);      // 弹到屏幕中间，不是从底部升上来
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        }
        d.show();
        UI.blurBehind(d, 28);
        regenSheet = d;
    }

    /** 画出当前这一页的 4 个候选 */
    private void fillRegenPage() {
        if (regenList == null) return;
        regenList.removeAllViews();
        int start = (candIdx / CAND_BATCH) * CAND_BATCH;
        int shown = 0;
        for (int i = start; i < Math.min(start + CAND_BATCH, cands.size()); i++) {
            final int idx = i;
            TextView block = UI.tv(this, cands.get(i), 14, UI.TEXT, false);
            int bp = UI.dp(this, 13);
            block.setPadding(bp, bp, bp, bp);
            block.setBackground(UI.bg(UI.blockColor(), 16, this));
            if (idx == candIdx) {
                block.setBackground(UI.bgStroke(UI.blockColor(), 16, UI.ACCENT, 1.6f, this));
            }
            block.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    candIdx = idx;
                    applyCand();
                    if (regenSheet != null) regenSheet.dismiss();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UI.dp(this, 8);
            regenList.addView(block, lp);
            shown++;
        }
        if (shown == 0) {
            regenList.addView(UI.tv(this,
                    generating ? "正在生成这一页的 4 个选项…" : "（这一页是空的，点右边 › 生成）",
                    13, UI.SUB, false));
        }
        if (regenPager != null) {
            regenPager.setText((candIdx + 1) + "/" + CAND_MAX);
        }
    }

    private void stepCand(int dir) {
        if (cands.isEmpty()) {
            if (dir > 0 && !generating) fetchMoreCands();
            return;
        }
        int target = candIdx + dir;
        if (target < 0) return;
        if (target < cands.size()) {
            candIdx = target;
            fillRegenPage();
            return;
        }
        if (dir > 0 && cands.size() < CAND_MAX) {
            fetchMoreCands();
        }
    }

    private void applyCand() {
        int idx = lastAssistantIndex();
        if (idx < 0 || candIdx >= cands.size()) return;
        history.get(idx).text = cands.get(candIdx);
        saveHistory();
        updatePager();
        recreate();
    }

    private void updatePager() {
        if (regenPager != null) {
            regenPager.setText((candIdx + 1) + "/" + CAND_MAX);
        }
    }

    /** 一次只多生成一批（4 个）；串行请求，避免触发接口限流 */
    private void fetchMoreCands() {
        final int want = Math.min(CAND_BATCH, CAND_MAX - cands.size());
        if (want <= 0) return;
        Msg u = lastUser();
        if (u == null) return;
        generating = true;
        busy = true;
        sendBtn.setText("…");
        fillRegenPage();

        final List<Net.Msg> base = new ArrayList<Net.Msg>();
        base.add(new Net.Msg("system",
                Net.systemPrompt(agent, store.effectiveUser(agent), store.r18)));
        int from = Math.max(0, history.size() - MAX_TURNS);
        for (int i = from; i < history.size() - 1; i++) {
            Msg m = history.get(i);
            String t = m.text == null ? "" : m.text;
            if (t.length() > 0) base.add(new Net.Msg(m.role, t));
        }
        base.add(new Net.Msg("system",
                "上面那句请换不同的说法重新回答，保持角色一致，但措辞、动作、情绪都要和已有版本不同。"));

        new Thread(new Runnable() {
            public void run() {
                final List<String> got = new ArrayList<String>();
                for (int k = 0; k < want; k++) {
                    final int step = k;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (pagerBar != null) pagerBar.setVisibility(View.VISIBLE);
                            if (pagerText != null) {
                                pagerText.setText("生成中 " + (step + 1) + "/" + want);
                            }
                        }
                    });
                    final Object lock = new Object();
                    final boolean[] done = new boolean[1];
                    List<Net.Msg> copy = new ArrayList<Net.Msg>(base);
                    copy.add(new Net.Msg("system",
                            "这是第 " + (cands.size() + k + 1) + " 个版本，请给一个不一样的。"));
                    Net.chat(store.currentApi(), copy, new Net.Cb() {
                        public void ok(String reply) {
                            String t = reply.trim();
                            synchronized (got) {
                                if (t.length() > 0 && !got.contains(t)) got.add(t);
                            }
                            synchronized (lock) { done[0] = true; lock.notifyAll(); }
                        }
                        public void fail(String message) {
                            synchronized (lock) { done[0] = true; lock.notifyAll(); }
                        }
                    });
                    synchronized (lock) {
                        long end = System.currentTimeMillis() + 200000;
                        while (!done[0] && System.currentTimeMillis() < end) {
                            try { lock.wait(1000); } catch (Exception ignored) { }
                        }
                    }
                    try { Thread.sleep(2500); } catch (Exception ignored) { }
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        busy = false;
                        generating = false;
                        sendBtn.setText("发送");
                        cands.addAll(got);
                        if (got.isEmpty()) {
                            UI.toast(ChatActivity.this, "没生成出来，可能又被限流了，过一会再试");
                        }
                        fillRegenPage();
                    }
                });
            }
        }).start();
    }

    // ---------------- 图片 ----------------

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_IMG);
    }

    private void pickChatLog() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed",
                        "application/octet-stream"});
        startActivityForResult(i, REQ_CHATLOG);
    }

    /** 导入的聊天记录直接接在现有对话后面，之后每次请求都会带上当上下文 */
    private void mergeChatLog(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            List<ZipPack.ChatLine> lines = ZipPack.readChatLog(in);
            if (in != null) in.close();
            if (lines.isEmpty()) { UI.toast(this, "这个包里没读到聊天记录"); return; }
            int added = 0;
            for (ZipPack.ChatLine l : lines) {
                if (l.text == null || l.text.trim().length() == 0) continue;
                Msg m = new Msg("user".equals(l.role) ? "user" : "assistant", l.text.trim());
                history.add(m);
                added++;
            }
            saveHistory();
            UI.toast(this, "已导入 " + added + " 条，之后的回复会记住这些上下文");
            recreate();
        } catch (Throwable t) {
            UI.toast(this, "导入失败：" + t.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMG && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            String name = store.importImage(uri);
            if (name.length() == 0) { UI.toast(this, "读取图片失败"); return; }
            // 有文字就图文一起发，没有就单发图片
            sendWithImage(name);
        } else if (req == REQ_CHATLOG && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) mergeChatLog(uri);
        }
    }

    // ---------------- 菜单 / 资料 ----------------

    private void menu() {
        LinearLayout box = UI.col(this);
        box.addView(UI.title(this, agent.name));
        box.addView(UI.space(this, 12));
        String[] items = {"查看资料", "用户设定", "导入聊天记录", "编辑角色",
                "换一张聊天背景", "清空对话"};
        View.OnClickListener[] acts = new View.OnClickListener[]{
                new View.OnClickListener() {
                    public void onClick(View v) { infoSheet(); }
                },
                new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent i = new Intent(ChatActivity.this, UserProfileActivity.class);
                        i.putExtra(UserProfileActivity.EXTRA_AGENT, agent.id);
                        startActivity(i);
                    }
                },
                new View.OnClickListener() {
                    public void onClick(View v) { pickChatLog(); }
                },
                new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent i = new Intent(ChatActivity.this, CreateActivity.class);
                        i.putExtra(CreateActivity.EXTRA_AGENT, agent.id);
                        startActivity(i);
                    }
                },
                new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent i = new Intent(ChatActivity.this, CreateActivity.class);
                        i.putExtra(CreateActivity.EXTRA_AGENT, agent.id);
                        startActivity(i);
                        UI.toast(ChatActivity.this, "在编辑页换「角色图片」，聊天背景会跟着变");
                    }
                },
                new View.OnClickListener() {
                    public void onClick(View v) {
                        UI.confirm(ChatActivity.this, "清空和「" + agent.name + "」的对话？",
                                "只清本机记录。", "清空", new UI.OnConfirm() {
                                    public void run() {
                                        history.clear();
                                        cands.clear();
                                        saveHistory();
                                        recreate();
                                    }
                                });
                    }
                }
        };
        final android.app.Dialog d = UI.dialog(this, box);
        for (int i = 0; i < items.length; i++) {
            final View.OnClickListener a = acts[i];
            TextView t = UI.tv(this, items[i], 15.5f, UI.TEXT, false);
            t.setPadding(UI.dp(this, 4), UI.dp(this, 13), UI.dp(this, 4), UI.dp(this, 13));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    d.dismiss();
                    a.onClick(v);
                }
            });
            box.addView(t);
        }
        d.show();
    }

    private void infoSheet() {
        LinearLayout box = UI.col(this);
        LinearLayout head = UI.row(this);
        ImageView av = new ImageView(this);
        int s = UI.dp(this, 56);
        Bitmap bmp = UI.loadBitmap(store.imageFile(agent.avatar), 256);
        if (bmp != null) av.setImageBitmap(UI.circle(UI.square(bmp, 200)));
        head.addView(av, new LinearLayout.LayoutParams(s, s));
        head.addView(UI.hspace(this, 12));
        LinearLayout nb = UI.col(this);
        nb.addView(UI.tv(this, agent.name, 17, UI.TEXT, true));
        nb.addView(UI.tv(this, agent.gender, 12.5f, UI.SUB, false));
        head.addView(nb);
        box.addView(head);
        if (agent.bio.length() > 0) {
            box.addView(UI.space(this, 14));
            box.addView(UI.tv(this, agent.bio, 13.5f, UI.SUB, false));
        }
        if (agent.memo.length() > 0) {
            box.addView(UI.space(this, 14));
            box.addView(UI.tv(this, "谨记", 12, UI.ACCENT, true));
            box.addView(UI.space(this, 4));
            box.addView(UI.tv(this, agent.memo, 13.5f, UI.TEXT, false));
        }
        box.addView(UI.space(this, 8));
        box.addView(UI.tv(this, "（人物设定只有你自己在编辑页能看到，聊天时不会显示）",
                11.5f, UI.SUB, false));
        UI.dialog(this, box).show();
    }

    // ---------------- 历史 ----------------

    private File chatFile() {
        File d = new File(getFilesDir(), "chats");
        if (!d.exists()) d.mkdirs();
        return new File(d, agent.id + ".json");
    }

    private void saveHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (Msg m : history) {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("text", m.text == null ? "" : m.text);
                if (m.image != null) o.put("image", m.image);
                if (m.greeting) o.put("greeting", true);
                arr.put(o);
            }
            FileOutputStream os = new FileOutputStream(chatFile());
            os.write(arr.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
        } catch (Throwable ignored) { }
    }

    private void loadHistory() {
        history.clear();
        try {
            File f = chatFile();
            if (!f.exists()) return;
            byte[] buf = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            in.close();
            JSONArray arr = new JSONArray(new String(buf, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Msg m = new Msg(o.optString("role"), o.optString("text"),
                        o.optString("image", null));
                m.greeting = o.optBoolean("greeting", false);
                history.add(m);
            }
            // 最后一个 assistant 视作有 1 个候选
            Msg last = lastAssistant();
            if (last != null) {
                cands.clear();
                cands.add(last.text);
                candIdx = 0;
            }
        } catch (Throwable ignored) { }
    }
}
