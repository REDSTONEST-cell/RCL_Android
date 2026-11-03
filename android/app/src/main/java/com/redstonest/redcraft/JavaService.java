package com.redstonest.redcraft;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.oracle.dalvik.VMLauncher;
import com.tungsten.fcl.FCLApp;
import com.tungsten.fclauncher.utils.FCLPath;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.bridge.FCLBridgeCallback;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * 在 :jvm 进程中用应用内置的 JVM 运行一次 Java 程序（Forge / NeoForge 安装器、OptiFine 补丁程序等）。
 * 手机上不能执行下载的 Java，也不能启动子进程；与 FCL 的安装器进程相同，每次运行使用一个新进程，结束后退出。
 *
 * 启动器把请求写在 files/java-session/<会话>/request.json（java、args、cwd），本服务写入 status.json、
 * heartbeat 与 java.log。退出码为 0 时 native 层不会回调，进程直接结束，启动器以心跳判断结束并检查输出。
 */
public final class JavaService extends Service {
    private File sessionDir;
    private boolean finished;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String session = intent == null ? null : intent.getStringExtra("session");
        if (session == null || !session.matches("[0-9a-f-]{36}") || sessionDir != null) {
            // 一个进程只能创建一次 JVM，已经在运行时不接受新的请求
            return START_NOT_STICKY;
        }
        sessionDir = new File(getFilesDir(), "java-session/" + session);
        // FCL 的部分类（如 SdlSettings）在初始化时读取应用 Context 与路径，游戏界面中由 GameActivity 设置
        FCLApp.attach(this);
        FCLPath.loadPaths(this);
        new Thread(this::run, "RCL-Java").start();
        return START_NOT_STICKY;
    }

    private void run() {
        try {
            File file = new File(sessionDir, "request.json");
            JSONObject request = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            writeStatus("running", null);
            Heartbeat.start(sessionDir);
            File root = GameRuntime.install(this, request.optString("java", "java21"));
            FCLBridge bridge = new FCLBridge();
            bridge.setLogPath(new File(sessionDir, "java.log").getAbsolutePath());
            bridge.setThread(new Thread(() -> {
                try {
                    finish(launch(bridge, request, root));
                } catch (Throwable error) {
                    log("\n" + Log.getStackTraceString(error) + "\n");
                    finish(-1);
                }
            }, "RCL-JVM"));
            bridge.execute(null, new FCLBridgeCallback() {
                @Override
                public void onCursorModeChange(int mode) {
                }

                @Override
                public void onLog(String text) {
                    log(text);
                }

                @Override
                public void onExit(int code) {
                    finish(code);
                }
            });
        } catch (Throwable error) {
            log("\n" + Log.getStackTraceString(error) + "\n");
            finish(-1);
        }
    }

    private int launch(FCLBridge bridge, JSONObject request, File root) throws Exception {
        String java = new File(root, request.optString("java", "java21")).getAbsolutePath();
        String nativeDir = getApplicationInfo().nativeLibraryDir;
        String cwd = request.getString("cwd");
        GameRuntime.patchJava(new File(java), nativeDir);
        String natives = new File(root, "natives").getAbsolutePath();
        bridge.setenv("JAVA_HOME", java);
        bridge.setenv("HOME", cwd);
        bridge.setenv("TMPDIR", getCacheDir().getAbsolutePath());
        bridge.setenv("LD_LIBRARY_PATH", NativeLibraryPaths.environment(natives, nativeDir));
        bridge.setenv("PATH", java + "/bin:/system/bin");
        bridge.setLdLibraryPath(NativeLibraryPaths.linker(java, natives, nativeDir));
        if (bridge.chdir(cwd) != 0) throw new IOException("无法进入工作目录 " + cwd);
        for (String lib : new String[]{"libjli.so", "server/libjvm.so", "libverify.so", "libjava.so", "libnet.so", "libnio.so"})
            bridge.dlopen(java + "/lib/" + lib);
        File resolv = GameRuntime.writeResolvConf(this, root);
        ArrayList<String> args = new ArrayList<>();
        args.add(java + "/bin/java");
        args.add("-Djava.home=" + java);
        args.add("-Xmx" + heapMb() + "m");
        args.add("-Duser.home=" + cwd);
        args.add("-Djava.io.tmpdir=" + getCacheDir().getAbsolutePath());
        args.add("-Djava.awt.headless=true");
        args.add("-Dext.net.resolvPath=" + resolv.getAbsolutePath());
        args.add("-Dos.name=Linux");
        args.add("-Djdk.lang.Process.launchMechanism=FORK");
        args.add("-XX:ErrorFile=" + new File(sessionDir, "hs_err_pid%p.log").getAbsolutePath());
        JSONArray extra = request.getJSONArray("args");
        for (int i = 0; i < extra.length(); i++) args.add(extra.getString(i));
        log("[RCL] 运行 " + String.join(" ", args.subList(args.size() - extra.length(), args.size())) + "\n");
        bridge.setupExitTrap(bridge);
        FCLBridge.initializeHooks();
        return VMLauncher.launchJVM(args.toArray(new String[0]));
    }

    /** 安装器（如 Forge 的二进制补丁）需要较多内存：取设备内存的四分之一，在 1 ~ 2GB 之间。 */
    private int heapMb() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        long mb = info.totalMem / 1024 / 1024 / 4;
        return (int) Math.max(1024, Math.min(2048, mb));
    }

    private synchronized void log(String text) {
        try (FileOutputStream out = new FileOutputStream(new File(sessionDir, "java.log"), true)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private void finish(int code) {
        synchronized (this) {
            if (finished) return;
            finished = true;
        }
        try {
            writeStatus("exited", code);
        } catch (Exception ignored) {
        }
        // 一个进程只能创建一次 JVM，结束后退出进程，下一次运行使用新进程
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private synchronized void writeStatus(String state, Integer code) throws Exception {
        JSONObject value = new JSONObject().put("state", state).put("pid", android.os.Process.myPid());
        if (code != null) value.put("code", code);
        File tmp = new File(sessionDir, "status.tmp");
        Files.write(tmp.toPath(), value.toString().getBytes(StandardCharsets.UTF_8));
        if (!tmp.renameTo(new File(sessionDir, "status.json"))) throw new IOException("无法保存运行状态");
    }
}
