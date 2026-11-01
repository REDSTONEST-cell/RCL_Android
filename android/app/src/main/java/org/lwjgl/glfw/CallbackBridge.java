package org.lwjgl.glfw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.redstonest.redcraft.GameActivity;

/** 与 FCL input_bridge_v3.c 的 JNI 接口保持一致。 */
public final class CallbackBridge {
    public static volatile boolean grabbing;
    public static native void nativeSetUseInputStackQueue(boolean value);
    public static native boolean nativeSendChar(char value);
    public static native boolean nativeSendCharMods(char value, int mods);
    public static native void nativeSendKey(int key, int scancode, int action, int mods);
    public static native void nativeSendCursorPos(float x, float y);
    public static native void nativeSendMouseButton(int button, int action, int mods);
    public static native void nativeResetInputState();
    public static native void nativeSendScroll(double x, double y);
    public static native void nativeSendScreenSize(int width, int height);
    public static native void setupBridgeWindow(Object surface);
    public static void onGrabStateChanged(boolean value) { grabbing = value; }
    public static void onDirectInputEnable() {}
    public static boolean notifyLauncher(int type, int[] actions) { return false; }
    public static String accessAndroidClipboard(int action, String value) {
        GameActivity activity = GameActivity.current;
        if (activity == null) return "";
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (action == 2000) {
            activity.runOnUiThread(() -> clipboard.setPrimaryClip(ClipData.newPlainText("Minecraft", value)));
            return "";
        }
        // 系统剪贴板由游戏画面在前台时访问。
        if (action == 2001 && clipboard.hasPrimaryClip())
            return clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity).toString();
        return "";
    }
    static { System.loadLibrary("pojavexec"); }
}
