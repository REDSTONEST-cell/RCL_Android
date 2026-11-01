package com.redstonest.redcraft

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.max

class MainActivity : TauriActivity() {
  private var webView: WebView? = null
  private val displayInfo = RclDisplay()
  private var rotation = -1

  override fun onCreate(savedInstanceState: Bundle?) {
    // 先设置窗口再创建界面，启动后第一帧就铺满屏幕
    applyFullscreen()
    super.onCreate(savedInstanceState)
    getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, null)
  }

  override fun onDestroy() {
    getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
    super.onDestroy()
  }

  // 让页面中的 viewport 设置生效：手机版按可用尺寸自动缩放（见前端 src/mobile/viewport.ts）
  override fun onWebViewCreate(webView: WebView) {
    this.webView = webView
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = true
    webView.settings.setSupportZoom(false)
    webView.settings.builtInZoomControls = false
    // 资源图标由 WebView 直接加载（见前端 src/mobile/icons.ts）：图标地址的内容不会变化，
    // 磁盘缓存中有就直接使用，不再向服务器确认，离线或网络慢时也能立即显示。
    // 网页本身由 Tauri 拦截提供，不受影响；开发调试时页面来自开发服务器，不启用
    if (!BuildConfig.DEBUG) webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
    // 页面通过 RclDisplay.info() 读取可用尺寸与挖孔区域，变化时再主动通知页面
    webView.addJavascriptInterface(displayInfo, "RclDisplay")
    webView.addJavascriptInterface(GameHost(this), "RclGame")
    ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
      onInsets(v, insets)
      WindowInsetsCompat.CONSUMED
    }
    window.decorView.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
      if (r - l != or - ol || b - t != ob - ot) publish()
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) applyFullscreen()
  }

  override fun onResume() {
    super.onResume()
    applyFullscreen()
  }

  /**
   * 全屏并进入挖孔区域（参照 FCL 的 ThemeEngine.applyFullscreen）：
   * - 横屏时系统默认不让窗口进入挖孔所在的短边，会在挖孔一侧留下黑边，这里改为允许进入；
   * - 窗口不再按系统栏留边，由页面根据 RclDisplay 提供的挖孔区域自行避让；
   * - 隐藏状态栏与导航栏，从屏幕边缘滑动时临时显示。
   */
  private fun applyFullscreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // 所有边都允许（个别平板、折叠屏横屏时挖孔在长边）
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
      } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
      if (window.attributes.layoutInDisplayCutoutMode != mode) {
        window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = mode }
      }
    }
    WindowCompat.getInsetsController(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  // 旋转 180° 时挖孔换到另一侧，但窗口尺寸不变、Activity 不会收到配置变化，这里重新分发一次窗口内边距
  private val displayListener = object : DisplayManager.DisplayListener {
    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}
    override fun onDisplayChanged(displayId: Int) {
      // 刷新率等变化也会触发，只在方向变化时处理
      @Suppress("DEPRECATION")
      val now = windowManager.defaultDisplay.rotation
      if (now == rotation) return
      rotation = now
      webView?.let { v -> v.post { ViewCompat.requestApplyInsets(v) } }
    }
  }

  private fun onInsets(v: View, insets: WindowInsetsCompat) {
    val density = resources.displayMetrics.density
    // 挖孔与系统栏（隐藏时为 0；分屏、无法隐藏导航栏的设备上不为 0）
    val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    var l = safe.left
    var t = safe.top
    var r = safe.right
    var b = safe.bottom
    // 曲面屏的弯曲边缘
    insets.displayCutout?.waterfallInsets?.let {
      l = max(l, it.left)
      t = max(t, it.top)
      r = max(r, it.right)
      b = max(b, it.bottom)
    }
    // 屏幕圆角半径，页面据此给贴近四角的内容留出边距
    var corner = 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      insets.toWindowInsets()?.let { wi ->
        for (pos in intArrayOf(
          RoundedCorner.POSITION_TOP_LEFT,
          RoundedCorner.POSITION_TOP_RIGHT,
          RoundedCorner.POSITION_BOTTOM_LEFT,
          RoundedCorner.POSITION_BOTTOM_RIGHT,
        )) {
          wi.getRoundedCorner(pos)?.let { corner = max(corner, it.radius) }
        }
      }
    }
    displayInfo.insets = floatArrayOf(l / density, t / density, r / density, b / density, corner / density)

    // 输入法：窗口不再随输入法缩小，改为把网页的底边抬到输入法上方
    val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
      if (lp.bottomMargin != ime) {
        lp.bottomMargin = ime
        v.layoutParams = lp
      }
    }
    publish()
  }

  /** 把最新的尺寸与挖孔区域交给页面。 */
  private fun publish() {
    val v = webView ?: return
    val root = window.decorView
    if (root.width == 0 || root.height == 0) return
    val density = resources.displayMetrics.density
    // 以整个窗口为准（输入法弹出时网页变矮，但缩放比例不应变化）
    displayInfo.width = root.width / density
    displayInfo.height = root.height / density
    v.evaluateJavascript("window.__rclDisplay&&window.__rclDisplay(${displayInfo.info()})", null)
  }
}

/** 提供给页面的显示信息，单位为 dp（与页面未缩放时的 CSS 像素相同）。 */
class RclDisplay {
  @Volatile var width = 0f
  @Volatile var height = 0f
  /** 左、上、右、下需要避让的宽度，以及屏幕圆角半径。 */
  @Volatile var insets = FloatArray(5)

  @JavascriptInterface
  fun info(): String {
    val i = insets
    return "{\"w\":$width,\"h\":$height,\"l\":${i[0]},\"t\":${i[1]},\"r\":${i[2]},\"b\":${i[3]},\"corner\":${i[4]}}"
  }
}
