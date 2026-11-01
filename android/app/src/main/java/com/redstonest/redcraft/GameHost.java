package com.redstonest.redcraft;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;

public final class GameHost {
    private final Activity activity;
    public GameHost(Activity activity) { this.activity = activity; }
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
