package com.twilight.zhiyu;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 智能体压缩包：导入 / 导出。
 *
 * 导出内容（同时写 json 和 4 个文本，尽量兼容）：
 *   agent.json        完整数据
 *   1_姓名性别.txt     姓名 + 性别
 *   2_设定.txt         智能体系设定
 *   3_简介.txt         简介
 *   4_开场白.txt       开场白
 *   image.jpg         角色主图
 *   avatar.jpg        1:1 头像
 *
 * 导入时容错：认 agent.json；没有就按序号/文件名猜那 4 个文本；
 * 图片按"最大的是主图、名字带 avatar/头像 的是头像"来判。
 */
public class ZipPack {

    private static final String TAG = "ZipPack";
    /** 单个条目上限，防止恶意超大 zip */
    private static final int MAX_ENTRY = 40 * 1024 * 1024;

    // ---------------- 导出 ----------------

    public static void export(Context c, Store store, Model.Agent a, OutputStream out)
            throws Exception {
        ZipOutputStream z = new ZipOutputStream(out);
        try {
            JSONObject o = new JSONObject();
            o.put("name", a.name);
            o.put("gender", a.gender);
            o.put("persona", a.persona);
            o.put("memo", a.memo);
            o.put("bio", a.bio);
            o.put("greeting", a.greeting);
            o.put("format", "zhiyu-agent/1");
            put(z, "agent.json", o.toString(2).getBytes("UTF-8"));

            put(z, "1_姓名性别.txt", ("姓名：" + a.name + "\n性别：" + a.gender + "\n").getBytes("UTF-8"));
            put(z, "2_设定.txt", (a.persona == null ? "" : a.persona).getBytes("UTF-8"));
            put(z, "3_简介.txt", (a.bio == null ? "" : a.bio).getBytes("UTF-8"));
            put(z, "4_开场白.txt", (a.greeting == null ? "" : a.greeting).getBytes("UTF-8"));
            if (a.memo != null && a.memo.length() > 0) {
                put(z, "5_谨记.txt", a.memo.getBytes("UTF-8"));
            }

            copyImage(z, "image.jpg", store.imageFile(a.image));
            copyImage(z, "avatar.jpg", store.imageFile(a.avatar));
        } finally {
            z.finish();
            z.flush();
        }
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws Exception {
        ZipEntry e = new ZipEntry(name);
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }

    private static void copyImage(ZipOutputStream z, String entry, File f) throws Exception {
        if (f == null || !f.exists()) return;
        ZipEntry e = new ZipEntry(entry);
        z.putNextEntry(e);
        InputStream in = new java.io.FileInputStream(f);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
        in.close();
        z.closeEntry();
    }

    // ---------------- 导出聊天记录 ----------------

    /**
     * 只导聊天记录：两个文本（智能体 / 用户），不带任何图片。
     * 序号是整段对话的全局序号：第 1 句是用户说的就是 1，智能体回的接 2，依此类推；
     * 想知道第 N 句是谁说的，看它出现在哪个文件里。
     * 开场白不算在聊天记录里。
     */
    public static int exportChat(Context c, Store store, Model.Agent agent, OutputStream out)
            throws Exception {
        java.io.File f = new java.io.File(new java.io.File(c.getFilesDir(), "chats"),
                agent.id + ".json");
        if (!f.exists()) return 0;
        byte[] buf = new byte[(int) f.length()];
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        int off = 0, n;
        while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
        in.close();
        JSONArray arr = new JSONArray(new String(buf, "UTF-8"));

        StringBuilder agentTxt = new StringBuilder();
        StringBuilder userTxt = new StringBuilder();
        int seq = 0;
        int count = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (o.optBoolean("greeting", false)) continue;   // 开场白不进聊天记录
            String role = o.optString("role");
            String text = o.optString("text", "");
            if (text.length() == 0) continue;
            seq++;
            count++;
            StringBuilder target = "user".equals(role) ? userTxt : agentTxt;
            target.append(seq).append(": ").append(text).append("\n");
        }

        ZipOutputStream z = new ZipOutputStream(out);
        try {
            put(z, "智能体.txt", agentTxt.toString().getBytes("UTF-8"));
            put(z, "用户.txt", userTxt.toString().getBytes("UTF-8"));
        } finally {
            z.finish();
            z.flush();
        }
        return count;
    }

    /** 读回来的聊天记录 */
    public static class ChatLine {
        public int seq;
        public String role;   // user / assistant
        public String text;
    }

