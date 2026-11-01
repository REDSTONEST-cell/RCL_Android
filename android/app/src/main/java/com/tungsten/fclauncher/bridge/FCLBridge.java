package com.tungsten.fclauncher.bridge;

import com.redstonest.redcraft.GameActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** FCL 本地启动层所需的最小宿主接口，Java 类名由上游 JNI 导出符号决定。 */
public final class FCLBridge {
    public static native void nativeClipboardReceived(String data, String mimeType);
    public static void openLink(String link) {
        GameActivity activity = GameActivity.current;
        if (activity == null || link == null) return;
        activity.runOnUiThread(() -> {
            try {
                Uri uri = Uri.parse(link);
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "mailto".equalsIgnoreCase(scheme)) {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } else {
                    android.widget.Toast.makeText(activity,"暂不支持从游戏中打开此类文件",android.widget.Toast.LENGTH_SHORT).show();
                }
            } catch (Exception error) {
                activity.appendLog("无法打开游戏链接：" + error.getClass().getSimpleName() + "\n");
            }
        });
    }
    public static void querySystemClipboard() {
        GameActivity activity = GameActivity.current;
        if (activity == null) { nativeClipboardReceived(null, null); return; }
        activity.runOnUiThread(() -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData data = clipboard.getPrimaryClip();
                CharSequence text = data != null && data.getItemCount() > 0 ? data.getItemAt(0).coerceToText(activity) : null;
                nativeClipboardReceived(text == null ? null : text.toString(), text == null ? null : "plain");
            } catch (Exception error) {
                nativeClipboardReceived(null, null);
            }
        });
    }
    public static void putClipboardData(String data, String mimeType) {
        GameActivity activity = GameActivity.current;
        if (activity == null || data == null) return;
        activity.runOnUiThread(() -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if ("text/html".equals(mimeType)) clipboard.setPrimaryClip(ClipData.newHtmlText("Minecraft",data,data));
            else if ("text/plain".equals(mimeType) || "plain".equals(mimeType)) clipboard.setPrimaryClip(ClipData.newPlainText("Minecraft",data));
        });
    }
    public native int redirectStdio(String path);
    public native int chdir(String path);
    public native void setenv(String key, String value);
    public native long dlopen(String path);
    public native void setLdLibraryPath(String path);
    public native void setupExitTrap(FCLBridge bridge);
    public static native void initializeHooks();
    public static native long getJavaVMPointer();
    public static native String jObjectToString(Object object);
    public void receiveLog(String message) {
        if (GameActivity.current != null) GameActivity.current.appendLog(message);
    }
    public void onExit(int code) {
        if (GameActivity.current != null) GameActivity.current.exitGame(code);
    }
    public void setCursorMode(int mode) { org.lwjgl.glfw.CallbackBridge.grabbing = mode == 0; }
    static {
        System.loadLibrary("fcl");
        System.loadLibrary("pojavexec_awt");
    }
}
