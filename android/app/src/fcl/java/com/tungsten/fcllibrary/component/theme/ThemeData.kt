package com.tungsten.fcllibrary.component.theme

import android.graphics.Bitmap

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.ColorUtils
import com.tungsten.fcl.FCLApp

/**
 * 主题数据（不可变 data class，替代原 fakefx 属性版 Theme）。
 *
 * 亮暗切换不改变数据本身，动态取色见 [getColor] 与 [getColor2] 与 [getBackground]；
 * ltColor/dkColor/autoTint 等由主色派生，随 [getColor] 变化自动重算。
 */
data class ThemeData(
    /** 亮色模式主要主题色（原始值，Java 侧经 [getColor] 动态取色或 [_getColor] 取原始值） */
    @get:JvmName("_getColor")
    val color: Int,
    /** 暗色模式主要主题色（原始值） */
    val colorDark: Int,
    /** 亮色模式次要色（原始值，Java 侧经 [getColor2] 动态取色或 [_getColor2] 取原始值） */
    @get:JvmName("_getColor2")
    val color2: Int,
    val color2Dark: Int,
    /** Java getter 命名为 isFullscreen（Kotlin Boolean 属性默认 getFullscreen） */
    @get:JvmName("isFullscreen")
    val fullscreen: Boolean,
    /** Java getter 命名为 isCloseSkinModel */
    @get:JvmName("isCloseSkinModel")
    val closeSkinModel: Boolean,
    val animationSpeed: Int,
    /** 派生色（ltColor/dkColor）透明度，独立于主色 alpha */
    val colorAlpha: Int,
    val backgroundLt: BitmapDrawable,
    val backgroundDk: BitmapDrawable
) {
    /** 主色的亮色变体（HSV 提亮，浅色背景/按压态用，随当前模式主色动态变化） */
    val ltColor: Int
        get() = deriveColor(getColor(), brighten = true)

    /** 主色的暗色变体（HSV 压暗，选中态/进度条等用，随当前模式主色动态变化） */
    val dkColor: Int
        get() = deriveColor(getColor(), brighten = false)

    /** 与主色对比的自动文字色（黑/白） */
    val autoTint: Int
        get() = if (ColorUtils.calculateLuminance(getColor()) >= 0.5) Color.BLACK else Color.WHITE

    /** 半透明自动文字色（提示文字用） */
    val autoHintTint: Int
        get() = if (ColorUtils.calculateLuminance(getColor()) >= 0.5) 0x99000000.toInt() else 0x99FFFFFF.toInt()

    /** 当前模式主色的不透明形态（文字/图标等可读性关键场景用，不受透明度设置影响） */
    val opaqueColor: Int
        get() = getColor() or 0xFF000000.toInt()

    /** 亮变体的不透明形态 */
    val opaqueLtColor: Int
        get() = ltColor or 0xFF000000.toInt()

    /** 暗变体的不透明形态 */
    val opaqueDkColor: Int
        get() = dkColor or 0xFF000000.toInt()

    /** 按当前亮暗模式取主要主题色（亮色用 [color]，暗色用 [colorDark]） */
    fun getColor(): Int {
        val activity = FCLApp.getActivity()
        return if (activity != null && ThemeEngine.isNightMode(activity)) colorDark else color
    }

    /** 按当前亮暗模式取次要色（亮色用 [color2]，暗色用 [color2Dark]） */
    fun getColor2(): Int {
        val activity = FCLApp.getActivity()
        return if (activity != null && ThemeEngine.isNightMode(activity)) color2Dark else color2
    }

    /** 按当前亮暗模式取背景 */
    fun getBackground(context: Context): BitmapDrawable {
        val dark = ThemeEngine.isNightMode(context)
        return if (dark) backgroundDk else backgroundLt
    }

    private fun deriveColor(base: Int, brighten: Boolean): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        if (brighten) {
            hsv[1] -= (1 - hsv[1]) * 0.3f
            hsv[2] += (1 - hsv[2]) * 0.3f
        } else {
            hsv[1] += (1 - hsv[1]) * 0.3f
            hsv[2] -= (1 - hsv[2]) * 0.3f
        }
        return Color.HSVToColor(colorAlpha, hsv)
    }

    companion object {
        /** RCL：使用固定主题；背景为纯色，不读取启动器的背景图。 */
        @JvmStatic
        fun getTheme(context: Context): ThemeData {
            val pref = ThemePreference()
            return ThemeData(
                pref.color, pref.colorDark, pref.color2, pref.color2Dark,
                pref.fullscreen, pref.closeSkinModel, pref.animationSpeed,
                pref.colorAlpha,
                solid(context, 0xFFF4F1EC.toInt()),
                solid(context, 0xFF1C1B1A.toInt())
            )
        }

        private fun solid(context: Context, color: Int): BitmapDrawable {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            return BitmapDrawable(context.resources, bitmap)
        }

        /** RCL：游戏内修改的主题只在本次游戏中生效。 */
        @JvmStatic
        fun saveTheme(context: Context, theme: ThemeData) {
        }
    }
}