    /**
     * 读聊天记录包（智能体.txt + 用户.txt）。
     * 文本格式是 "序号: 内容"，序号是整段对话的全局序号，两个文件靠它合并回正确顺序。
     */
    public static java.util.List<ChatLine> readChatLog(InputStream src) throws Exception {
        java.util.List<ChatLine> out = new java.util.ArrayList<ChatLine>();
        ZipInputStream z = new ZipInputStream(src);
        ZipEntry e;
        byte[] buf = new byte[64 * 1024];
        while ((e = z.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            String name = e.getName();
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            String low = name.toLowerCase();
            if (!low.endsWith(".txt")) continue;
            String role = (name.contains("用户") || low.contains("user")) ? "user" : "assistant";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int n, total = 0;
            while ((n = z.read(buf)) > 0) {
                total += n;
                if (total > MAX_ENTRY) throw new Exception("文件太大");
                bo.write(buf, 0, n);
            }
            String body = new String(bo.toByteArray(), "UTF-8");
            parseLines(body, role, out);
        }
        z.close();
        java.util.Collections.sort(out, new java.util.Comparator<ChatLine>() {
            public int compare(ChatLine a, ChatLine b) { return a.seq - b.seq; }
        });
        return out;
    }

    private static void parseLines(String body, String role, java.util.List<ChatLine> out) {
        String[] lines = body.split("\\r?\\n");
        ChatLine cur = null;
        for (String raw : lines) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^\\s*(\\d+)\\s*[:：.]\\s?(.*)$").matcher(raw);
            if (m.matches()) {
                cur = new ChatLine();
                cur.seq = Integer.parseInt(m.group(1));
                cur.role = role;
                cur.text = m.group(2);
                out.add(cur);
            } else if (cur != null && raw.trim().length() > 0) {
                cur.text = cur.text + "\n" + raw;
            }
        }
    }

    // ---------------- 导入 ----------------

    public static class Result {
        public Model.Agent agent;
        public String error;
    }

