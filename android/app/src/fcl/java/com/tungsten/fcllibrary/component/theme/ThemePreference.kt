package com.tungsten.fcllibrary.component.theme

/**
 * 主题配置。
 *
 * RCL：游戏内菜单固定使用 RCL 的主题色（与电脑版 --accent 相同），全屏显示，不保存修改。
 */
data class ThemePreference(
    val color: Int = 0xFFE8590C.toInt(),
    val colorDark: Int = 0xFFF0782F.toInt(),
    val color2: Int = 0xFF000000.toInt(),
    val color2Dark: Int = 0xFFFFFFFF.toInt(),
    val fullscreen: Boolean = true,
    val closeSkinModel: Boolean = false,
    val animationSpeed: Int = 8,
    /** 派生色（ltColor/dkColor）透明度，0 完全透明 ~ 255 完全不透明，独立于主要主题色的 alpha */
    val colorAlpha: Int = 255
)
