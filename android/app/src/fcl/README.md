# 游戏内按键系统（来自 FCL）

本目录的源码与资源取自 [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher)
（子模块 `vendor/fcl` 固定的版本，GPL-3.0），只包含游戏画面、按键、按键编辑器与游戏内菜单需要的部分。

RCL 的修改：

- `JVMActivity`：去掉联机（Terracotta）与语音朗读；新增 `onGameExit`，由 RCL 的 `GameActivity` 把退出码写回启动器。
- `GameService`（游戏运行时的保活前台服务）与 FCL 相同，运行在游戏所在的 `:jvm` 进程；通知点击后回到 RCL 的 `GameActivity`。
- `GameMenu`：去掉 TouchController 与联机菜单；记住选择的按键布局；游戏日志只追加写入。
- 悬浮菜单按钮的图标换成 RCL 的图标（`R.drawable.rcl_menu_button`）。
- `FCLApp`、`FCLPath`、`AndroidUtil.kt`、`AppSettings.kt`、主题相关类改为精简实现：路径都在应用私有目录，主题固定为 RCL 配色，不使用 DataStore。
- SDL3 支持（`org.libsdl.app`、`SdlBridge`）原样保留，用于 26.3 起的版本；`GamepadModePromptDialog` 为空实现（不提示选择手柄输入方式，手柄使用按键映射）。
- `FCLBridge.openLink` 只打开网页与邮件链接。
- 资源（`res/`）是从源码引用的资源计算出的子集；`values/fcl_ids.xml` 补充了代码用到但未复制布局中的 id。

完整许可见 APK 内的 `assets/notices/FCL-LICENSE.txt`。
