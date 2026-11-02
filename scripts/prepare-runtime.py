#!/usr/bin/env python3
"""从固定的 FCL 子模块准备 Java 17 / 21、LWJGL 与渲染器运行资源。"""
import shutil
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path

fcl, app = map(Path, sys.argv[1:])
runtime = app / 'src/main/assets/runtime'
runtime.mkdir(parents=True, exist_ok=True)
source = fcl / 'FCL/src/main'
# Java 17：1.17 ~ 1.20.4；Java 21：1.20.5 ~ 1.21.x
for version in (17, 21):
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
native = app / 'src/main/jniLibs/arm64-v8a'
native.mkdir(parents=True, exist_ok=True)
target_jna = runtime / 'natives/libjnidispatch.so'
target_jna.parent.mkdir(exist_ok=True)
shutil.copy2(source / 'assets/app_runtime/jna/5.13.0/natives/arm64-v8a/libjnidispatch.so', target_jna)
shutil.copy2(source / 'jniLibs/arm64-v8a/libgl4es_114.so', native)
# 渲染器、LWJGL 与 OpenAL 的本地库（NG-GL4ES 与 spirv-cross 用于 1.21.5 及更新的版本）
for name in ('NG-GL4ES-release.aar', 'spirv-cross-natives.aar', 'lwjgl-3.3.3-natives-release.aar', 'openal-soft-release.aar'):
    with zipfile.ZipFile(fcl / 'FCL/libs' / name) as aar:
        for entry in aar.namelist():
            if not entry.endswith('.so') or '/arm64-v8a/' not in entry:
                continue
            if any(word in entry.lower() for word in ('pffft', 'lwjglx', 'terracotta')):
                raise ValueError(f'不支持的运行组件：{entry}')
            target = runtime / 'natives' / Path(entry).name if entry.startswith('assets/') else native / Path(entry).name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(aar.read(entry))
for version in (17, 21):
    if not list((runtime / f'java{version}').rglob('libjvm.so')):
        raise ValueError(f'Java {version} 运行时缺少 libjvm.so')
