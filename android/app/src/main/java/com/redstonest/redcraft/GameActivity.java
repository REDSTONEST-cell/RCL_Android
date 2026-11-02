package com.redstonest.redcraft;

import android.app.AlertDialog;
import android.os.Bundle;
import com.tungsten.fcl.FCLApp;
import com.tungsten.fcl.activity.JVMActivity;
import com.tungsten.fcl.control.MenuType;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.utils.FCLPath;
import org.json.JSONObject;
import org.lwjgl.glfw.CallbackBridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 游戏画面。界面、按键与游戏内菜单使用 FCL 的 JVMActivity / GameMenu；
 * 这里负责读取启动器交给的启动请求、在 FCLBridge 的线程中启动 JVM，并把运行状态写回启动器。
 */
public final class GameActivity extends JVMActivity {
    public static volatile GameActivity current;
    private File sessionDir;
    private JSONObject request;
    private FCLBridge bridge;
    private boolean exited;

    @Override
    protected void onCreate(Bundle saved) {
        current = this;
        FCLApp.attach(this);
        FCLPath.loadPaths(this);
        JVMActivity.setFCLBridge(null, MenuType.GAME);
        Throwable failure = null;
        String session = getIntent().getStringExtra("session");
        if (session != null && session.matches("[0-9a-f-]{36}")) {
            sessionDir = new File(getFilesDir(), "game-session/" + session);
            try {
                File file = new File(sessionDir, "request.json");
                request = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                if (!file.delete()) throw new IOException("无法清除临时启动凭据");
                writeStatus("starting", null);
                bridge = new FCLBridge();
                bridge.setLogPath(new File(sessionDir, "game.log").getAbsolutePath());
                bridge.setGameDir(request.getString("cwd"));
                bridge.setJava("17");
                bridge.setRenderer("GL4ES");
                bridge.setThread(new Thread(this::runGame, "RCL-JVM"));
                JVMActivity.setFCLBridge(bridge, MenuType.GAME);
            } catch (Exception error) {
                failure = error;
            }
        }
        super.onCreate(saved);
        if (sessionDir == null) {
            finish();
        } else if (failure != null) {
            showError(failure);
        }
    }

    /** 由 FCLBridge.execute 在画面就绪后启动。 */
    private void runGame() {
        // 游戏正常退出（退出码 0）时，native 退出拦截不会回调 onExit，而是直接调用 System.exit(0)；
        // 在关闭钩子中补写退出码，否则启动器只能看到进程消失，会误判为崩溃
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (this) {
                if (exited) return;
                exited = true;
            }
            appendLog("\n[RCL] 游戏已退出，退出码 0\n");
            try {
                writeStatus("exited", 0);
            } catch (Exception ignored) {
            }
        }, "RCL-Exit"));
        try {
            File root = GameRuntime.install(this, request.optString("java", "java17"));
            writeStatus("running", null);
            int code = GameRuntime.launch(this, bridge, request, root, CallbackBridge.windowWidth, CallbackBridge.windowHeight);
            onGameExit(code);
        } catch (Throwable error) {
            showError(error);
        }
    }

    /** 写入游戏日志；游戏菜单就绪后经 FCLBridge 转发，同时显示在游戏内的日志窗口。 */
    public void appendLog(String message) {
        if (bridge != null && bridge.getCallback() != null) {
            bridge.receiveLog(message);
            return;
        }
        if (sessionDir == null) return;
        synchronized (this) {
            try (FileOutputStream stream = new FileOutputStream(new File(sessionDir, "game.log"), true)) {
                stream.write(message.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void onGameExit(int code) {
        synchronized (this) {
            if (exited) return;
            exited = true;
        }
        if (sessionDir != null) {
            File[] reports = sessionDir.listFiles((dir, name) -> name.startsWith("hs_err_pid") && name.endsWith(".log"));
            if (reports != null) for (File report : reports) {
                try (InputStream in = new FileInputStream(report)) {
                    byte[] bytes = new byte[128 * 1024];
                    int length = in.read(bytes);
                    if (length > 0) appendLog("\n[RCL] JVM 崩溃报告：" + report.getName() + "\n" + new String(bytes, 0, length, StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                }
            }
        }
        try {
            writeStatus("exited", code);
        } catch (Exception ignored) {
        }
        runOnUiThread(() -> {
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    private synchronized void writeStatus(String state, Integer code) throws Exception {
        JSONObject value = new JSONObject().put("state", state).put("pid", android.os.Process.myPid());
        if (code != null) value.put("code", code);
        File tmp = new File(sessionDir, "status.tmp");
        Files.write(tmp.toPath(), value.toString().getBytes(StandardCharsets.UTF_8));
        if (!tmp.renameTo(new File(sessionDir, "status.json"))) throw new IOException("无法保存游戏状态");
    }

    private void showError(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        appendLog("\n" + trace + "\n");
        synchronized (this) {
            exited = true;
        }
        try {
            writeStatus("exited", -1);
        } catch (Exception ignored) {
        }
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("游戏启动失败")
                .setMessage(error + "\n\n日志可在启动器中查看")
                .setCancelable(false)
                .setPositiveButton("确定", (dialog, which) -> {
                    finish();
                    android.os.Process.killProcess(android.os.Process.myPid());
                })
                .show());
    }
}
