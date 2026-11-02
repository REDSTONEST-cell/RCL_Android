#!/usr/bin/env python3
"""从固定的 FCL 子模块准备 Java、LWJGL、JNA 与渲染器运行资源。

运行时目录（APK 的 assets/runtime）：
  java17 / java21 / java25   Java 运行时：1.17 ~ 1.20.4 / 1.20.5 ~ 1.21.x / 26.1 ~ 26.2
  lwjgl, natives             LWJGL 3.3.3（26 之前的版本）
  lwjgl-3.4.1, natives-3.4.1 LWJGL 3.4.1（26.x），由 FCL 的源码编译合并，与 FCL 的 Gradle 任务相同
  jna/<版本>                 各版本 JNA 的本地库，按游戏使用的 JNA 版本选择
"""
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path

fcl, app = map(Path, sys.argv[1:])
runtime = app / 'src/main/assets/runtime'
runtime.mkdir(parents=True, exist_ok=True)
source = fcl / 'FCL/src/main'

for version in (17, 21, 25):
    with tempfile.TemporaryDirectory() as tmp:
        java = Path(tmp)
        for name in ('universal.tar.xz', 'bin-arm64.tar.xz'):
            with tarfile.open(source / f'jreAssets/app_runtime/java/jre{version}' / name) as archive:
                archive.extractall(java, filter='data')
        shutil.rmtree(runtime / f'java{version}', ignore_errors=True)
        shutil.copytree(java, runtime / f'java{version}', symlinks=False)

for group, dest in (('lwjgl/3.3.3', 'lwjgl'), ('caciocavallo17', 'cacio')):
    out = runtime / dest
    out.mkdir(exist_ok=True)
    for jar in (source / 'assets/app_runtime' / group).glob('*.jar'):
        if 'lwjglx' not in jar.name:
            shutil.copy2(jar, out / jar.name)
for name in ('MioLaunchWrapper.jar', 'MioLibPatcher.jar'):
    shutil.copy2(source / 'assets/game' / name, runtime / name)


