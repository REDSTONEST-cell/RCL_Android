package com.redstonest.redcraft;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public final class GameHost {
    private final Activity activity;
    public GameHost(Activity activity) { this.activity = activity; }
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
