package com.redstonest.redcraft;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** 只安装更新模块下载的固定文件。系统安装确认由 Android 完成。 */
final class UpdateInstaller {
    static final int REQUEST_INSTALL_PERMISSION = 4105;
    private final Activity activity;
    private Consumer<String> pending;

    UpdateInstaller(Activity activity) { this.activity = activity; }

    void install(Consumer<String> callback) {
        activity.runOnUiThread(() -> {
            if (pending != null) { callback.accept("正在准备安装，请稍候"); return; }
            pending = callback;
            validateAndOpen();
        });
    }

    private File apk() { return new File(activity.getFilesDir(), "config/updates/update.apk"); }

    private void validateAndOpen() {
        new Thread(() -> {
            try {
                File file = apk();
                String expected = new String(Files.readAllBytes(new File(file.getParentFile(), "update.sha256").toPath()), StandardCharsets.UTF_8).trim();
                if (!expected.matches("[0-9a-f]{64}")) throw new Exception("更新校验信息无效，请重新下载");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (FileInputStream in = new FileInputStream(file)) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
                }
                StringBuilder actual = new StringBuilder();
                for (byte b : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                if (!expected.equals(actual.toString())) throw new Exception("安装包校验失败，请重新下载");
                PackageManager pm = activity.getPackageManager();
                int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
                PackageInfo candidate = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
                PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
                if (candidate == null || !activity.getPackageName().equals(candidate.packageName))
                    throw new Exception("安装包不属于当前启动器");
                long oldCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
                long newCode = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
                if (newCode <= oldCode) throw new Exception("安装包不是更高版本，请重新检查更新");
                if (!signatures(installed).equals(signatures(candidate)) || signatures(candidate).isEmpty())
                    throw new Exception("安装包签名与当前应用不一致，无法覆盖更新");
                activity.runOnUiThread(() -> {
                    try {
                        if (!pm.canRequestPackageInstalls()) {
                            activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName())), REQUEST_INSTALL_PERMISSION);
                            return;
                        }
                        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
                        Intent intent = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(android.content.ClipData.newRawUri("更新安装包", uri));
                        activity.startActivity(intent);
                        finish(null);
                    } catch (Exception e) { finish("无法打开安装界面：" + e.getMessage()); }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> finish(e.getMessage()));
            }
        }, "RCL-UpdateInstaller").start();
    }

    private static Set<String> signatures(PackageInfo info) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
            ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        Set<String> result = new HashSet<>();
        if (signatures != null) for (Signature signature : signatures) result.add(signature.toCharsString());
        return result;
    }

    boolean onActivityResult(int code) {
        if (code != REQUEST_INSTALL_PERMISSION) return false;
        if (pending != null) {
            if (activity.getPackageManager().canRequestPackageInstalls()) validateAndOpen();
            else finish("未允许安装更新，可授权后再次点击安装");
        }
        return true;
    }

    private void finish(String error) {
        Consumer<String> callback = pending;
        pending = null;
        if (callback != null) callback.accept(error);
    }
}
