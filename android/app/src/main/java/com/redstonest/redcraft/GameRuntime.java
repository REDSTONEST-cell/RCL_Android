package com.redstonest.redcraft;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import com.oracle.dalvik.VMLauncher;
import com.tungsten.fclauncher.bridge.FCLBridge;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

final class GameRuntime {
    private static void copyAssets(Context context, String source, File target) throws IOException {
        String[] children = context.getAssets().list(source);
        if (children != null && children.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建运行时目录");
            for (String child : children) copyAssets(context, source + "/" + child, new File(target, child));
        } else {
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建运行时目录");
            try (InputStream in = context.getAssets().open(source); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
            if (!target.setExecutable(true, true)) throw new IOException("无法设置运行时文件权限");
        }
    }
    /** APK 中打包的 Java 运行时（目录名）。 */
    static final String[] JAVA_RUNTIMES = {"java17", "java21", "java25"};

    /**
     * 运行时内容的标识（打包时由 prepare-runtime.py 生成）。运行时没有变化时，更新应用后继续使用已经解压的运行时。
     */
    private static String runtimeId(Context context) {
        try (InputStream in = context.getAssets().open("runtime/runtime-id.txt")) {
            byte[] bytes = new byte[64];
            int length = in.read(bytes);
            String id = length > 0 ? new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8).trim() : "";
            if (id.matches("[0-9a-f]{6,64}")) return id;
        } catch (IOException ignored) {
        }
        return String.valueOf(BuildConfig.VERSION_CODE);
    }

