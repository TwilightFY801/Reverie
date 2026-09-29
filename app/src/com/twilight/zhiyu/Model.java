package com.twilight.zhiyu;

/** 纯数据模型：智能体 / API 配置 / 用户全局角色 */
public class Model {

    /** 一个智能体（角色） */
    public static class Agent {
        public String id = "";
        public String name = "";             // 名称
        public String gender = GENDER_SECRET;// 性别
        public String persona = "";          // 智能体系设定：仅编辑页可见
        public String memo = "";             // 谨记：对外展示
        public String bio = "";              // 简介
        public String greeting = "";         // 开场白
        public String image = "";            // 主图（大图）文件名
        public String avatar = "";           // 1:1 裁剪头像文件名
        public long createdAt = 0L;
        public long updatedAt = 0L;
        public boolean fromImport = false;   // 是否由 zip 导入而来

        /** 用户设定：默认用全局的；关掉就用下面这份角色专用的 */
        public boolean useGlobalUser = true;
        public Profile userProfile = null;

        public Agent copy() {
            Agent a = new Agent();
            a.id = id; a.name = name; a.gender = gender; a.persona = persona;
            a.memo = memo; a.bio = bio; a.greeting = greeting;
            a.image = image; a.avatar = avatar;
            a.createdAt = createdAt; a.updatedAt = updatedAt; a.fromImport = fromImport;
            a.useGlobalUser = useGlobalUser;
            a.userProfile = copyProfile(userProfile);
            return a;
        }
    }

    public static Profile copyProfile(Profile p) {
        if (p == null) return null;
        Profile q = new Profile();
        q.name = p.name; q.gender = p.gender; q.bio = p.bio;
        q.avatar = p.avatar; q.image = p.image;
        return q;
    }

    /** 一个 API 通道 */
    public static class Api {
        public String id = "";
        public String name = "";
        public String baseUrl = "";    // 例如 https://xxx/v1
        public String apiKey = "";
        public String model = "";
        public boolean builtin = false; // 内置
        public boolean free = false;    // 内置免费公共通道
        public boolean experimental = false; // 预留：稍慢但好用、限时可用
        public float temperature = 0.9f;
    }

    /** 用户自己的全局角色 */
    public static class Profile {
        public String name = "";
        public String gender = GENDER_SECRET;
        public String bio = "";
        public String avatar = "";   // 1:1 头像
        public String image = "";    // 原图
    }

    public static final String GENDER_MALE = "男";
    public static final String GENDER_FEMALE = "女";
    public static final String GENDER_SECRET = "暂不透露";
    public static final String[] GENDERS = {GENDER_MALE, GENDER_FEMALE, GENDER_SECRET};
}