def build_lwjgl_341():
    """与 FCL 的 LWJGL/3.4.1 Gradle 任务相同：编译 FCL 的 GLFW 实现，与 glfw / opengl / sdl 模块合并，
    其余模块原样复制。FCL 仓库中没有预先编译好的 3.4.1 合并包。"""
    project = fcl / 'LWJGL/3.4.1'
    libs = project / 'libs/3.4.1'
    out = runtime / 'lwjgl-3.4.1'
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    merged_modules = ('lwjgl-glfw.jar', 'lwjgl-opengl.jar', 'lwjgl-sdl.jar')
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        # jspecify 注解只在编译时需要，这里生成最小定义，不打进合并包
        stubs = tmp / 'stubs/org/jspecify/annotations'
        stubs.mkdir(parents=True)
        for name in ('Nullable', 'NonNull'):
            (stubs / f'{name}.java').write_text(
                'package org.jspecify.annotations;\n'
                '@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE})\n'
                f'public @interface {name} {{}}\n')
        for name in ('NullMarked', 'NullUnmarked'):
            (stubs / f'{name}.java').write_text(f'package org.jspecify.annotations;\npublic @interface {name} {{}}\n')
        classes = tmp / 'classes'
        sources = [str(p) for p in (project / 'src/main/java').rglob('*.java')] + [str(p) for p in (tmp / 'stubs').rglob('*.java')]
        classpath = ':'.join(str(p) for p in sorted(libs.glob('*.jar')) + sorted((fcl / 'LWJGL/compileOnly').glob('*.jar')))
        subprocess.run(['javac', '--release', '17', '-nowarn', '-encoding', 'UTF-8', '-d', str(classes), '-cp', classpath, *sources], check=True)
        seen = set()
        with zipfile.ZipFile(out / 'lwjgl-3.4.1-merged-modules.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
            # FCL 的实现在前，与模块中的同名类冲突时保留 FCL 的（与 Gradle 的 EXCLUDE 策略相同）
            for path in sorted(classes.rglob('*')):
                name = path.relative_to(classes).as_posix()
                if path.is_file() and not name.startswith('org/jspecify/'):
                    jar.write(path, name)
                    seen.add(name)
            for module in merged_modules:
                with zipfile.ZipFile(libs / module) as src:
                    for entry in src.infolist():
                        if entry.is_dir() or entry.filename in seen or entry.filename.startswith('net/java/openjdk/cacio/ctc/'):
                            continue
                        jar.writestr(entry.filename, src.read(entry))
                        seen.add(entry.filename)
    for module in libs.glob('*.jar'):
        if module.name not in merged_modules and 'lwjglx' not in module.name:
            shutil.copy2(module, out / module.name)


build_lwjgl_341()

# 各版本 JNA 的本地库（1.20.1 使用 5.12.1，1.21.5 起使用 5.17.0 等）
shutil.rmtree(runtime / 'jna', ignore_errors=True)
for jna in (source / 'assets/app_runtime/jna').iterdir():
    lib = jna / 'natives/arm64-v8a/libjnidispatch.so'
    if lib.is_file():
        (runtime / 'jna' / jna.name).mkdir(parents=True, exist_ok=True)
        shutil.copy2(lib, runtime / 'jna' / jna.name / lib.name)

native = app / 'src/main/jniLibs/arm64-v8a'
native.mkdir(parents=True, exist_ok=True)
target_jna = runtime / 'natives/libjnidispatch.so'
target_jna.parent.mkdir(exist_ok=True)
shutil.copy2(source / 'assets/app_runtime/jna/5.13.0/natives/arm64-v8a/libjnidispatch.so', target_jna)
shutil.copy2(source / 'jniLibs/arm64-v8a/libgl4es_114.so', native)
# 渲染器、LWJGL、OpenAL 与 SDL 的本地库：
# NG-GL4ES 与 spirv-cross 用于 1.21.5 ~ 26.2；26.3 起游戏改用 SDL3 管理窗口，渲染器使用 MobileGlues
natives_dir = {'lwjgl-3.4.1-natives-release.aar': 'natives-3.4.1'}
for name in ('NG-GL4ES-release.aar', 'spirv-cross-natives.aar', 'MobileGlues-release.aar', 'SDL-release.aar',
             'lwjgl-3.3.3-natives-release.aar', 'lwjgl-3.4.1-natives-release.aar', 'openal-soft-release.aar'):
    with zipfile.ZipFile(fcl / 'FCL/libs' / name) as aar:
        for entry in aar.namelist():
            if not entry.endswith('.so') or '/arm64-v8a/' not in entry:
                continue
            if any(word in entry.lower() for word in ('pffft', 'lwjglx', 'terracotta')):
                raise ValueError(f'不支持的运行组件：{entry}')
            if entry.startswith('assets/'):
                target = runtime / natives_dir.get(name, 'natives') / Path(entry).name
            else:
                target = native / Path(entry).name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(aar.read(entry))

for version in (17, 21, 25):
    if not list((runtime / f'java{version}').rglob('libjvm.so')):
        raise ValueError(f'Java {version} 运行时缺少 libjvm.so')
for required in ('lwjgl-3.4.1/lwjgl-3.4.1-merged-modules.jar', 'lwjgl-3.4.1/lwjgl.jar', 'natives-3.4.1/liblwjgl.so', 'jna/5.17.0/libjnidispatch.so'):
    if not (runtime / required).is_file():
        raise ValueError(f'运行时缺少 {required}')

# MobileGlues 使用 LGPL-2.1，随 APK 附带其许可
with zipfile.ZipFile(fcl / 'FCL/libs/MobileGlues-release.aar') as aar:
    notices = app / 'src/main/assets/notices'
    notices.mkdir(parents=True, exist_ok=True)
    (notices / 'MobileGlues-LGPL-2.1.txt').write_bytes(aar.read('assets/licenses/mobileglues-lgplv2.1-license.txt'))
