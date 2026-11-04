package com.redstonest.redcraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * 提高 OpenAL 混音线程的优先级。
 *
 * 游戏的所有线程都继承启动线程的优先级，OpenAL 的混音线程（alsoft-mixer）与区块构建、渲染等繁忙的线程同级；
 * 它每次只渲染十几毫秒的声音，CPU 繁忙或频率较低时稍有延迟，声音就会变成快速的「哒哒哒」。
 * 混音线程由 OpenAL 在游戏打开声音设备时创建（切换耳机等输出设备时会重新创建），这里定期查找并提高到音频优先级。
 */
final class AudioPriority {
    private static final String MIXER_THREAD = "alsoft-mixer";

    private AudioPriority() {
    }

    static void watch(GameActivity activity) {
        Thread watcher = new Thread(() -> {
            Set<Integer> boosted = new HashSet<>();
            while (true) {
                File[] tasks = new File("/proc/self/task").listFiles();
                if (tasks != null) {
                    for (File task : tasks) {
                        int tid;
                        try {
                            tid = Integer.parseInt(task.getName());
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        if (boosted.contains(tid) || !MIXER_THREAD.equals(threadName(task))) continue;
                        boosted.add(tid);
                        try {
                            android.os.Process.setThreadPriority(tid, android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                            activity.appendLog("[RCL] 已提高声音混音线程的优先级（" + tid + "）\n");
                        } catch (RuntimeException error) {
                            activity.appendLog("[RCL] 无法提高声音混音线程的优先级：" + error + "\n");
                        }
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "RCL-AudioPriority");
        watcher.setDaemon(true);
        watcher.setPriority(Thread.MIN_PRIORITY);
        watcher.start();
    }

    private static String threadName(File task) {
        try {
            return new String(Files.readAllBytes(new File(task, "comm").toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
