package com.twilight.zhiyu;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 兼容 OpenAI 的接口调用 + 角色提示词拼装。 */
public class Net {

    private static final String TAG = "Net";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 一条对话消息 */
    public static class Msg {
        public String role;   // system / user / assistant
        public String text;
        public Msg(String r, String t) { role = r; text = t; }
    }

    public interface Cb {
        void ok(String text);
        void fail(String message);
    }

    // ---------------- 提示词 ----------------

    public static String systemPrompt(Model.Agent a, Model.Profile u) {
        return systemPrompt(a, u, false);
    }

    public static String systemPrompt(Model.Agent a, Model.Profile u, boolean r18) {
        StringBuilder sb = new StringBuilder();
        sb.append("你现在要扮演下面这个角色，和用户进行日常聊天。\n\n");
        sb.append("【角色名】").append(nz(a.name, "未命名")).append("\n");
        sb.append("【性别】").append(nz(a.gender, Model.GENDER_SECRET)).append("\n");
        if (a.bio.length() > 0) sb.append("【简介】").append(a.bio).append("\n");
        if (a.persona.length() > 0) sb.append("【人物设定】\n").append(a.persona).append("\n");
        if (a.memo.length() > 0) sb.append("【必须谨记】\n").append(a.memo).append("\n");

        sb.append("\n【关于和你聊天的人】\n");
        sb.append("名字：").append(nz(u.name, "对方")).append("\n");
        sb.append("性别：").append(nz(u.gender, Model.GENDER_SECRET)).append("\n");
        if (u.bio.length() > 0) sb.append("简介：").append(u.bio).append("\n");

        sb.append("\n【回复要求】\n");
        sb.append("1. 用第一人称扮演这个角色，全程不要跳出角色，不要提到自己是 AI、模型或程序。\n");
        sb.append("2. 动作、神态、语气、心理活动一律写在括号里，用（）或()都行；真正说出口的话写在括号外。\n");
        sb.append("3. 一次回复别太长，像真人发微信那样，一兩句话就够，可以带括号里的动作。\n");
        sb.append("4. 严格按角色设定说话，不要用旁白的口吻替用户做决定。\n");
        if (r18) {
            sb.append("5. 用户已确认成年并主动开启成人内容模式：可以按照剧情自然地描写更亲密、"
                    + "更露骨的情节与情绪，不要回避也不要刻意加免责话；但仍然要贴合角色人设，"
                    + "保持文风与节奏，不要为了露骨而露骨。\n");
        }
        return sb.toString();
    }

    private static String nz(String s, String def) {
        return (s == null || s.length() == 0) ? def : s;
    }

    /** 给监控面板看的设备名，例如「Xiaomi 23054RA19C / Android 15」
     *  （HTTP 头只能放 ASCII，所以这里别用「·」这类字符） */
    private static String deviceTag() {
        try {
            String brand = android.os.Build.MANUFACTURER;
            String model = android.os.Build.MODEL;
            if (model != null && brand != null
                    && model.toLowerCase().startsWith(brand.toLowerCase())) {
                brand = "";
            }
            String s = (brand == null ? "" : brand) + " " + (model == null ? "" : model);
            s = s.trim() + " / Android " + android.os.Build.VERSION.RELEASE;
            return s.length() > 90 ? s.substring(0, 90) : s;
        } catch (Throwable t) {
            return "Android";
        }
    }

    // ---------------- 请求 ----------------

    public static String endpoint(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/chat/completions")) return u;
        if (u.endsWith("/openai")) return u;          // pollinations 这类直连端点
        return u + "/chat/completions";
    }

    /** 可重试的失败（限流/临时故障） */
    static class Retryable extends Exception {
        long delayMs;
        Retryable(String msg, long delay) { super(msg); delayMs = delay; }
    }

    public static void chat(final Model.Api api, final List<Msg> msgs, final Cb cb) {
        chat(api, msgs, 3, cb);
    }

    /**
     * attempts 次尝试；遇到 402/429/5xx 会自动等待后重试。
     * 免费公共接口是十几秒才放一次的限流，所以这里必须退避。
     */
    public static void chat(final Model.Api api, final List<Msg> msgs,
                            final int attempts, final Cb cb) {
        new Thread(new Runnable() {
            public void run() {
                Throwable last = null;
                for (int i = 0; i < attempts; i++) {
                    try {
                        final String out = doChat(api, msgs);
                        MAIN.post(new Runnable() {
                            public void run() { cb.ok(out); }
                        });
                        return;
                    } catch (Retryable r) {
                        last = r;
                        Log.w(TAG, "retry " + (i + 1) + "/" + attempts + ": " + r.getMessage());
                        if (i < attempts - 1) {
                            try { Thread.sleep(r.delayMs); } catch (InterruptedException ignored) { }
                        }
                    } catch (Throwable t) {
                        last = t;
                        break;
                    }
                }
                final String msg = friendly(last);
                MAIN.post(new Runnable() {
                    public void run() { cb.fail(msg); }
                });
            }
        }).start();
    }

