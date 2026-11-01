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