    public static Result read(Context c, Store store, InputStream src) {
        Result r = new Result();
        Model.Agent a = new Model.Agent();
        a.fromImport = true;
        List<String> texts = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        List<byte[]> images = new ArrayList<byte[]>();
        List<String> imageNames = new ArrayList<String>();
        String json = null;

        ZipInputStream z = new ZipInputStream(src);
        try {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                int n, total = 0;
                while ((n = z.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_ENTRY) throw new Exception("压缩包里有超大文件");
                    bo.write(buf, 0, n);
                }
                byte[] data = bo.toByteArray();
                String name = e.getName();
                String base = name;
                int slash = base.lastIndexOf('/');
                if (slash >= 0) base = base.substring(slash + 1);
                String lower = base.toLowerCase();
                if (lower.endsWith(".json")) {
                    if (json == null) json = new String(data, "UTF-8");
                } else if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                    texts.add(new String(data, "UTF-8"));
                    names.add(base);
                } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                        || lower.endsWith(".png") || lower.endsWith(".webp")) {
                    images.add(data);
                    imageNames.add(base);
                }
            }
        } catch (Exception ex) {
            r.error = "压缩包读取失败：" + ex.getMessage();
            return r;
        } finally {
            try { z.close(); } catch (Exception ignored) { }
        }

        if (json == null && texts.isEmpty()) {
            r.error = "压缩包里没有找到描述文件（.txt 或 agent.json）";
            return r;
        }
        // 没有图片也允许导入，只是头像留空，之后可以自己补

        // 1) 优先 agent.json
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                a.name = o.optString("name", "");
                a.gender = o.optString("gender", Model.GENDER_SECRET);
                a.persona = o.optString("persona", "");
                a.memo = o.optString("memo", "");
                a.bio = o.optString("bio", "");
                a.greeting = o.optString("greeting", "");
            } catch (Exception ignored) { }
        }

        // 2) 文本兜底：按序号或关键字把 4 个文档归位
        if (a.name.length() == 0 || a.persona.length() == 0
                || a.bio.length() == 0 || a.greeting.length() == 0) {
            String tName = null, tPersona = null, tBio = null, tGreet = null, tMemo = null;
            for (int i = 0; i < texts.size(); i++) {
                String base = names.get(i);
                String body = texts.get(i);
                String low = base.toLowerCase();
                int idx = firstDigit(low);

                if (looksLikePersona(base, low)) tPersona = body;
                else if (looksLikeBio(base, low)) tBio = body;
                else if (looksLikeGreeting(base, low)) tGreet = body;
                else if (looksLikeMemo(base, low)) tMemo = body;
                else if (looksLikeName(base, low, idx)) tName = body;
                else if (idx == 1) tName = body;
                else if (idx == 2) tPersona = body;
                else if (idx == 3) tBio = body;
                else if (idx == 4) tGreet = body;
                else if (tPersona == null) tPersona = body;
            }
            if (a.persona.length() == 0 && tPersona != null) a.persona = tPersona.trim();
            if (a.bio.length() == 0 && tBio != null) a.bio = tBio.trim();
            if (a.greeting.length() == 0 && tGreet != null) a.greeting = tGreet.trim();
            if (a.memo.length() == 0 && tMemo != null) a.memo = tMemo.trim();
            if (a.name.length() == 0 && tName != null) {
                String[] split = splitNameGender(tName);
                a.name = split[0];
                if (a.gender.length() == 0 || Model.GENDER_SECRET.equals(a.gender)) {
                    if (split[1] != null) a.gender = split[1];
                }
            }
        }

        // 3) 图片：名字里带 avatar/头像 的当头像，其余最大的一张当主图
        int avatarIdx = -1, mainIdx = -1;
        long mainSize = -1;
        for (int i = 0; i < images.size(); i++) {
            String low = imageNames.get(i).toLowerCase();
            if (low.contains("avatar") || low.contains("头像") || low.contains("1x1")
                    || low.contains("1-1")) {
                if (avatarIdx < 0) avatarIdx = i;
            }
            if (images.get(i).length > mainSize) {
                mainSize = images.get(i).length;
                mainIdx = i;
            }
        }
        if (mainIdx < 0) mainIdx = 0;
        if (avatarIdx < 0) avatarIdx = mainIdx;

        try {
            a.image = writeImage(store, images.get(mainIdx));
            if (avatarIdx == mainIdx) {
                a.avatar = makeSquareAvatar(store, images.get(avatarIdx));
            } else {
                a.avatar = writeImage(store, images.get(avatarIdx));
            }
        } catch (Exception ex) {
            r.error = "图片写入失败：" + ex.getMessage();
            return r;
        }

        if (a.name.length() == 0) a.name = "未命名角色";
        if (a.gender.length() == 0) a.gender = Model.GENDER_SECRET;
        r.agent = a;
        return r;
    }

    private static int firstDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '1' && ch <= '9') return ch - '0';
        }
        return -1;
    }

    private static boolean looksLikeName(String base, String low, int idx) {
        return low.contains("name") || base.contains("姓名") || base.contains("名称")
                || base.contains("信息") || base.contains("info") || idx == 1;
    }

    private static boolean looksLikePersona(String base, String low) {
        return low.contains("persona") || low.contains("prompt") || low.contains("setting")
                || base.contains("设定") || base.contains("人设") || base.contains("性格");
    }

    private static boolean looksLikeBio(String base, String low) {
        return low.contains("bio") || low.contains("desc") || base.contains("简介")
                || base.contains("介绍");
    }

    private static boolean looksLikeGreeting(String base, String low) {
        return low.contains("greet") || low.contains("opening") || base.contains("开场")
                || base.contains("第一句");
    }

    private static boolean looksLikeMemo(String base, String low) {
        return low.contains("memo") || low.contains("note") || base.contains("谨记")
                || base.contains("记住");
    }

    /** 把"第一段文本"拆成 姓名 / 性别 */
    private static String[] splitNameGender(String body) {
        String name = "", gender = null;
        String[] lines = body.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            String v = stripKey(line);
            if (line.startsWith("性别") || line.toLowerCase().startsWith("gender")
                    || line.startsWith("性別")) {
                gender = normalizeGender(v);
            } else if (line.startsWith("姓名") || line.startsWith("名字")
                    || line.startsWith("名称") || line.toLowerCase().startsWith("name")) {
                name = v;
            } else if (name.length() == 0) {
                name = line;
            } else if (gender == null) {
                String g = normalizeGender(line);
                if (g != null) gender = g;
            }
        }
        return new String[]{name.trim(), gender};
    }

    private static String stripKey(String line) {
        int i = line.indexOf(':');
        int j = line.indexOf('：');
        int k = (i < 0) ? j : (j < 0 ? i : Math.min(i, j));
        if (k >= 0) return line.substring(k + 1).trim();
        return line;
    }

    private static String normalizeGender(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.contains("男")) return Model.GENDER_MALE;
        if (t.contains("女")) return Model.GENDER_FEMALE;
        if (t.contains("不") || t.contains("保密") || t.contains("未知")
                || t.length() == 0) return Model.GENDER_SECRET;
        return Model.GENDER_SECRET;
    }

    private static String writeImage(Store store, byte[] data) throws Exception {
        String name = Store.newImageName();
        File f = new File(store.imagesDir(), name);
        FileOutputStream os = new FileOutputStream(f);
        os.write(data);
        os.flush();
        os.close();
        return name;
    }

    /** 没有单独头像时，从主图正中间裁一个 1:1 出来 */
    private static String makeSquareAvatar(Store store, byte[] data) throws Exception {
        String path = writeImage(store, data);
        try {
            Bitmap b = UI.loadBitmap(new File(store.imagesDir(), path), 512);
            if (b == null) return path;
            Bitmap sq = UI.square(b, 512);
            String name = Store.newImageName();
            FileOutputStream os = new FileOutputStream(new File(store.imagesDir(), name));
            sq.compress(Bitmap.CompressFormat.JPEG, 92, os);
            os.flush();
            os.close();
            return name;
        } catch (Throwable t) {
            Log.w(TAG, "makeSquareAvatar failed", t);
            return path;
        }
    }
}
