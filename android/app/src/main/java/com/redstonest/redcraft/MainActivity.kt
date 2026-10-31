package com.redstonest.redcraft

import android.os.Bundle
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : TauriActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    hideSystemBars()
  }

  // 让页面中的 viewport 设置生效：手机版按屏幕尺寸自动缩放（见前端 src/mobile/viewport.ts）
  override fun onWebViewCreate(webView: WebView) {
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = true
    webView.settings.setSupportZoom(false)
    webView.settings.builtInZoomControls = false
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) hideSystemBars()
  }

  // 全屏：隐藏状态栏与导航栏，从屏幕边缘滑动时临时显示
  private fun hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }
}
