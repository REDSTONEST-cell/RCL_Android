# Android 游戏启动层

## 当前接入范围

FCL 通过 `vendor/fcl` 子模块固定版本，构建 `FCL/src/main/jni` 本地库，
准备 Java 17、LWJGL 3.3.3、cacio、JNA、GL4ES 和 OpenAL 资源。
源码原有版权声明保持不变，FCL 的 LICENSE 随 APK 保存在 `assets/notices`。
不引入 FCL 主应用 Gradle 工程，不打包 Terracotta、LWJGLX 和 pffft。

构建依赖 NDK 27.0.12077973、CMake 3.22.1、ByteHook 1.0.10，目标仅 arm64。
`scripts/build.sh` 在生成的 Android 工程中复制 JNI 源码，再交给 Gradle 编译。
CI 对 APK 中的核心库、ELF 架构、许可文件和禁止打包的组件进行检查。

## 启动与进程

Rust 负责账号刷新、文件补全和生成启动参数，跳过桌面 Java 下载及 natives 解压。
参数保存在应用私有目录中的随机会话目录，网页桥只接收会话编号。
`GameActivity` 在 `:jvm` 进程中读取并删除请求文件，首次运行时解压内置运行时。
JNI 类名保持上游 ABI，使用最小宿主接口，避免依赖 FCL 的主应用、菜单和联网模块。
游戏日志、PID 与退出码通过私有文件回传 Rust；前端日志中的令牌会被遮蔽。
结束操作仅结束当前应用的 `:jvm` 进程。

首轮限制为原版与 Fabric 1.20.1。其他加载器和 Java 版本直接返回尚未支持的提示。
基础输入包括 WASD、跳跃、背包、暂停、攻击、使用和拖动视角。
构建成功仅验证编译与资源打包，进入主菜单、创建世界和操作必须在 Android 真机验证。
完整的布局管理和渲染器切换仍属于后续工作。
