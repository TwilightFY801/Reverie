package com.twilight.zhiyu;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全部数据只存本机（filesDir），不联网、不上传。
 *   filesDir/data.json      结构化数据
 *   filesDir/images/*.jpg   用户导入的图片
 */
public class Store {

    private static final String TAG = "Store";
    private static Store sInstance;

    public Model.Profile profile = new Model.Profile();
    public final List<Model.Agent> agents = new ArrayList<Model.Agent>();
    public final List<Model.Api> apis = new ArrayList<Model.Api>();
    public String currentApiId = "";
    public boolean dark = false;
    public boolean glass = false;   // 毛玻璃效果
    public boolean r18 = false;     // R18+ 模式（要过免责声明页才会 true）
    public String bgImage = "";     // 自定义背景图文件名，空 = 用纯色

    private Context ctx;

    private Store(Context c) {
        ctx = c.getApplicationContext();
    }

    public static synchronized Store get(Context c) {
        if (sInstance == null) {
            sInstance = new Store(c);
            sInstance.load();
        }
        return sInstance;
    }

    /** 换背景/换主题后，界面重建前用它刷新 */
    public void reload() {
        load();
    }

    // ---------------- 路径 ----------------

    public File imagesDir() {
        File d = new File(ctx.getFilesDir(), "images");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public File imageFile(String name) {
        if (name == null || name.length() == 0) return null;
        return new File(imagesDir(), name);
    }

    public static String newImageName() {
        return UUID.randomUUID().toString().replace("-", "") + ".jpg";
    }

    /** 把外部 Uri 的图片复制进本应用私有目录，返回文件名 */
    public String importImage(Uri uri) {
        if (uri == null) return "";
        String name = newImageName();
        File out = new File(imagesDir(), name);
        InputStream in = null;
        OutputStream os = null;
        try {
            in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return "";
            os = new FileOutputStream(out);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
            return name;
        } catch (Exception e) {
            Log.w(TAG, "importImage failed", e);
            return "";
        } finally {
            close(in);
            close(os);
        }
    }

    public void deleteImage(String name) {
        File f = imageFile(name);
        if (f != null && f.exists()) f.delete();
    }

    /** 把一张 Bitmap 存进私有目录，返回文件名 */
    public String saveBitmap(android.graphics.Bitmap b) {
        if (b == null) return "";
        try {
            String name = newImageName();
            FileOutputStream os = new FileOutputStream(new File(imagesDir(), name));
            b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os);
            os.flush();
            os.close();
            return name;
        } catch (Throwable t) {
            return "";
        }
    }

    private static void close(java.io.Closeable c) {
        try { if (c != null) c.close(); } catch (Exception ignored) { }
    }

    // ---------------- 读写 ----------------

    private File dataFile() {
        return new File(ctx.getFilesDir(), "data.json");
    }

    public synchronized void load() {
        agents.clear();
        apis.clear();
        try {
            File f = dataFile();
            if (!f.exists()) {
                seedDefaults();
                return;
            }
            byte[] buf = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            in.close();
            JSONObject root = new JSONObject(new String(buf, "UTF-8"));

            JSONObject p = root.optJSONObject("profile");
            if (p != null) {
                profile.name = p.optString("name", "");
                profile.gender = p.optString("gender", Model.GENDER_SECRET);
                profile.bio = p.optString("bio", "");
                profile.avatar = p.optString("avatar", "");
                profile.image = p.optString("image", "");
            }

            JSONArray ja = root.optJSONArray("agents");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    Model.Agent a = new Model.Agent();
                    a.id = o.optString("id");
                    a.name = o.optString("name");
                    a.gender = o.optString("gender", Model.GENDER_SECRET);
                    a.persona = o.optString("persona");
                    a.memo = o.optString("memo");
                    a.bio = o.optString("bio");
                    a.greeting = o.optString("greeting");
                    a.image = o.optString("image");
                    a.avatar = o.optString("avatar");
                    a.createdAt = o.optLong("createdAt");
                    a.updatedAt = o.optLong("updatedAt");
                    a.fromImport = o.optBoolean("fromImport");
                    a.useGlobalUser = o.optBoolean("useGlobalUser", true);
                    JSONObject up = o.optJSONObject("userProfile");
                    if (up != null) {
                        Model.Profile pf = new Model.Profile();
                        pf.name = up.optString("name", "");
                        pf.gender = up.optString("gender", Model.GENDER_SECRET);
                        pf.bio = up.optString("bio", "");
                        pf.avatar = up.optString("avatar", "");
                        pf.image = up.optString("image", "");
                        a.userProfile = pf;
                    }
                    agents.add(a);
                }
            }

