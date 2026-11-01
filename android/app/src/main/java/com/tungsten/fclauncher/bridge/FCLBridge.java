package com.tungsten.fclauncher.bridge;

import com.redstonest.redcraft.GameActivity;

/** FCL 本地启动层所需的最小宿主接口，Java 类名由上游 JNI 导出符号决定。 */
public final class FCLBridge {
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
