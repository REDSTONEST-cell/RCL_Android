package com.tungsten.fcl.game.sdl

import android.app.Activity

/** RCL：没有 SDL 直连手柄模式，不需要提示选择手柄输入方式。 */
class GamepadModePromptDialog {
    companion object {
        @JvmStatic
        fun checkAndShow(activity: Activity): Boolean = false
    }
}