            JSONArray jp = root.optJSONArray("apis");
            if (jp != null) {
                for (int i = 0; i < jp.length(); i++) {
                    JSONObject o = jp.getJSONObject(i);
                    Model.Api a = new Model.Api();
                    a.id = o.optString("id");
                    a.name = o.optString("name");
                    a.baseUrl = o.optString("baseUrl");
                    a.apiKey = o.optString("apiKey");
                    a.model = o.optString("model");
                    a.builtin = o.optBoolean("builtin");
                    a.free = o.optBoolean("free");
                    a.experimental = o.optBoolean("experimental");
                    a.temperature = (float) o.optDouble("temperature", 0.9);
                    apis.add(a);
                }
            }

            currentApiId = root.optString("currentApiId", "");
            dark = root.optBoolean("dark", false);
            glass = root.optBoolean("glass", false);
            r18 = root.optBoolean("r18", false);
            bgImage = root.optString("bgImage", "");

            if (apis.isEmpty()) addBuiltinApis();
            migrateBuiltinApis();
            if (currentApiId.length() == 0 && !apis.isEmpty()) currentApiId = apis.get(0).id;
        } catch (Exception e) {
            Log.w(TAG, "load failed", e);
            if (apis.isEmpty()) addBuiltinApis();
        }
    }

    private void seedDefaults() {
        addBuiltinApis();
        if (!apis.isEmpty()) currentApiId = apis.get(0).id;
        save();
    }

    /** 内置通道：第一个是免费公共接口（不稳定），第二个是电脑上自己跑的模型 */
    private void addBuiltinApis() {
        Model.Api free = new Model.Api();
        free.id = "builtin-free";
        free.name = "免费公共接口";
        free.baseUrl = "https://text.pollinations.ai/openai";
        free.model = "openai";
        free.builtin = true;
        free.free = true;
        free.temperature = 0.9f;
        apis.add(free);

        Model.Api exp = new Model.Api();
        exp.id = "builtin-limited";
        exp.name = LOCAL_NAME;
        // 电脑上跑 chat_server.py 时，cmd 窗口里会打印这个地址
        exp.baseUrl = LOCAL_URL;
        exp.model = LOCAL_MODEL;
        exp.builtin = true;
        exp.experimental = true;
        apis.add(exp);
    }

    /** 电脑上跑模型的地址；换网络环境时改这里 */
    public static final String LOCAL_NAME = "限时通道";
    public static final String LOCAL_URL = "http://192.168.1.2:8000/v1";
    public static final String LOCAL_MODEL = "qwen3-14b";

    /**
     * 内置通道如果是老版本留下的占位记录（没填地址，或者名字还叫「待接入」
     * 「限时通道（我自己电脑上的模型）」这种），就刷新成现在的默认值 ——
     * 否则老用户升级上来会一直连不上。用户自己改过地址的不会被覆盖。
     */
    private void migrateBuiltinApis() {
        boolean dirty = false;
        for (Model.Api a : apis) {
            if (!a.builtin || !"builtin-limited".equals(a.id)) continue;
            String u = a.baseUrl == null ? "" : a.baseUrl.trim();
            boolean placeholder = u.length() == 0 || u.contains("127.0.0.1")
                    || u.contains("localhost") || u.contains("0.0.0.0");
            if (placeholder) {
                a.baseUrl = LOCAL_URL;
                if (a.model == null || a.model.length() == 0) a.model = LOCAL_MODEL;
            }
            String n = a.name == null ? "" : a.name.trim();
            if (n.length() == 0 || !n.equals(LOCAL_NAME)) a.name = LOCAL_NAME;
            a.builtin = true;
            a.experimental = true;
            if (placeholder) dirty = true;
        }
        if (dirty) save();
    }

    public synchronized void save() {
        try {
            JSONObject root = new JSONObject();

            JSONObject p = new JSONObject();
            p.put("name", profile.name);
            p.put("gender", profile.gender);
            p.put("bio", profile.bio);
            p.put("avatar", profile.avatar);
            p.put("image", profile.image);
            root.put("profile", p);

            JSONArray ja = new JSONArray();
            for (Model.Agent a : agents) {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("name", a.name);
                o.put("gender", a.gender);
                o.put("persona", a.persona);
                o.put("memo", a.memo);
                o.put("bio", a.bio);
                o.put("greeting", a.greeting);
                o.put("image", a.image);
                o.put("avatar", a.avatar);
                o.put("createdAt", a.createdAt);
                o.put("updatedAt", a.updatedAt);
                o.put("fromImport", a.fromImport);
                o.put("useGlobalUser", a.useGlobalUser);
                if (a.userProfile != null) {
                    JSONObject up = new JSONObject();
                    up.put("name", a.userProfile.name);
                    up.put("gender", a.userProfile.gender);
                    up.put("bio", a.userProfile.bio);
                    up.put("avatar", a.userProfile.avatar);
                    up.put("image", a.userProfile.image);
                    o.put("userProfile", up);
                }
                ja.put(o);
            }
            root.put("agents", ja);

            JSONArray jp = new JSONArray();
            for (Model.Api a : apis) {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("name", a.name);
                o.put("baseUrl", a.baseUrl);
                o.put("apiKey", a.apiKey);
                o.put("model", a.model);
                o.put("builtin", a.builtin);
                o.put("free", a.free);
                o.put("experimental", a.experimental);
                o.put("temperature", a.temperature);
                jp.put(o);
            }
            root.put("apis", jp);
            root.put("currentApiId", currentApiId);
            root.put("dark", dark);
            root.put("glass", glass);
            root.put("r18", r18);
            root.put("bgImage", bgImage);

            FileOutputStream out = new FileOutputStream(dataFile());
            out.write(root.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
        }
    }

    // ---------------- 便捷方法 ----------------

    public Model.Agent findAgent(String id) {
        for (Model.Agent a : agents) if (a.id.equals(id)) return a;
        return null;
    }

    public Model.Api findApi(String id) {
        for (Model.Api a : apis) if (a.id.equals(id)) return a;
        return null;
    }

    public Model.Api currentApi() {
        Model.Api a = findApi(currentApiId);
        if (a == null && !apis.isEmpty()) a = apis.get(0);
        return a;
    }

    /** 这个角色聊天时，「你」用哪份设定 */
    public Model.Profile effectiveUser(Model.Agent agent) {
        if (agent != null && !agent.useGlobalUser && agent.userProfile != null) {
            return agent.userProfile;
        }
        return profile;
    }

    public void putAgent(Model.Agent a) {
        if (a.id == null || a.id.length() == 0) a.id = UUID.randomUUID().toString();
        Model.Agent old = findAgent(a.id);
        if (old == null) {
            a.createdAt = System.currentTimeMillis();
            agents.add(0, a);
        } else {
            int idx = agents.indexOf(old);
            agents.set(idx, a);
        }
        a.updatedAt = System.currentTimeMillis();
        save();
    }

    public void removeAgent(String id) {
        Model.Agent a = findAgent(id);
        if (a != null) {
            agents.remove(a);
            save();
        }
    }
}
