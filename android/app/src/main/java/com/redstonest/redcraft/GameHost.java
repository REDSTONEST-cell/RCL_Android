package com.redstonest.redcraft;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;

public final class GameHost {
    private static final int PICK_FILES = 4101, PICK_FOLDER = 4102, SAVE_FILE = 4103;
    private final Activity activity;
    private final WebView webView;
    private final UpdateInstaller updater;
    /** 正在等待系统界面返回的请求：前端回调 id，以及要保存的文件 */
    private String pendingId;
    private File pendingSave;

    public GameHost(Activity activity, WebView webView) { this.activity = activity; this.webView = webView; this.updater = new UpdateInstaller(activity); }

    // ─── 文件导入导出 ───
    // 手机上不能用电脑版的「选择路径」方式：系统选择器返回的是 content:// 地址，启动器无法按路径读写，
    // 这里把选中的文件复制到应用缓存目录再交给启动器导入；导出时由系统让用户选择保存位置。

    /** 选择文件（可多选），复制到缓存目录后回调 {paths}。 */
    @JavascriptInterface public void pickFiles(String id, boolean multiple) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        request(id, intent, PICK_FILES, null);
    }

    /** 选择文件夹，整个文件夹复制到缓存目录后回调 {paths:[文件夹]}。 */
    @JavascriptInterface public void pickFolder(String id) {
        request(id, new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER, null);
    }

    /** 把缓存目录中的文件保存到用户选择的位置，回调 {ok:true}。 */
    @JavascriptInterface public void saveFile(String id, String path, String name, String mime) {
        File file = new File(path);
        try {
            String cache = activity.getCacheDir().getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(cache) || !file.isFile()) throw new IOException("文件不存在");
        } catch (IOException error) {
            deliver(id, error("无法导出：" + error.getMessage()));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime).putExtra(Intent.EXTRA_TITLE, name);
        request(id, intent, SAVE_FILE, file);
    }

    private void request(String id, Intent intent, int code, File save) {
        activity.runOnUiThread(() -> {
            if (pendingId != null) deliver(pendingId, cancelled());
            pendingId = id;
            pendingSave = save;
            try {
                activity.startActivityForResult(intent, code);
            } catch (Exception error) {
                pendingId = null;
                deliver(id, error("无法打开系统文件选择器：" + error.getMessage()));
            }
        });
    }

    /** 由 MainActivity 转交系统界面的返回结果；不是这里发起的请求返回 false。 */
    public boolean onActivityResult(int code, int result, Intent data) {
        if (updater.onActivityResult(code)) return true;
        if (code != PICK_FILES && code != PICK_FOLDER && code != SAVE_FILE) return false;
        String id = pendingId;
        File save = pendingSave;
        pendingId = null;
        pendingSave = null;
        if (id == null) return true;
        if (result != Activity.RESULT_OK || data == null) {
            deliver(id, cancelled());
            return true;
        }
        // 复制可能较慢（存档有几百 MB），放到后台线程
        new Thread(() -> {
            try {
                if (code == SAVE_FILE) {
                    Uri uri = data.getData();
                    if (uri == null) throw new IOException("没有选择保存位置");
                    try (InputStream in = new FileInputStream(save); OutputStream out = activity.getContentResolver().openOutputStream(uri, "w")) {
                        if (out == null) throw new IOException("无法写入所选位置");
                        copy(in, out);
                    } finally {
                        save.delete();
                    }
                    deliver(id, new JSONObject().put("ok", true));
                    return;
                }
                File dir = new File(activity.getCacheDir(), "rcl-import");
                deleteTree(dir);
                JSONArray paths = new JSONArray();
                if (code == PICK_FOLDER) {
                    Uri tree = data.getData();
                    if (tree == null) throw new IOException("没有选择文件夹");
                    String root = DocumentsContract.getTreeDocumentId(tree);
                    File target = new File(dir, safeName(queryName(DocumentsContract.buildDocumentUriUsingTree(tree, root)), "folder"));
                    copyTree(tree, root, target);
                    paths.put(target.getAbsolutePath());
                } else {
                    java.util.List<Uri> uris = new java.util.ArrayList<>();
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
                    } else if (data.getData() != null) {
                        uris.add(data.getData());
                    }
                    for (int i = 0; i < uris.size(); i++) {
                        // 每个文件单独一个子目录，同名文件不会互相覆盖，导入后仍保留原文件名
                        File target = new File(new File(dir, String.valueOf(i)), safeName(queryName(uris.get(i)), "file"));
                        copyUri(uris.get(i), target);
                        paths.put(target.getAbsolutePath());
                    }
                }
                deliver(id, new JSONObject().put("paths", paths));
            } catch (Exception error) {
                deliver(id, error((code == SAVE_FILE ? "导出失败：" : "读取所选文件失败：") + error.getMessage()));
            }
        }, "RCL-Files").start();
        return true;
    }

    private void copyTree(Uri tree, String documentId, File target) throws IOException {
        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建 " + target.getName());
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = activity.getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String child = cursor.getString(0);
                File file = new File(target, safeName(cursor.getString(1), "file"));
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) copyTree(tree, child, file);
                else copyUri(DocumentsContract.buildDocumentUriUsingTree(tree, child), file);
            }
        }
    }

    private void copyUri(Uri uri, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建缓存目录");
        try (InputStream in = activity.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("无法读取 " + target.getName());
            copy(in, out);
        }
    }

    private String queryName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {}
        return null;
    }

    /** 文件名只保留最后一段，去掉路径分隔符，避免写到缓存目录之外。 */
    private static String safeName(String name, String fallback) {
        if (name == null) return fallback;
        name = name.replace('/', '_').replace('\\', '_').trim();
        return name.isEmpty() || name.equals(".") || name.equals("..") ? fallback : name;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static JSONObject error(String message) {
        try { return new JSONObject().put("error", message); } catch (Exception e) { return new JSONObject(); }
    }

    private static JSONObject cancelled() {
        try { return new JSONObject().put("cancelled", true); } catch (Exception e) { return new JSONObject(); }
    }

    private void deliver(String id, JSONObject result) {
        String js = "window.__rclFiles&&window.__rclFiles(" + JSONObject.quote(id) + "," + result + ")";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }
    @JavascriptInterface public void captureExit(String session, int pid) {
        if (!session.matches("[0-9a-f-]{36}") || pid <= 0) return;
        String message;
        try {
            if (Build.VERSION.SDK_INT < 30) message = "当前 Android 版本不提供历史进程退出信息";
            else {
                ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                message = "系统暂未提供该游戏进程的退出记录";
                for (android.app.ApplicationExitInfo info : manager.getHistoricalProcessExitReasons(activity.getPackageName(),pid,3)) {
                    if (!(activity.getPackageName() + ":jvm").equals(info.getProcessName())) continue;
                    message = "Android 游戏进程退出记录：reason=" + info.getReason() + ", status=" + info.getStatus() + ", description=" + info.getDescription();
                    break;
                }
            }
        } catch (Exception error) { message = "无法读取系统退出记录：" + error.getClass().getSimpleName(); }
        File log = new File(activity.getFilesDir(), "game-session/" + session + "/game.log");
        try (FileOutputStream out = new FileOutputStream(log,true)) {
            out.write(("\n[RCL] " + message + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
    @JavascriptInterface public void shareLog(String text) {
        if (text.length() > 4 * 1024 * 1024) return;
        activity.runOnUiThread(() -> {
            try {
                File file = new File(activity.getCacheDir(), "RCL-game.log");
                Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(share, "导出游戏日志"));
            } catch (Exception error) {
                android.widget.Toast.makeText(activity,"无法导出日志：" + error.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }
    // ─── 与 FCL 相同：下载时持有唤醒锁与 WiFi 锁 ───
    private android.os.PowerManager.WakeLock downloadWakeLock;
    private android.net.wifi.WifiManager.WifiLock downloadWifiLock;

    /** 有任务进行时保持 CPU 与 WiFi 活跃，关屏或切到后台时下载不中断；任务全部结束后释放。 */
    @JavascriptInterface public synchronized void keepAwake(boolean on) {
        try {
            if (on && downloadWakeLock == null) {
                downloadWakeLock = ((android.os.PowerManager) activity.getSystemService(Context.POWER_SERVICE))
                    .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "RCL:download");
                downloadWakeLock.acquire();
                downloadWifiLock = ((android.net.wifi.WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RCL:download");
                downloadWifiLock.acquire();
            } else if (!on && downloadWakeLock != null) {
                if (downloadWakeLock.isHeld()) downloadWakeLock.release();
                downloadWakeLock = null;
                if (downloadWifiLock != null && downloadWifiLock.isHeld()) downloadWifiLock.release();
                downloadWifiLock = null;
            }
        } catch (RuntimeException ignored) {
        }
    }

    // ─── 麦克风权限（游戏内的语音聊天等，由玩家在设置中授权） ───
    private static final int REQUEST_MICROPHONE = 4104;

    @JavascriptInterface public boolean hasMicrophone() {
        return activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** 请求麦克风权限；被拒绝且系统不再弹出询问时，打开应用设置页让玩家手动开启。 */
    @JavascriptInterface public void requestMicrophone() {
        activity.runOnUiThread(() -> {
            if (hasMicrophone()) return;
            android.content.SharedPreferences prefs = activity.getSharedPreferences("rcl", Context.MODE_PRIVATE);
            boolean asked = prefs.getBoolean("microphone_asked", false);
            if (!asked || activity.shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)) {
                prefs.edit().putBoolean("microphone_asked", true).apply();
                activity.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            } else {
                activity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.getPackageName(), null)));
            }
        });
    }

    @JavascriptInterface public void installUpdate(String id) {
        updater.install(message -> {
            if (message != null) deliver(id, error(message));
            else {
                try { deliver(id, new JSONObject().put("ok", true)); }
                catch (Exception e) { deliver(id, error("无法返回安装状态")); }
            }
        });
    }

    /** 手机版的版本号（显示在设置页）。 */
    @JavascriptInterface public String appVersion() {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception error) {
            return "";
        }
    }
    @JavascriptInterface public void start(String session) {
        if (!session.matches("[0-9a-f-]{36}")) return;
        // 一个进程只能创建一次 JVM：上一次的游戏或安装器进程还没结束时（例如卡住后被切到后台），先结束它再启动
        boolean stale = stop();
        Runnable launch = () -> activity.startActivity(new Intent(activity, GameActivity.class).putExtra("session", session));
        if (stale) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(launch, 800);
        else activity.runOnUiThread(launch);
    }
    /**
     * 在 :jvm 进程中运行一次 Java 程序（安装器等，见 JavaService）。一个进程只能创建一次 JVM，
     * 先结束残留的 :jvm 进程（例如上一次游戏或安装器），稍后再启动服务。
     */
    @JavascriptInterface public void runJava(String session) {
        if (!session.matches("[0-9a-f-]{36}")) return;
        stop();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
            activity.startService(new Intent(activity, JavaService.class).putExtra("session", session)), 800);
    }
    /** 结束 :jvm 进程（游戏或安装器）；返回是否有进程被结束。 */
    @JavascriptInterface public boolean stop() {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) return false;
        boolean killed = false;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if ((activity.getPackageName() + ":jvm").equals(process.processName)) {
                android.os.Process.killProcess(process.pid);
                killed = true;
            }
        }
        return killed;
    }
}