    /**
     * 把运行时从 APK 复制到应用目录。每个运行时版本使用自己的目录，避免更新中断留下不完整的运行时；
     * 各个 Java 只在第一次使用时复制，旧版本留下的运行时目录一并删除。
     */
    static File install(Context context, String java) throws IOException {
        if (!Arrays.asList(JAVA_RUNTIMES).contains(java)) throw new IOException("不支持的 Java 运行时：" + java);
        File root = new File(context.getFilesDir(), "runtime-" + runtimeId(context));
        File[] old = context.getFilesDir().listFiles((dir, name) -> name.startsWith("runtime-") && !name.equals(root.getName()));
        if (old != null) for (File dir : old) deleteTree(dir);
        File marker = new File(root, ".ready");
        if (!marker.isFile()) {
            String[] children = context.getAssets().list("runtime");
            if (children == null || children.length == 0) throw new IOException("APK 中缺少游戏运行时");
            if (!root.isDirectory() && !root.mkdirs()) throw new IOException("无法创建运行时目录");
            for (String child : children) {
                if (child.startsWith("java")) continue;
                copyAssets(context, "runtime/" + child, new File(root, child));
            }
            Files.write(marker.toPath(), new byte[]{1});
        }
        File javaMarker = new File(root, java + "/.ready");
        if (!javaMarker.isFile()) {
            deleteTree(new File(root, java));
            copyAssets(context, "runtime/" + java, new File(root, java));
            if (!new File(root, java + "/lib/server/libjvm.so").isFile()) throw new IOException(java + " 运行时不完整");
            Files.write(javaMarker.toPath(), new byte[]{1});
        }
        return root;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    /**
     * Android 没有 /etc/resolv.conf，游戏内的 Java DNS 查询会失败（服务器、皮肤等）。
     * 与 FCL 相同：写一份 resolv.conf，通过 -Dext.net.resolvPath 交给 JRE；先用系统当前的 DNS，再补充公共 DNS。
     */
    static File writeResolvConf(Context context, File root) {
        LinkedHashSet<String> servers = new LinkedHashSet<>();
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = manager.getActiveNetwork();
            LinkProperties link = network == null ? null : manager.getLinkProperties(network);
            if (link != null) for (java.net.InetAddress address : link.getDnsServers()) {
                // JRE 的 DNS 解析无法处理裸 IPv6 地址，只保留 IPv4
                if (address instanceof java.net.Inet4Address) servers.add(address.getHostAddress());
            }
        } catch (Exception ignored) {
        }
        Collections.addAll(servers, "223.5.5.5", "119.29.29.29", "8.8.8.8", "1.1.1.1");
        StringBuilder text = new StringBuilder();
        for (String server : servers) text.append("nameserver ").append(server).append('\n');
        File file = new File(root, "resolv.conf");
        try {
            Files.write(file.toPath(), text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        return file;
    }
    /**
     * 与 FCL 的 RuntimeUtils.patchJava 相同：JRE 自带的 libawt_xawt.so 依赖桌面系统的 X11，在手机上无法加载，
     * cacio 初始化 AWT 时会因此让 JVM 直接退出；用 APK 中编译的替代实现覆盖它。libjsound.so 同样替换为
     * 使用 OpenAL 的实现。
     */
    static void patchJava(File java, String nativeDir) throws IOException {
        for (String name : new String[]{"libawt_xawt.so", "libjsound.so"}) {
            File source = new File(nativeDir, name);
            if (!source.isFile()) {
                if (name.equals("libawt_xawt.so")) throw new IOException("APK 中缺少 " + name);
                continue;
            }
            File target = new File(java, "lib/" + name);
            if (target.isFile() && target.length() == source.length()) continue;
            if (target.exists() && !target.delete()) throw new IOException("无法替换 " + target);
            Files.copy(source.toPath(), target.toPath());
        }
    }

    /**
     * OpenAL Soft 的配置：加大输出缓冲（每段 1024 帧 × 4 段，48kHz 下约 85 毫秒）。
     * 混音线程在 CPU 繁忙时稍有延迟，缓冲太小时就会播放中断，声音变成快速的「哒哒哒」。
     */
    private static File writeOpenAlConfig(File root) {
        File file = new File(root, "alsoft.conf");
        try {
            Files.write(file.toPath(), "[general]\nperiod_size = 1024\nperiods = 4\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        return file;
    }

    /**
     * MobileGlues 的配置：与 FCL 的默认设置相同，设备支持时使用 ANGLE（GLES on Vulkan）作为后端。
     * 直接使用驱动的 GLES 时，26.3 的部分着色器（按变量下标写输出数组）无法编译，游戏会在加载时崩溃。
     * 只补充缺少的键，不覆盖已有的设置。
     */
    private static void writeMobileGluesConfig(File mgDir) {
        File file = new File(mgDir, "config.json");
        try {
            JSONObject config = file.isFile()
                ? new JSONObject(new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8))
                : new JSONObject();
            if (config.has("enableANGLE")) return;
            config.put("enableANGLE", 1);
            Files.write(file.toPath(), config.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    /**
     * 选择与游戏使用的 JNA 版本一致的本地库（版本不一致时 JNA 会报「incompatible JNA native library」）。
     * 没有完全相同的版本时，选择不低于它的最近版本，再不行用最新的。
     */
    private static String jnaDirectory(File root, JSONArray args, String fallback) throws Exception {
        String wanted = null;
        for (int i = 0; i < args.length(); i++) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/net/java/dev/jna/jna/([0-9.]+)/jna-").matcher(args.getString(i));
            if (m.find()) { wanted = m.group(1); break; }
        }
        File[] dirs = new File(root, "jna").listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return fallback;
        Arrays.sort(dirs, (a, b) -> compareVersion(a.getName(), b.getName()));
        if (wanted != null) for (File dir : dirs) if (compareVersion(dir.getName(), wanted) >= 0) return dir.getAbsolutePath();
        return dirs[dirs.length - 1].getAbsolutePath();
    }

    private static int compareVersion(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? number(pa[i]) : 0, y = i < pb.length ? number(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /** 版本号中的一段；带后缀（如 0-jpms）时取开头的数字。 */
    private static int number(String part) {
        int end = 0;
        while (end < part.length() && end < 9 && Character.isDigit(part.charAt(end))) end++;
        return end == 0 ? 0 : Integer.parseInt(part.substring(0, end));
    }

    /**
     * 与 FCL 相同：26.3 起关闭 NeoForge / Forge 的 FML 早期加载画面（它在启动期反复分配图形缓冲，
     * 会与启动器界面互相阻塞）。键写在 fml.toml 开头，FML 加载后会按规范补全其余内容。
     */
    private static void disableEarlyWindow(String cwd) {
        File toml = new File(cwd, "config/fml.toml");
        try {
            StringBuilder text = new StringBuilder("earlyWindowControl = false\n");
            if (toml.isFile()) {
                for (String line : Files.readAllLines(toml.toPath(), java.nio.charset.StandardCharsets.UTF_8))
                    if (!line.strip().startsWith("earlyWindowControl")) text.append(line).append('\n');
            } else if (!toml.getParentFile().isDirectory() && !toml.getParentFile().mkdirs()) {
                return;
            }
            Files.write(toml.toPath(), text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    /** 与 FCL 相同：关闭旧版 Forge 的启动动画（它会自己创建窗口，在手机上无法显示）。 */
    private static void disableForgeSplash(String cwd) {
        File config = new File(cwd, "config");
        try {
            if (!config.isDirectory() && !config.mkdirs()) return;
            Files.write(new File(config, "splash.properties").toPath(), "enabled=false".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    /**
     * 在 FCLBridge 的游戏线程中运行：日志重定向与画面绑定已由 FCLBridge.execute 完成，
     * 这里设置环境、加载运行库并启动 JVM。width/height 为游戏窗口尺寸。
     */
    static int launch(GameActivity activity, FCLBridge bridge, JSONObject request, File root, int width, int height) throws Exception {
        String java = new File(root, request.optString("java", "java17")).getAbsolutePath();
        String nativeDir = activity.getApplicationInfo().nativeLibraryDir;
        String cwd = request.getString("cwd");
        File log = new File(bridge.getLogPath());
        patchJava(new File(java), nativeDir);
        // LWJGL：26.x 使用 3.4.1，更早的版本使用 3.3.3
        boolean lwjgl341 = "3.4.1".equals(request.optString("lwjgl", "3.3.3"));
        String natives = new File(root, lwjgl341 ? "natives-3.4.1" : "natives").getAbsolutePath();
        File lwjglDir = new File(root, lwjgl341 ? "lwjgl-3.4.1" : "lwjgl");
        JSONArray original = request.getJSONArray("args");
        String jna = jnaDirectory(root, original, natives);
        String libraryPath = NativeLibraryPaths.linker(java,natives,nativeDir);
        String environmentPath = NativeLibraryPaths.environment(natives,nativeDir);
        // 渲染器（环境变量与 FCL 相同）：GL4ES 支持到 1.21.4，NG-GL4ES 用于 1.21.5 ~ 1.21.x，26.x 使用 MobileGlues
        String renderer = request.optString("renderer", "gl4es");
        boolean ng = "ng_gl4es".equals(renderer);
        boolean mg = "mobileglues".equals(renderer);
        String rendererName = mg ? "MobileGlues" : ng ? "NG-GL4ES" : "GL4ES";
        String glLibrary = nativeDir + (mg ? "/libmobileglues.so" : ng ? "/libng_gl4es.so" : "/libgl4es_114.so");
        String eglLibrary = mg ? glLibrary : "libEGL.so";
        String[][] env = {
            {"JAVA_HOME", java}, {"HOME", cwd}, {"TMPDIR", activity.getCacheDir().getAbsolutePath()},
            {"LD_LIBRARY_PATH", environmentPath}, {"PATH", java + "/bin:/system/bin"},
            {"FCL_NATIVEDIR", nativeDir}, {"POJAV_NATIVEDIR", nativeDir}, {"POJAVEXEC_EGL", mg ? "libmobileglues.so" : "libEGL.so"},
            {"LIBGL_NORMALIZE", "1"}, {"LIBGL_NOINTOVLHACK", "1"}, {"LIBGL_NOERROR", "1"},
            {"ALSOFT_DRIVERS", "opensl"}, {"FORCE_VSYNC", "false"}
        };
        for (String[] pair : env) bridge.setenv(pair[0], pair[1]);
        bridge.setenv("ALSOFT_CONF", writeOpenAlConfig(root).getAbsolutePath());
        if (mg) {
            bridge.setenv("POJAV_RENDERER", "opengles3_mobileglues");
            bridge.setenv("LIBGL_ES", "3");
            bridge.setenv("LIBGL_EGL", "libmobileglues.so");
            // MobileGlues 的配置目录放在应用私有存储，不使用 /sdcard/MG。
            // 26.3 起需要 ANGLE 后端，与直接使用驱动 GLES 的 26.1 ~ 26.2 分开存放配置
            boolean angle = request.optBoolean("angle", false);
            File mgDir = new File(activity.getFilesDir(), angle ? "mobileglues-angle" : "mobileglues");
            if (!mgDir.isDirectory() && !mgDir.mkdirs()) throw new IOException("无法创建 MobileGlues 配置目录");
            bridge.setenv("MG_DIR_PATH", mgDir.getAbsolutePath());
            if (angle) writeMobileGluesConfig(mgDir);
        } else if (ng) {
            bridge.setenv("POJAV_RENDERER", "opengles3");
            bridge.setenv("LIBGL_ES", "3");
            bridge.setenv("LIBGL_GL", "31");
            bridge.setenv("LIBGL_USE_MC_COLOR", "1");
            bridge.setenv("DLOPEN", "libspirv-cross-c-shared.so");
        } else {
            bridge.setenv("POJAV_RENDERER", "opengles2");
            bridge.setenv("LIBGL_ES", "2");
            bridge.setenv("LIBGL_MIPMAP", "3");
        }
        // SDL3 自行创建 GL 上下文，需要渲染器 GL / EGL 库的绝对路径（与 FCL 相同：EGL 只在渲染器自带时设置）
        bridge.setenv("SDL_OPENGL_LIBRARY", glLibrary);
        if (mg) bridge.setenv("SDL_EGL_LIBRARY", glLibrary);
        org.lwjgl.glfw.CallbackBridge.nativeSetUseInputStackQueue(true);
        bridge.setenv("DALVIK_JAVAVM", Long.toString(FCLBridge.getJavaVMPointer()));
        bridge.setenv("DALVIK_APPLICATION", FCLBridge.jObjectToString(activity.getApplicationContext()));
        bridge.setLdLibraryPath(libraryPath);
        if (bridge.chdir(cwd) != 0) throw new IOException("无法进入游戏目录");
        for (String lib : new String[]{"libjli.so", "server/libjvm.so", "libfreetype.so", "libverify.so", "libjava.so", "libnet.so", "libnio.so", "libawt.so", "libawt_headless.so", "libfontmanager.so"})
            bridge.dlopen(java + "/lib/" + lib);
        if (ng && bridge.dlopen(nativeDir + "/libspirv-cross-c-shared.so") == 0) throw new IOException("无法加载 NG-GL4ES 的着色器转换库");
        if (bridge.dlopen(glLibrary) == 0) throw new IOException("无法加载 " + rendererName + " 渲染器");
        if (bridge.dlopen(nativeDir + "/libopenal.so") == 0) throw new IOException("无法加载 OpenAL 音频库");
        disableForgeSplash(cwd);
        if (request.optBoolean("disableEarlyWindow", false)) disableEarlyWindow(cwd);
        com.tungsten.fcl.setting.GameOption option = new com.tungsten.fcl.setting.GameOption(cwd);
        if (lwjgl341) {
            // 26.x 可以用 Vulkan 绘图。很多手机 GPU（如 Mali）只提供 KHR 版的 vertex_attribute_divisor，
            // 缺少游戏要求的 EXT 版；与 FCL 相同，经 vkshim 包装系统 Vulkan 补上（驱动原生支持时直通）。
            // 26.3 的新透明度着色器无法在 GLES（包括 ANGLE）上编译，固定使用 Vulkan（FCL 的「图形后端」选项）；
            // 26.1 ~ 26.2 使用 OpenGL（MobileGlues）
            bridge.setenv("VKSHIM_ENABLE", "1");
            option.set("preferredGraphicsBackend", request.optBoolean("angle", false) ? "vulkan" : "opengl");
        }
        // 与 FCL 相同：游戏上次启动中途退出时会在下次启动强制改用 OpenGL，每次启动前清除这个标记
        option.set("startedCleanly", "true");
        option.save();
        File resolv = writeResolvConf(activity, root);
        activity.appendLog("[RCL] 运行环境 " + new File(java).getName() + " · LWJGL " + (lwjgl341 ? "3.4.1" : "3.3.3") + " · " + rendererName + " · JNA " + new File(jna).getName() + "，画面尺寸 " + width + "x" + height + "\n");
        JSONObject memory = request.optJSONObject("memory");
        if (memory != null) activity.appendLog(String.format(Locale.ROOT, "[RCL] 游戏内存 %d MB（手机共 %.1f GB，启动前可用 %.1f GB）\n",
            memory.optLong("heap"), memory.optLong("total") / 1024.0, memory.optLong("available") / 1024.0));
        ArrayList<String> args = new ArrayList<>();
        args.add(java + "/bin/java");
        args.add("-Djava.home=" + java);
        args.add("-Duser.home=" + cwd);
        args.add("-Djava.io.tmpdir=" + activity.getCacheDir().getAbsolutePath());
        args.add("-XX:ErrorFile=" + new File(log.getParentFile(), "hs_err_pid%p.log").getAbsolutePath());
        args.add("-XX:ActiveProcessorCount=" + Runtime.getRuntime().availableProcessors());
        args.add("-Dext.net.resolvPath=" + resolv.getAbsolutePath());
        args.add("-Dos.name=Linux");
        // 与 FCL 相同：Android 上 Java 读不到系统时区与语言，不设置时游戏内的时间按 UTC 显示
        args.add("-Dos.version=Android-" + android.os.Build.VERSION.RELEASE);
        args.add("-Duser.timezone=" + TimeZone.getDefault().getID());
        args.add("-Duser.language=" + Locale.getDefault().getLanguage());
        args.add("-Duser.country=" + Locale.getDefault().getCountry());
        args.add("-Dorg.lwjgl.opengl.libname=" + glLibrary);
        args.add("-Dorg.lwjgl.egl.libname=" + eglLibrary);
        args.add("-Dorg.lwjgl.openal.libname=" + nativeDir + "/libopenal.so");
        args.add("-Dorg.lwjgl.librarypath=" + natives);
        if (new File(natives, "libfreetype.so").isFile()) args.add("-Dorg.lwjgl.freetype.libname=" + natives + "/libfreetype.so");
        args.add("-Djna.boot.library.path=" + jna);
        args.add("-Dorg.lwjgl.vulkan.libname=libvulkan.so");
        args.add("-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared");
        args.add("-Djna.nosys=true");
        args.add("-Djna.nounpack=true");
        args.add("-Dorg.lwjgl.system.allocator=system");
        args.add("-Dglfwstub.initEgl=false");
        args.add("-Dloader.disable_forked_guis=true");
        // Fabric 加载失败（如 Mod 与游戏版本不符）时不打开错误窗口（手机上无法显示，游戏会一直卡在黑屏），直接退出并留下日志
        args.add("-Dfabric.noGui=true");
        args.add("-Dfml.earlyprogresswindow=false");
        args.add("-Dsodium.checks.issue2561=false");
        args.add("-Djdk.lang.Process.launchMechanism=FORK");
        args.add("-Djava.awt.headless=false");
        args.add("-Dcacio.managed.screensize=" + width + "x" + height);
        args.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit");
        args.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment");
        args.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager");
        args.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler");
        args.add("-Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel");
        args.add("-javaagent:" + root + "/cacio/cacio-agent.jar");
        for (String pkg : new String[]{"java.awt", "java.awt.peer", "java.awt.dnd.peer", "sun.awt", "sun.awt.image", "sun.awt.event", "sun.awt.datatransfer", "sun.java2d", "sun.font"})
            args.add("--add-exports=java.desktop/" + pkg + "=ALL-UNNAMED");
        for (String pkg : new String[]{"java.base/java.util", "java.base/java.lang.reflect", "java.base/java.net", "java.desktop/java.awt", "java.desktop/sun.font", "java.desktop/sun.java2d"})
            args.add("--add-opens=" + pkg + "=ALL-UNNAMED");
        args.add("--add-exports=java.base/sun.security.action=ALL-UNNAMED");
        File[] cacio = new File(root, "cacio").listFiles((dir, name) -> name.endsWith(".jar"));
        ArrayList<String> boot = new ArrayList<>();
        if (cacio != null) for (File file : cacio) boot.add(file.getAbsolutePath());
        args.add("-Xbootclasspath/a:" + String.join(":", boot));
        String mainClass = request.getString("mainClass");
        boolean wrapped = false;
        for (int i = 0; i < original.length(); i++) {
            String arg = original.getString(i);
            if (arg.equals("-cp") || arg.equals("-classpath")) {
                args.add(arg);
                ArrayList<String> cp = new ArrayList<>();
                File[] lwjgl = lwjglDir.listFiles((dir, name) -> name.endsWith(".jar"));
                if (lwjgl != null) for (File jar : lwjgl) cp.add(jar.getAbsolutePath());
                cp.add(new File(root, "MioLaunchWrapper.jar").getAbsolutePath());
                for (String entry : original.getString(++i).split(":"))
                    if (!entry.contains("/org/lwjgl/")) cp.add(entry);
                args.add(String.join(":", cp));
                continue;
            }
            if (arg.startsWith("-Djava.library.path=")) { args.add("-Djava.library.path=" + libraryPath); continue; }
            if (!wrapped && arg.equals(mainClass)) {
                // 与 FCL 相同：由 mio.Wrapper 启动主类；主类在模块中时（Forge/NeoForge 的 BootstrapLauncher）需要导出给它
                String pkg = mainClass.substring(0, Math.max(0, mainClass.lastIndexOf('.')));
                if (!pkg.isEmpty()) {
                    args.add("--add-exports");
                    args.add(pkg + "/" + pkg + "=ALL-UNNAMED");
                }
                args.add("mio.Wrapper");
                wrapped = true;
            }
            args.add(arg);
        }
        if (!wrapped) throw new IOException("启动参数中缺少主类 " + mainClass);
        if (lwjgl341 && request.optBoolean("angle", false)) {
            // 26.3 起可以用启动参数指定图形后端，游戏内的设置会被忽略。只写 options.txt 不可靠：新建版本第一次启动时
            // options.txt 没有版本号，游戏的数据修正会把它改回「默认」（默认先试 OpenGL），Forge / NeoForge 因此
            // 用 OpenGL 加载，透明度着色器编译失败。Vulkan 不可用时游戏仍会退回 OpenGL。
            args.add("--graphicsBackend");
            args.add("vulkan");
        }
        activity.appendLog("[RCL] JVM 参数生成完成，准备设置退出回调\n");
        bridge.setupExitTrap(bridge);
        activity.appendLog("[RCL] 退出回调设置完成，准备初始化本地 hooks\n");
        FCLBridge.initializeHooks();
        activity.appendLog("[RCL] 本地 hooks 初始化完成，准备调用 JVM\n");
        activity.appendLog("[RCL] JLI 环境检查：LD_LIBRARY_PATH 不含 JRE；链接器已设置 JRE 库路径\n");
        int code = VMLauncher.launchJVM(args.toArray(new String[0]));
        activity.appendLog("[RCL] JVM 调用返回，退出码 " + code + "\n");
        return code;
    }
}
