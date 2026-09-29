# -*- coding: utf-8 -*-
"""
遐语 (Reverie) 构建脚本
=========================================================
不依赖 Gradle / Android Studio，直接用 Android 官方命令行工具链把 App 编出来：

    aapt2 compile  ->  aapt2 link  ->  javac  ->  d8
                   ->  塞 classes.dex  ->  zipalign  ->  apksigner

用法
----
    python build.py                      # 自动找 JDK / SDK，产物 out/Reverie.apk
    python build.py --version 1.2.0      # 指定版本名（版本号自动按语义化折算）
    python build.py --out D:\\x.apk       # 指定产物路径
    python build.py --debug              # 不裁剪、保留 -g 调试信息

环境变量（全都可以不设，脚本会自己去猜）
----
    JAVA_HOME           JDK 17+ 所在目录
    ANDROID_SDK_ROOT    Android SDK 目录（也认 ANDROID_HOME）
    REVERIE_KEYSTORE    签名密钥路径，默认 keystore/release.keystore（不存在就自动生成）
    REVERIE_KS_PASS     密钥库口令，默认 "android"
    REVERIE_KS_ALIAS    密钥别名，默认 "reverie"

依赖
----
    JDK 17 或更高（javac / keytool）
    Android SDK：platforms/android-34、build-tools/35.0.0
      （34.0.0 的 d8 处理匿名内部类会崩，所以优先用 35.0.0）

这个 App 没有用 AndroidX，也没有第三方依赖，所以只需要一个 android.jar 就能编译，
不需要联网拉 aar，也不需要 gradle 缓存。
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

try:  # Windows 控制台默认 GBK，中文输出会炸，这里强制 UTF-8
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(HERE, "app")
OUT_DIR = os.path.join(HERE, "out")
WORK = os.path.join(HERE, "build")

PKG = "com.twilight.zhiyu"
MIN_SDK = "24"
TARGET_SDK = "34"
PLATFORM = "android-34"

# javac 只认一个 android.jar 就够了（里面带整套 java.* 存根）
ANDROID_JAR = None


# --------------------------------------------------------------------------
# 找工具
# --------------------------------------------------------------------------

def _first_dir(*cands):
    for c in cands:
        if c and os.path.isdir(c):
            return os.path.abspath(c)
    return None


def find_java_home():
    env = os.environ.get("JAVA_HOME")
    if env and os.path.isfile(os.path.join(env, "bin", "javac.exe" if os.name == "nt" else "javac")):
        return env
    # 常见安装位置扫一遍，挑版本号最大的
    roots = []
    for base in ("D:\\", r"C:\Program Files\Java", r"C:\Program Files\Eclipse Adoptium",
                 r"C:\Program Files\Microsoft", "/usr/lib/jvm", "/opt/java", "/Library/Java/JavaVirtualMachines"):
        if os.path.isdir(base):
            try:
                for name in os.listdir(base):
                    p = os.path.join(base, name)
                    if os.path.isdir(p) and re.search(r"(?i)jdk|java|jbr", name):
                        roots.append(p)
            except OSError:
                pass
    for p in sorted(roots, reverse=True):
        if os.path.isfile(os.path.join(p, "bin", "javac.exe" if os.name == "nt" else "javac")):
            return p
    return None


def find_sdk():
    cands = [
        os.environ.get("ANDROID_SDK_ROOT"),
        os.environ.get("ANDROID_HOME"),
        os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk"),
        r"E:\az\sdk",
        r"D:\Android\Sdk",
        os.path.expanduser("~/Android/Sdk"),
        "/usr/lib/android-sdk",
    ]
    for c in cands:
        if c and os.path.isdir(os.path.join(c, "build-tools")):
            return os.path.abspath(c)
    return None


def pick_build_tools(sdk):
    """挑 build-tools：优先 35.x（34 的 d8 处理匿名内部类会崩），否则取版本号最大的。"""
    bt = os.path.join(sdk, "build-tools")
    vers = [v for v in os.listdir(bt) if os.path.isdir(os.path.join(bt, v))]
    if not vers:
        sys.exit("SDK 里没有 build-tools：" + bt)

    def key(v):
        nums = [int(x) for x in re.findall(r"\d+", v)[:3]]
        nums += [0] * (3 - len(nums))
        return (nums[0] != 35, nums)          # 35 排最前，其余按版本降序

    return os.path.join(bt, sorted(vers, key=key, reverse=False)[0])


def exe(path, name):
    """Windows 上 aapt2/d8/zipalign/apksigner 有的带 .exe 有的带 .bat，都兜住。"""
    for suffix in ("", ".exe", ".bat", ".cmd"):
        p = path + suffix
        if os.path.isfile(p):
            return p
    sys.exit("缺少工具：%s（在 %s 里没找到）" % (name, path))


def run(cmd, allow_fail=False, quiet=False):
    if not quiet:
        shown = " ".join(str(c) for c in cmd[:6])
        print(">>", shown, ("..." if len(cmd) > 6 else ""), flush=True)
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    env["PATH"] = os.path.join(JAVA_HOME, "bin") + os.pathsep + env.get("PATH", "")
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                       errors="replace", env=env)
    out = ((p.stdout or "") + (p.stderr or "")).strip()
    if out and not quiet:
        print(out, flush=True)
    if p.returncode != 0 and not allow_fail:
        print("\n===== 失败：%s (exit %d) =====" % (os.path.basename(str(cmd[0])), p.returncode),
              flush=True)
        if out:
            for line in out.splitlines()[-40:]:
                print("   ", line, flush=True)
        sys.exit(1)
    return p


# --------------------------------------------------------------------------
# 签名
# --------------------------------------------------------------------------

def ensure_keystore(path, alias, password):
    if os.path.isfile(path):
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    print("== 没有找到签名密钥，自动生成一份 ==", flush=True)
    print("   %s   （口令 %s / 别名 %s）" % (path, password, alias), flush=True)
    print("   ⚠ 这份密钥决定 App 能不能覆盖安装，请自己留好、不要提交到 git。", flush=True)
    run([KEYTOOL, "-genkeypair", "-keystore", path, "-alias", alias,
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
         "-storepass", password, "-keypass", password, "-storetype", "PKCS12",
         "-dname", "CN=Reverie, OU=Reverie, O=Reverie, L=, ST=, C=CN"])


# --------------------------------------------------------------------------

def main():
    global JAVA_HOME, KEYTOOL, ANDROID_JAR

    ap = argparse.ArgumentParser(description="遐语 (Reverie) 构建脚本")
    ap.add_argument("--version", help="版本名，例如 1.2.0；不传则按时间戳自动生成")
    ap.add_argument("--out", help="产物路径，默认 out/Reverie.apk")
    ap.add_argument("--debug", action="store_true", help="保留调试信息、不做 release 优化")
    args = ap.parse_args()

    t0 = time.time()

    # ---- 找工具链 ----
    JAVA_HOME = find_java_home()
    if not JAVA_HOME:
        sys.exit("找不到 JDK。装一个 JDK 17+，或者设一下 JAVA_HOME。")
    sdk = find_sdk()
    if not sdk:
        sys.exit("找不到 Android SDK。设一下 ANDROID_SDK_ROOT（里面要有 build-tools/ 和 platforms/）。")
    bt = pick_build_tools(sdk)

    platform_dir = os.path.join(sdk, "platforms", PLATFORM)
    ANDROID_JAR = os.path.join(platform_dir, "android.jar")
    if not os.path.isfile(ANDROID_JAR):
        sys.exit("缺少 %s —— 用 sdkmanager 装一下 \"platforms;%s\"。" % (ANDROID_JAR, PLATFORM))

    AAPT2 = exe(os.path.join(bt, "aapt2"), "aapt2")
    D8 = exe(os.path.join(bt, "d8"), "d8")
    ZIPALIGN = exe(os.path.join(bt, "zipalign"), "zipalign")
    APKSIGNER = exe(os.path.join(bt, "apksigner"), "apksigner")
    JAVAC = exe(os.path.join(JAVA_HOME, "bin", "javac"), "javac")
    KEYTOOL = exe(os.path.join(JAVA_HOME, "bin", "keytool"), "keytool")

    ks = os.environ.get("REVERIE_KEYSTORE") or os.path.join(HERE, "keystore", "release.keystore")
    ks_alias = os.environ.get("REVERIE_KS_ALIAS", "reverie")
    ks_pass = os.environ.get("REVERIE_KS_PASS", "android")

    print("=" * 60)
    print(" 遐语 (Reverie) 构建")
    print("=" * 60)
    print(" JDK        :", JAVA_HOME)
    print(" SDK        :", sdk)
    print(" build-tools:", os.path.basename(bt))
    print(" 源码       :", PROJ, flush=True)

    # ---- 版本号 ----
    # Android 不让你装一个 versionCode 比现有版本低的包，所以默认拿时间戳当版本号，
    # 每次构建都能直接覆盖安装；要发正式版就用 --version 指定。
    if args.version:
        ver_name = args.version.strip()
        nums = [int(x) for x in re.findall(r"\d+", ver_name)[:3]]
        nums += [0] * (3 - len(nums))
        ver_code = nums[0] * 10000 + nums[1] * 100 + nums[2]
    else:
        ver_code = int(time.time()) % 2000000000
        ver_name = "1.0.%d" % (ver_code % 10000)
    print(" 版本       : %s (%d)" % (ver_name, ver_code), flush=True)

    # ---- 清理 ----
    if os.path.isdir(WORK):
        shutil.rmtree(WORK)
    os.makedirs(WORK)
    os.makedirs(OUT_DIR, exist_ok=True)
    ensure_keystore(ks, ks_alias, ks_pass)

    res_dir = os.path.join(PROJ, "res")
    manifest = os.path.join(PROJ, "AndroidManifest.xml")
    src_dir = os.path.join(PROJ, "src")
    for p in (res_dir, manifest, src_dir):
        if not os.path.exists(p):
            sys.exit("源码不完整，缺少：" + p)

    # ---- 1) 编译资源 ----
    compiled = os.path.join(WORK, "compiled.zip")
    run([AAPT2, "compile", "--dir", res_dir, "-o", compiled])

    # ---- 2) 链接资源 + 生成 R.java ----
    base_apk = os.path.join(WORK, "base.apk")
    gen = os.path.join(WORK, "gen")
    os.makedirs(gen, exist_ok=True)
    run([AAPT2, "link", "-o", base_apk, "-I", ANDROID_JAR,
         "--manifest", manifest, "--java", gen,
         "--min-sdk-version", MIN_SDK, "--target-sdk-version", TARGET_SDK,
         "--version-code", str(ver_code), "--version-name", ver_name,
         "--no-version-vectors", compiled])

    # ---- 3) javac ----
    classes = os.path.join(WORK, "classes")
    os.makedirs(classes, exist_ok=True)
    srcs = []
    for dp, _, files in os.walk(src_dir):
        srcs += [os.path.join(dp, f) for f in files if f.endswith(".java")]
    srcs += [os.path.join(dp, f)
             for dp, _, files in os.walk(gen) for f in files if f.endswith(".java")]
    if not srcs:
        sys.exit("没有找到 java 源码")
    print("\n源码 %d 个文件，开始编译…" % len(srcs), flush=True)

    # android.jar 里已经带上整套 java.* 存根，把它当 bootclasspath 就够编译了，
    # 不需要 AndroidX / core-for-system-modules 之类的额外 jar。
    javac_cmd = [JAVAC, "-encoding", "UTF-8", "-nowarn", "-source", "8", "-target", "8",
                 "-bootclasspath", ANDROID_JAR, "-d", classes] + srcs
    r = subprocess.run(javac_cmd, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    out = (r.stdout or "") + (r.stderr or "")
    if r.returncode != 0:
        print(out.strip(), flush=True)
        print("\n===== javac 编译错误 =====", flush=True)
        for line in out.splitlines():
            if ": error:" in line or "错误:" in line:
                print("   ", line, flush=True)
        sys.exit(1)

    # ---- 4) d8：class -> dex ----
    #    类文件一个个写在命令行上会顶到 Windows 8191 字符上限，所以先打成 jar 再交给 d8。
    dex = os.path.join(WORK, "dex")
    os.makedirs(dex, exist_ok=True)
    classes_jar = os.path.join(WORK, "classes.jar")
    n = 0
    with zipfile.ZipFile(classes_jar, "w", zipfile.ZIP_DEFLATED) as z:
        for dp, _, files in os.walk(classes):
            for f in files:
                if not f.endswith(".class"):
                    continue
                full = os.path.join(dp, f)
                z.write(full, os.path.relpath(full, classes).replace("\\", "/"))
                n += 1
    print("    %d 个 class -> classes.jar" % n, flush=True)
    d8_cmd = [D8, "--min-api", MIN_SDK, "--lib", ANDROID_JAR, "--output", dex, classes_jar]
    if not args.debug:
        d8_cmd.insert(1, "--release")
    run(d8_cmd)

    # ---- 5) 把 dex 塞进 apk ----
    unsigned = os.path.join(WORK, "unsigned.apk")
    shutil.copyfile(base_apk, unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(os.listdir(dex)):
            if f.endswith(".dex"):
                z.write(os.path.join(dex, f), f)

    # ---- 6) zipalign ----
    aligned = os.path.join(WORK, "aligned.apk")
    run([ZIPALIGN, "-f", "-p", "4", unsigned, aligned])

    # ---- 7) 签名（v1 + v2 + v3 都开，老机器新机器都能装）----
    final = args.out or os.path.join(OUT_DIR, "Reverie.apk")
    final = os.path.abspath(final)
    os.makedirs(os.path.dirname(final), exist_ok=True)
    run([APKSIGNER, "sign", "--ks", ks, "--ks-key-alias", ks_alias,
         "--ks-pass", "pass:" + ks_pass, "--key-pass", "pass:" + ks_pass,
         "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true", "--out", final, aligned])

    # ---- 校验 ----
    v = run([APKSIGNER, "verify", "--print-certs", final], quiet=True)
    who = ""
    for line in v.stdout.splitlines():
        if "Signer #1 certificate DN" in line:
            who = line.split(":", 1)[1].strip()
    ok = v.returncode == 0

    size = os.path.getsize(final) / 1024.0
    print("\n" + "=" * 60)
    print(" 构建成功")
    print("=" * 60)
    print("  版本   : %s (%d)" % (ver_name, ver_code))
    print("  包名   : %s" % PKG)
    print("  签名   : %s" % ("通过  " + who if ok else "校验失败！"))
    print("  体积   : %.1f KB" % size)
    print("  产物   : %s" % final)
    print("  耗时   : %.1f 秒" % (time.time() - t0))
    return final


if __name__ == "__main__":
    main()
