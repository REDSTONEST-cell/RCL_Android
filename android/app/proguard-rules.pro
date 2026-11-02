# Add project specific ProGuard rules here.
-keep class com.redstonest.redcraft.GameHost { *; }
-keep class com.redstonest.redcraft.GameActivity { *; }
-keep class com.tungsten.fclauncher.bridge.FCLBridge { *; }
-keep class org.lwjgl.glfw.CallbackBridge { *; }
-keep class com.oracle.dalvik.VMLauncher { *; }
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 手机版显示信息（MainActivity.kt 中的 RclDisplay），页面通过 RclDisplay.info() 读取
-keep class com.redstonest.redcraft.RclDisplay {
  public <init>(...);

  @android.webkit.JavascriptInterface public <methods>;
}

# 游戏内按键系统（移植自 FCL）：按键布局与菜单设置通过 Gson 反射读写，
# native 层也按名称回调这些类的方法，FCL 本身也不做混淆，因此整体保留。
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.tungsten.** { *; }
-keep class com.mio.** { *; }
-keep class org.lwjgl.glfw.** { *; }
-keep class org.libsdl.** { *; }
-keep class fr.spse.gamepad_remapper.** { *; }