    private static String doChat(Model.Api api, List<Msg> msgs) throws Exception {
        if (api == null) throw new Exception("没有可用的 API，去「设置 → API 管理」配一个");
        if (api.baseUrl == null || api.baseUrl.length() == 0) {
            throw new Exception("这条通道还没接入，换一条 API");
        }
        final String url = endpoint(api.baseUrl);

        JSONObject body = new JSONObject();
        body.put("model", api.model == null || api.model.length() == 0 ? "openai" : api.model);
        body.put("temperature", api.temperature);
        body.put("stream", false);
        JSONArray arr = new JSONArray();
        for (Msg m : msgs) {
            JSONObject o = new JSONObject();
            o.put("role", m.role);
            o.put("content", m.text);
            arr.put(o);
        }
        body.put("messages", arr);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        // 让本机那个「限时通道」的监控面板能认出是哪台手机
        conn.setRequestProperty("User-Agent", "Zhiyu/1.0 (Android)");
        conn.setRequestProperty("X-Zhiyu-Device", deviceTag());
        if (api.apiKey != null && api.apiKey.length() > 0) {
            conn.setRequestProperty("Authorization", "Bearer " + api.apiKey);
        }
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300)
                ? conn.getInputStream() : conn.getErrorStream();
        String raw = readAll(is);
        if (code == 402 || code == 429) {
            throw new Retryable("接口返回 " + code + "（限流）", 15000);
        }
        if (code >= 500) {
            throw new Retryable("接口返回 " + code, 5000);
        }
        if (code < 200 || code >= 300) {
            throw new Exception("接口返回 " + code + "：" + shortOf(raw));
        }
        String text = extract(raw);
        if (text == null || text.length() == 0) {
            throw new Exception("接口没返回内容：" + shortOf(raw));
        }
        return text;
    }

    /** 把常见错误翻译成人话 */
    private static String friendly(Throwable t) {
        if (t == null) return "未知错误";
        String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        String low = m.toLowerCase();
        if (low.contains("402") || low.contains("429")) {
            return "免费公共接口被限流了（隔十几秒才放一次）。等一下再发，"
                    + "或者去「设置 → API 管理」换成自己的 API 会稳很多。";
        }
        if (low.contains("unable to resolve host") || low.contains("unknownhost")) {
            return "连不上服务器，检查网络（这条通道可能在墙外，需要梯子）";
        }
        if (low.contains("timeout") || low.contains("timed out")) {
            return "请求超时了，这条通道现在可能很慢或者挂了，换一条试试";
        }
        if (low.contains("401") || low.contains("403")) {
            return "鉴权失败，检查 API Key 填对没有";
        }
        if (low.contains("404")) {
            return "接口地址不对（404），检查 Base URL 和模型名";
        }
        return m;
    }

    private static String shortOf(String s) {
        if (s == null) return "(空)";
        s = s.replace("\n", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    /** 兼容几种常见返回结构 */
    public static String extract(String raw) {
        try {
            String t = raw.trim();
            if (t.startsWith("{")) {
                JSONObject o = new JSONObject(t);
                JSONArray ch = o.optJSONArray("choices");
                if (ch != null && ch.length() > 0) {
                    JSONObject c0 = ch.getJSONObject(0);
                    JSONObject msg = c0.optJSONObject("message");
                    if (msg != null && msg.has("content")) {
                        Object content = msg.get("content");
                        if (content instanceof String) return (String) content;
                        if (content instanceof JSONArray) {
                            JSONArray parts = (JSONArray) content;
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < parts.length(); i++) {
                                JSONObject p = parts.optJSONObject(i);
                                if (p != null && p.has("text")) sb.append(p.optString("text"));
                            }
                            return sb.toString();
                        }
                    }
                    if (c0.has("text")) return c0.optString("text");
                }
                if (o.has("content")) return o.optString("content");
                if (o.has("response")) return o.optString("response");
                return null;
            }
            return t;   // 有些免费接口直接返回纯文本
        } catch (Throwable t) {
            return raw;
        }
    }

    // ---------------- 历史记录 ----------------

    public static List<Msg> historyToMsgs(JSONArray arr) {
        List<Msg> list = new ArrayList<Msg>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            list.add(new Msg(o.optString("role"), o.optString("text")));
        }
        return list;
    }
}
