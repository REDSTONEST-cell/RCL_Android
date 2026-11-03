package com.redstonest.redcraft;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * :jvm 进程每秒更新一次会话目录中的 heartbeat 文件。启动器据此判断进程是否还在运行：
 * Android 上无法可靠地通过 /proc 判断其他进程是否存在。
 */
final class Heartbeat {
    private Heartbeat() {
    }

    static void start(File sessionDir) {
        File file = new File(sessionDir, "heartbeat");
        Thread thread = new Thread(() -> {
            while (true) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write('1');
                } catch (IOException ignored) {
                }
                file.setLastModified(System.currentTimeMillis());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "RCL-Heartbeat");
        thread.setDaemon(true);
        thread.start();
    }
}
