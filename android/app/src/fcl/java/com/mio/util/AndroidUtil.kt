package com.mio.util

import android.app.ActivityManager
import android.content.Context

// RCL：只保留游戏内按键与菜单用到的函数，原文件其余部分依赖启动器界面。

fun getMemoryInfo(context: Context): ActivityManager.MemoryInfo {
    val info = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
    return info
}

fun getTotalMemory(context: Context): Long = getMemoryInfo(context).totalMem

fun getUsedMemory(context: Context): Long {
    val info = getMemoryInfo(context)
    return info.totalMem - info.availMem
}

fun getScreenHeight(): Int {
    if (DisplayUtil.screenHeight != -1) return DisplayUtil.screenHeight
    return DisplayUtil.currentDisplayMetrics.heightPixels
}

fun getScreenWidth(): Int {
    if (DisplayUtil.screenWidth != -1) return DisplayUtil.screenWidth
    return DisplayUtil.currentDisplayMetrics.widthPixels
}
