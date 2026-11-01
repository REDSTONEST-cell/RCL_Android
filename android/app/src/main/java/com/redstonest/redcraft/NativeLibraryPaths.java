package com.redstonest.redcraft;

/** JLI 的进程环境与 Android 动态链接器使用不同的库搜索路径。 */
final class NativeLibraryPaths {
    private NativeLibraryPaths() {}

    static String environment(String natives, String nativeDir) {
        // JRE 的 lib/server 不能放在这里，否则 JLI 会尝试 execve 重新启动 Java。
        return natives + ":" + nativeDir + ":/system/lib64:/vendor/lib64:/vendor/lib64/hw:/system_ext/lib64";
    }

    static String linker(String java, String natives, String nativeDir) {
        return java + "/lib:" + java + "/lib/jli:" + java + "/lib/server:" + environment(natives,nativeDir);
    }
}
