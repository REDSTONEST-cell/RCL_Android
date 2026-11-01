# Android 游戏启动层

## 当前接入范围

FCL 通过 `vendor/fcl` 子模块固定版本，目前只构建 `FCL/src/main/jni` 本地库。
源码原有版权声明保持不变，FCL 的 LICENSE 随 APK 保存在 `assets/notices`。
不引入 FCL 主应用 Gradle 工程，不打包 Terracotta、LWJGLX 和 pffft。

构建依赖 NDK 27.0.12077973、CMake 3.22.1、ByteHook 1.0.10，目标仅 arm64。
`scripts/build.sh` 在生成的 Android 工程中复制 JNI 源码，再交给 Gradle 编译。
CI 对 APK 中的核心库、ELF 架构、许可文件和禁止打包的组件进行检查。

## 后续接入顺序

1. 引入 FCLauncher 与 FCLBridge 所需的 Java/Kotlin 类，拆开插件管理器与主应用单例依赖。
2. 准备 Java 17、LWJGL、cacio、JNA 和渲染器资源；单独确认每个运行时组件的许可。
3. 创建独立 `:jvm` 进程的游戏 Activity，接入 Surface、触控和默认按键。
4. 增加 Tauri Android 插件，传递版本、游戏目录和账号，生成手机端启动参数。
5. Rust 端完成账号刷新和文件补全后调用插件，回传日志及退出状态。
6. 真机验证原版和 Fabric 1.20.1，再扩展其他加载器。

目前仍保留无法启动游戏的提示。本地库构建成功不代表已经能进入游戏，
在 Java 桥、运行时资源和游戏 Activity 完成前不移除该提示。
