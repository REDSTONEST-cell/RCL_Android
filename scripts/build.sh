#!/usr/bin/env bash
# 打包 RCL 手机版 APK。
#
# 用法：scripts/build.sh [--debug] [其他 tauri android build 参数]
# 需要：Android SDK（ANDROID_HOME）、NDK 27（NDK_HOME）、Rust aarch64-linux-android 目标、Node 与 pnpm。
#
# 界面与核心来自子模块 red_craft（Red_Craft，MIT），Android 工程在 android/ 目录（GPL-3.0）。
# 构建时把 android/ 复制到 red_craft/src-tauri/gen/android，再由 Tauri 打包。
set -euo pipefail

# 与电脑版使用同一个公开的 Azure 应用；仍允许构建环境显式覆盖。
export REDCRAFT_MS_CLIENT_ID="${REDCRAFT_MS_CLIENT_ID:-3b8bb0ce-d514-42fe-83b6-bb374eb8b665}"

root=$(cd "$(dirname "$0")/.." && pwd)
gen="$root/red_craft/src-tauri/gen/android"

: "${ANDROID_HOME:?请设置 ANDROID_HOME}"
: "${NDK_HOME:?请设置 NDK_HOME}"

# 只引入固定版本的本地启动层，不把上游应用、联网模块或运行时 JAR 打进 APK。
fcl="$root/vendor/fcl"
if [ ! -f "$fcl/FCL/src/main/jni/CMakeLists.txt" ]; then
    echo "缺少 FCL 启动层源码，请运行 git submodule update --init --recursive" >&2
    exit 1
fi

# 保留上一次构建的 Gradle 缓存与产物，只覆盖源码
mkdir -p "$gen"
(cd "$root/android" && tar -cf - .) | (cd "$gen" && tar -xf -)
mkdir -p "$gen/app/src/main/jni"
(cd "$fcl/FCL/src/main/jni" && tar -cf - .) | (cd "$gen/app/src/main/jni" && tar -xf -)
mkdir -p "$gen/app/src/main/assets/notices"
cp "$fcl/LICENSE" "$gen/app/src/main/assets/notices/FCL-LICENSE.txt"

cd "$root/red_craft"
pnpm install --frozen-lockfile
pnpm tauri android build --target aarch64 --apk "$@"

out="$root/dist"
mkdir -p "$out"
find "$gen/app/build/outputs/apk" -name "*.apk" -newer "$root/android/app/build.gradle.kts" -exec cp {} "$out/" \;
ls -la "$out"
