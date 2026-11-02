package com.tungsten.fcl.game.sdl

import android.app.Activity
import android.view.Surface
import android.view.ViewGroup

/** RCL：手机版不打包 SDL，保留 FCL 按键系统调用的接口，始终处于未启用状态。 */
object SdlBridge {
    @JvmStatic
    @Volatile
    var sdlEnabled: Boolean = false

    @JvmStatic fun setupJNI(): Boolean = false
    @JvmStatic fun markSdlInitialized(): Boolean = false
    @JvmStatic fun clearSdlInitialized() {}
    @JvmStatic fun getSdlImeAutoShowEnabled(): Boolean = false
    @JvmStatic fun prepareSurface(activity: Activity, surface: Surface, layout: ViewGroup?, source: Any? = null) {}
    @JvmStatic fun isSdlRenderActive(): Boolean = false
    @JvmStatic fun syncResolution(width: Int, height: Int) {}
    @JvmStatic fun isNativeTextInputActive(): Boolean = false
    @JvmStatic fun reset() {}
}
