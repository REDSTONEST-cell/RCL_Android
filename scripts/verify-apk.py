#!/usr/bin/env python3
"""检查手机版 APK 的启动层与打包边界。"""

import sys
import zipfile
from pathlib import Path


def verify(path: Path) -> None:
    with zipfile.ZipFile(path) as apk:
        names = set(apk.namelist())
        required = {
            f"lib/arm64-v8a/lib{name}.so"
            for name in ("pojavexec", "pojavexec_awt", "fcl", "androidnsbypass", "bytehook", "c++_shared")
        }
        missing = required - names
        if missing:
            raise ValueError(f"缺少启动层本地库：{', '.join(sorted(missing))}")
        for name in names:
            lower = name.lower()
            if any(part in lower for part in ("terracotta", "lwjglx", "pffft")):
                raise ValueError(f"不应打包的组件：{name}")
            if name.startswith("lib/") and not name.startswith("lib/arm64-v8a/"):
                raise ValueError(f"不应打包的架构：{name}")
        if "assets/notices/FCL-LICENSE.txt" not in names:
            raise ValueError("缺少 FCL 许可文件")
        # ELF e_machine：AArch64 为 183，避免把其他架构的库误命名后混入。
        for name in sorted(required):
            header = apk.read(name)[:20]
            if header[:4] != b"\x7fELF" or header[4:6] != b"\x02\x01" or header[18:20] != b"\xb7\x00":
                raise ValueError(f"本地库不是 arm64 ELF：{name}")
    print(f"打包检查通过：{path.name}")


if __name__ == "__main__":
    paths = list(Path(sys.argv[1] if len(sys.argv) > 1 else "dist").glob("*.apk"))
    if not paths:
        sys.exit("没有找到 APK")
    for path in paths:
        verify(path)
