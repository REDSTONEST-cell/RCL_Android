#!/usr/bin/env python3
"""从固定的 FCL 子模块准备 Java 17 与 LWJGL 运行资源。"""
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
with tempfile.TemporaryDirectory() as tmp:
    java = Path(tmp)
    for name in ('universal.tar.xz', 'bin-arm64.tar.xz'):
        with tarfile.open(source / 'jreAssets/app_runtime/java/jre17' / name) as archive:
            archive.extractall(java, filter='data')
    shutil.copytree(java, runtime / 'java17', dirs_exist_ok=True, symlinks=False)
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
shutil.copy2(source / 'jniLibs/arm64-v8a/libgl4es_114.so', native)
for name in ('lwjgl-3.3.3-natives-release.aar', 'openal-soft-release.aar'):
    with zipfile.ZipFile(fcl / 'FCL/libs' / name) as aar:
        for entry in aar.namelist():
            if not entry.endswith('.so') or '/arm64-v8a/' not in entry:
                continue
            if any(word in entry.lower() for word in ('pffft', 'lwjglx', 'terracotta')):
                raise ValueError(f'不支持的运行组件：{entry}')
            target = runtime / 'natives' / Path(entry).name if entry.startswith('assets/') else native / Path(entry).name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(aar.read(entry))
if not list((runtime / 'java17').rglob('libjvm.so')):
    raise ValueError('Java 17 运行时缺少 libjvm.so')
