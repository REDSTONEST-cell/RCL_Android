package com.redstonest.redcraft;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class NativeLibraryPathsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    // 对照 OpenJDK 17 java_md.c 的 RequiresSetenv/ContainsLibJVM 分支。
    private boolean requiresReexec(String jvmDir, String environment) {
        if (environment.startsWith(jvmDir)) return false;
        for (String path : environment.split(":")) {
            if ((path.contains("lib/server") || path.contains("lib/client"))
                    && new File(path,"libjvm.so").isFile()) return true;
        }
        return false;
    }

    @Test public void oldCombinedPathTriggersReexecButEnvironmentPathDoesNot() throws Exception {
        File java = temp.newFolder("java17");
        File server = new File(java,"lib/server");
        assertTrue(server.mkdirs());
        assertTrue(new File(server,"libjvm.so").createNewFile());
        String nativePath = "/data/app/example/lib/arm64";
        String natives = "/data/user/0/example/files/runtime/natives";
        String linker = NativeLibraryPaths.linker(java.getAbsolutePath(),natives,nativePath);
        String environment = NativeLibraryPaths.environment(natives,nativePath);
        assertTrue("回归样例必须复现旧路径触发的 JLI 重执行",requiresReexec(server.getAbsolutePath(),linker));
        assertFalse("JLI 的环境不能触发重执行",requiresReexec(server.getAbsolutePath(),environment));
        assertFalse(environment.contains(java.getAbsolutePath()));
        assertTrue("链接器仍然需要找到 libjvm.so",linker.contains(server.getAbsolutePath()));
        assertTrue(linker.contains(natives));
        assertTrue(environment.contains(nativePath));
    }
}
