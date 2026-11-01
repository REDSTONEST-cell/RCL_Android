package com.redstonest.redcraft;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;

public final class GameHost {
    private final Activity activity;
    public GameHost(Activity activity) { this.activity = activity; }
    @JavascriptInterface public void captureExit(String session, int pid) {
        if (!session.matches("[0-9a-f-]{36}") || pid <= 0) return;
        String message;
        try {
            if (Build.VERSION.SDK_INT < 30) message = "当前 Android 版本不提供历史进程退出信息";
            else {
                ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                message = "系统暂未提供该游戏进程的退出记录";
                for (android.app.ApplicationExitInfo info : manager.getHistoricalProcessExitReasons(activity.getPackageName(),pid,3)) {
                    if (!(activity.getPackageName() + ":jvm").equals(info.getProcessName())) continue;
                    message = "Android 游戏进程退出记录：reason=" + info.getReason() + ", status=" + info.getStatus() + ", description=" + info.getDescription();
                    break;
                }
            }
        } catch (Exception error) { message = "无法读取系统退出记录：" + error.getClass().getSimpleName(); }
        File log = new File(activity.getFilesDir(), "game-session/" + session + "/game.log");
        try (FileOutputStream out = new FileOutputStream(log,true)) {
            out.write(("\n[RCL] " + message + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
    @JavascriptInterface public void shareLog(String text) {
        if (text.length() > 4 * 1024 * 1024) return;
        activity.runOnUiThread(() -> {
            try {
                File file = new File(activity.getCacheDir(), "RCL-game.log");
                Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(share, "导出游戏日志"));
            } catch (Exception error) {
                android.widget.Toast.makeText(activity,"无法导出日志：" + error.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }
    @JavascriptInterface public void start(String session) {
        if (!session.matches("[0-9a-f-]{36}")) return;
        activity.runOnUiThread(() -> activity.startActivity(
            new Intent(activity, GameActivity.class).putExtra("session", session)));
    }
    @JavascriptInterface public void stop() {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager.getRunningAppProcesses() == null) return;
        for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
            if ((activity.getPackageName() + ":jvm").equals(process.processName))
                android.os.Process.killProcess(process.pid);
        }
    }
}
