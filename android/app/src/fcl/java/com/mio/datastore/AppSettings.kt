package com.mio.datastore

import android.content.Context

// RCL：FCL 原本用 DataStore 保存整份启动器设置，这里只保留游戏内快捷栏需要的两项，存进 SharedPreferences。
data class GameItemBarSetting(val slideSelection: Boolean, val doubleTapSwapHands: Boolean)

object GameItemBarSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("game_item_bar", Context.MODE_PRIVATE)

    fun load(context: Context) = prefs(context).let {
        GameItemBarSetting(it.getBoolean("slideSelection", true), it.getBoolean("doubleTapSwapHands", true))
    }

    fun save(context: Context, setting: GameItemBarSetting) {
        prefs(context).edit()
            .putBoolean("slideSelection", setting.slideSelection)
            .putBoolean("doubleTapSwapHands", setting.doubleTapSwapHands)
            .apply()
    }
}
