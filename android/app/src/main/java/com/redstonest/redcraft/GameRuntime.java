package com.redstonest.redcraft;

import android.content.Context;
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
            try (InputStream in = context.getAssets().open(source); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
            if (!target.setExecutable(true, true)) throw new IOException("无法设置运行时文件权限");
        }
    }
    static File install(Context context) throws IOException {
        // 每个应用版本使用自己的目录，避免更新中断留下不完整的运行时。
        File root = new File(context.getFilesDir(), "runtime-" + BuildConfig.VERSION_CODE);
        File marker = new File(root, ".ready");
        if (!marker.isFile()) {
            copyAssets(context, "runtime", root);
            if (!new File(root, "java17/lib/server/libjvm.so").isFile()) throw new IOException("Java 17 运行时不完整");
            Files.write(marker.toPath(), new byte[]{1});
        }
        return root;
    }
    static int launch(GameActivity activity, JSONObject request, File root, File log) throws Exception {
        String java = new File(root, "java17").getAbsolutePath();
        String nativeDir = activity.getApplicationInfo().nativeLibraryDir;
        String natives = new File(root, "natives").getAbsolutePath();
        String libraryPath = NativeLibraryPaths.linker(java,natives,nativeDir);
        String environmentPath = NativeLibraryPaths.environment(natives,nativeDir);
        FCLBridge bridge = new FCLBridge();
        String[][] env = {
            {"JAVA_HOME", java}, {"HOME", request.getString("cwd")}, {"TMPDIR", activity.getCacheDir().getAbsolutePath()},
            {"LD_LIBRARY_PATH", environmentPath}, {"PATH", java + "/bin:/system/bin"},
            {"FCL_NATIVEDIR", nativeDir}, {"POJAV_NATIVEDIR", nativeDir},
            {"POJAV_RENDERER", "opengles2"}, {"POJAVEXEC_EGL", "libEGL.so"},
            {"LIBGL_ES", "2"}, {"LIBGL_MIPMAP", "3"}, {"LIBGL_NORMALIZE", "1"}, {"LIBGL_NOERROR", "1"},
            {"ALSOFT_DRIVERS", "opensl"}, {"FORCE_VSYNC", "false"}
        };
        for (String[] pair : env) bridge.setenv(pair[0], pair[1]);
        Class.forName("org.lwjgl.glfw.CallbackBridge");
        activity.nativeReady = true;
        org.lwjgl.glfw.CallbackBridge.nativeSetUseInputStackQueue(true);
        bridge.setenv("DALVIK_JAVAVM", Long.toString(FCLBridge.getJavaVMPointer()));
        bridge.setenv("DALVIK_APPLICATION", FCLBridge.jObjectToString(activity.getApplicationContext()));
        bridge.setLdLibraryPath(libraryPath);
        if (bridge.redirectStdio(log.getAbsolutePath()) != 0) throw new IOException("无法创建游戏日志");
        if (bridge.chdir(request.getString("cwd")) != 0) throw new IOException("无法进入游戏目录");
        for (String lib : new String[]{"libjli.so", "server/libjvm.so", "libfreetype.so", "libverify.so", "libjava.so", "libnet.so", "libnio.so", "libawt.so", "libawt_headless.so", "libfontmanager.so"})
            bridge.dlopen(java + "/lib/" + lib);
        if (bridge.dlopen(nativeDir + "/libgl4es_114.so") == 0) throw new IOException("无法加载 GL4ES 渲染器");
        if (bridge.dlopen(nativeDir + "/libopenal.so") == 0) throw new IOException("无法加载 OpenAL 音频库");
        activity.appendLog("[RCL] 准备绑定游戏 Surface\n");
        if (activity.surface == null || !activity.surface.isValid()) throw new IOException("游戏画面 Surface 已失效");
        org.lwjgl.glfw.CallbackBridge.setupBridgeWindow(activity.surface);
        activity.appendLog("[RCL] Surface 绑定完成，准备设置画面尺寸\n");
        org.lwjgl.glfw.CallbackBridge.nativeSendScreenSize(activity.width, activity.height);
        activity.appendLog("[RCL] 画面尺寸设置完成，准备生成 JVM 参数\n");
        activity.inputReady = true;
        ArrayList<String> args = new ArrayList<>();
        args.add(java + "/bin/java");
        args.add("-Djava.home=" + java);
        args.add("-Duser.home=" + request.getString("cwd"));
        args.add("-Djava.io.tmpdir=" + activity.getCacheDir().getAbsolutePath());
        args.add("-XX:ErrorFile=" + new File(log.getParentFile(), "hs_err_pid%p.log").getAbsolutePath());
        args.add("-Dorg.lwjgl.opengl.libname=" + nativeDir + "/libgl4es_114.so");
        args.add("-Dorg.lwjgl.egl.libname=libEGL.so");
        args.add("-Dorg.lwjgl.openal.libname=" + nativeDir + "/libopenal.so");
        args.add("-Dorg.lwjgl.librarypath=" + natives);
        args.add("-Djna.boot.library.path=" + natives);
        args.add("-Djna.nosys=true");
        args.add("-Djna.nounpack=true");
        args.add("-Dorg.lwjgl.system.allocator=system");
        args.add("-Dglfwstub.initEgl=false");
        args.add("-Dloader.disable_forked_guis=true");
        args.add("-Djdk.lang.Process.launchMechanism=FORK");
        args.add("-Djava.awt.headless=false");
        args.add("-Dcacio.managed.screensize=" + activity.width + "x" + activity.height);
        args.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit");
        args.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment");
        args.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager");
        args.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler");
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
        JSONArray original = request.getJSONArray("args");
        for (int i = 0; i < original.length(); i++) {
            String arg = original.getString(i);
            if (arg.equals("-cp") || arg.equals("-classpath")) {
                args.add(arg);
                ArrayList<String> cp = new ArrayList<>();
                File[] lwjgl = new File(root, "lwjgl").listFiles((dir, name) -> name.endsWith(".jar"));
                if (lwjgl != null) for (File jar : lwjgl) cp.add(jar.getAbsolutePath());
                cp.add(new File(root, "MioLaunchWrapper.jar").getAbsolutePath());
                for (String entry : original.getString(++i).split(":"))
                    if (!entry.contains("/org/lwjgl/")) cp.add(entry);
                args.add(String.join(":", cp));
                continue;
            }
            if (arg.startsWith("-Djava.library.path=")) { args.add("-Djava.library.path=" + libraryPath); continue; }
            if (arg.equals(request.getString("mainClass"))) args.add("mio.Wrapper");
            args.add(arg);
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
