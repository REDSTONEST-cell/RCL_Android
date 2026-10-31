# RCL 手机版（Android）

[RCL（Red Craft Launcher）](https://github.com/REDSTONEST-cell/Red_Craft) 的 Android 版本。

> 开发中：目前可以安装并使用启动器界面（下载版本、Mod 与资源、账号等），**游戏启动正在接入中**。

## 组成

| 目录 | 内容 | 许可 |
| --- | --- | --- |
| `red_craft/` | 子模块：RCL 的界面（Svelte）与核心（Rust），与电脑版共用 | MIT |
| `android/` | Android 工程（Tauri），以及之后接入的游戏运行环境 | GPL-3.0 |
| `scripts/` | 构建脚本 | GPL-3.0 |

游戏运行环境（在手机上运行 Java 版 Minecraft、渲染器、触屏按键）将基于
[Fold Craft Launcher（FCL）](https://github.com/FCL-Team/FoldCraftLauncher) 接入，
因此本仓库整体以 **GPL-3.0** 发布，见 [LICENSE](LICENSE)。电脑版 RCL 仍为 MIT。

## 下载

在 [Actions](../../actions) 中打开最新一次成功的「构建」，下载 `RCL-Android` 中的 APK（arm64）。

目前的测试版使用仓库中公开的测试密钥签名（`android/test-key.jks`），可以直接覆盖安装升级；
正式版将改用私有密钥签名，届时需要卸载测试版后重新安装。

## 构建

需要 Android SDK（compileSdk 36）、NDK 27.0.12077973、Java 17+、Rust（`aarch64-linux-android` 目标）、Node.js 22 与 pnpm。

```bash
git clone --recursive https://github.com/REDSTONEST-cell/RCL_Android.git
cd RCL_Android
export ANDROID_HOME=~/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973
scripts/build.sh            # 正式构建
scripts/build.sh --debug    # 调试构建
```

APK 输出到 `dist/`。

## 致谢

- [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher)（GPL-3.0）及其使用的
  [HMCL](https://github.com/HMCL-dev/HMCL)、[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) 等项目
- [Tauri](https://tauri.app/)
