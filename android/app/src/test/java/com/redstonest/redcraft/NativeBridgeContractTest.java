package com.redstonest.redcraft;

import com.tungsten.fclauncher.bridge.FCLBridge;
import com.oracle.dalvik.VMLauncher;
import org.lwjgl.glfw.CallbackBridge;
import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

/** 对照固定版本 FCL 的 JNI_OnLoad 和 RegisterNatives 检查宿主 ABI。
 * 使用类字面量和反射，不在宿主 JVM 中初始化 Android 本地库。 */
public class NativeBridgeContractTest {
    private void method(Class<?> owner, String name, Class<?> result, boolean isStatic, boolean isNative, Class<?>... args) throws Exception {
        Method method = owner.getDeclaredMethod(name,args);
        assertEquals(name,result,method.getReturnType());
        assertEquals(name,isStatic,Modifier.isStatic(method.getModifiers()));
        assertEquals(name,isNative,Modifier.isNative(method.getModifiers()));
    }
    @Test public void awtCallbacks() throws Exception {
        method(FCLBridge.class,"openLink",void.class,true,false,String.class);
        method(FCLBridge.class,"querySystemClipboard",void.class,true,false);
        method(FCLBridge.class,"putClipboardData",void.class,true,false,String.class,String.class);
        method(FCLBridge.class,"nativeClipboardReceived",void.class,true,true,String.class,String.class);
    }
    @Test public void launcherCallbacks() throws Exception {
        method(FCLBridge.class,"receiveLog",void.class,false,false,String.class);
        method(FCLBridge.class,"onExit",void.class,false,false,int.class);
        method(VMLauncher.class,"launchJVM",int.class,true,true,String[].class);
    }
    @Test public void inputCallbacks() throws Exception {
        method(CallbackBridge.class,"accessAndroidClipboard",String.class,true,false,int.class,String.class);
        method(CallbackBridge.class,"onGrabStateChanged",void.class,true,false,boolean.class);
        method(CallbackBridge.class,"onDirectInputEnable",void.class,true,false);
        method(CallbackBridge.class,"notifyLauncher",boolean.class,true,false,int.class,int[].class);
        method(CallbackBridge.class,"nativeSetUseInputStackQueue",void.class,true,true,boolean.class);
        method(CallbackBridge.class,"nativeSendChar",boolean.class,true,true,char.class);
        method(CallbackBridge.class,"nativeSendCharMods",boolean.class,true,true,char.class,int.class);
        method(CallbackBridge.class,"nativeSendKey",void.class,true,true,int.class,int.class,int.class,int.class);
        method(CallbackBridge.class,"nativeSendCursorPos",void.class,true,true,float.class,float.class);
        method(CallbackBridge.class,"nativeSendMouseButton",void.class,true,true,int.class,int.class,int.class);
        method(CallbackBridge.class,"nativeResetInputState",void.class,true,true);
        method(CallbackBridge.class,"nativeSendScroll",void.class,true,true,double.class,double.class);
        method(CallbackBridge.class,"nativeSendScreenSize",void.class,true,true,int.class,int.class);
    }
}
