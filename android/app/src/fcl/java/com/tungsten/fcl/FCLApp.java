package com.tungsten.fcl;

import android.app.Activity;
import android.content.Context;

import java.lang.ref.WeakReference;

/** RCL：FCL 的按键系统通过它取得应用 Context 与当前游戏界面，由 GameActivity 启动时设置。 */
public final class FCLApp {
    private static Context context;
    private static WeakReference<Activity> activity;

    private FCLApp() {
    }

    public static void attach(Activity current) {
        context = current.getApplicationContext();
        activity = new WeakReference<>(current);
    }

    public static Context getAppContext() {
        if (context == null) throw new IllegalStateException("FCLApp is not initialized");
        return context;
    }

    public static Activity getActivity() {
        return activity == null ? null : activity.get();
    }
}
