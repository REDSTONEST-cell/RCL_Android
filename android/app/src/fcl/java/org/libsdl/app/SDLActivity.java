package org.libsdl.app;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;

/** RCL：手机版不打包 SDL。FCL 按键系统只在 SDL 启用时调用这些方法，这里全部为空实现。 */
public final class SDLActivity {
    private SDLActivity() {
    }

    public static boolean forwardGenericMotionToSDL(View view, MotionEvent event) {
        return false;
    }

    public static Object getSDLSurface() {
        return null;
    }

    public static void commitTextInput(String text) {
    }

    public static void enableSDLEditKeyboard() {
    }

    public static void disableSDLEditKeyboard() {
    }

    public static boolean isSDLEditKeyboardShown() {
        return false;
    }

    public static void onNativeKeyDown(int keycode) {
    }

    public static void onNativeKeyUp(int keycode) {
    }

    public static void onNativeMouse(int button, int action, float x, float y, boolean relative) {
    }

    public static boolean isTextInputEvent(KeyEvent event) {
        return false;
    }

    public static boolean handleKeyEvent(View v, int keyCode, KeyEvent event, InputConnection ic) {
        return false;
    }
}
