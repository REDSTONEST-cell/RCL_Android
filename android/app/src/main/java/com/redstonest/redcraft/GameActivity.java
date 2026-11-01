package com.redstonest.redcraft;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.lwjgl.glfw.CallbackBridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class GameActivity extends Activity implements SurfaceHolder.Callback {
    public static volatile GameActivity current;
    public Surface surface;
    public int width = 1280, height = 720;
    public volatile boolean inputReady;
    public volatile boolean nativeReady;
    private boolean launched;
    private File sessionDir;
    private JSONObject request;
    private float lastX, lastY, cursorX, cursorY;
    private boolean moved;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        current = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        String session = getIntent().getStringExtra("session");
        if (session == null || !session.matches("[0-9a-f-]{36}")) { finish(); return; }
        sessionDir = new File(getFilesDir(), "game-session/" + session);
        try {
            File file = new File(sessionDir, "request.json");
            request = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (!file.delete()) throw new IOException("无法清除临时启动凭据");
            writeStatus("starting", null);
        } catch (Exception error) { showError(error); return; }
        FrameLayout frame = new FrameLayout(this);
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(this);
        view.setOnTouchListener((v, event) -> {
            if (!inputReady) return true;
            float x = event.getX(), y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { lastX = x; lastY = y; moved = false; }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = x - lastX, dy = y - lastY;
                moved |= Math.abs(dx) + Math.abs(dy) > 3;
                cursorX += dx; cursorY += dy;
                CallbackBridge.nativeSendCursorPos(CallbackBridge.grabbing ? cursorX : x, CallbackBridge.grabbing ? cursorY : y);
                lastX = x; lastY = y;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved) {
                if (!CallbackBridge.grabbing) CallbackBridge.nativeSendCursorPos(x, y);
                CallbackBridge.nativeSendMouseButton(0, 1, 0);
                CallbackBridge.nativeSendMouseButton(0, 0, 0);
            }
            return true;
        });
        frame.addView(view);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.addView(keyRow(new String[]{"W", "A", "S", "D"}, new int[]{87,65,83,68}));
        controls.addView(keyRow(new String[]{"跳跃", "背包", "暂停"}, new int[]{32,69,256}));
        LinearLayout actions = new LinearLayout(this);
        for (int i = 0; i < 2; i++) {
            final int button = i;
            Button b = new Button(this); b.setText(i == 0 ? "攻击" : "使用");
            b.setOnTouchListener((v,e) -> {
                if (inputReady && (e.getActionMasked() == 0 || e.getActionMasked() == 1 || e.getActionMasked() == 3))
                    CallbackBridge.nativeSendMouseButton(button, e.getActionMasked() == 0 ? 1 : 0, 0);
                return true;
            });
            actions.addView(b);
        }
        controls.addView(actions);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-2,-2, Gravity.BOTTOM | Gravity.LEFT);
        frame.addView(controls, position);
        setContentView(frame);
    }
    private LinearLayout keyRow(String[] names, int[] keys) {
        LinearLayout row = new LinearLayout(this);
        for (int i=0;i<names.length;i++) {
            final int key = keys[i]; Button b = new Button(this); b.setText(names[i]); b.setAlpha(.65f);
            b.setOnTouchListener((v,e) -> {
                int action = e.getActionMasked();
                if (inputReady && (action == 0 || action == 1 || action == 3))
                    CallbackBridge.nativeSendKey(key,0,action == 0 ? 1 : 0,0);
                return true;
            }); row.addView(b);
        }
        return row;
    }
    private synchronized void writeStatus(String state, Integer code) throws Exception {
        JSONObject value = new JSONObject().put("state", state).put("pid", android.os.Process.myPid());
        if (code != null) value.put("code", code);
        File tmp = new File(sessionDir,"status.tmp");
        Files.write(tmp.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
        if (!tmp.renameTo(new File(sessionDir,"status.json"))) throw new IOException("无法保存游戏状态");
    }
    public synchronized void appendLog(String message) {
        if (sessionDir == null) return;
        try (FileOutputStream stream = new FileOutputStream(new File(sessionDir,"game.log"),true)) {
            stream.write(message.getBytes(StandardCharsets.UTF_8));
        } catch(IOException ignored) {}
    }
    public synchronized void exitGame(int code) {
        inputReady = false;
        try { writeStatus("exited",code); } catch(Exception ignored) {}
        runOnUiThread(() -> { finish(); android.os.Process.killProcess(android.os.Process.myPid()); });
    }
    private void showError(Throwable error) {
        if (sessionDir != null) try {
            appendLog("\n" + error + "\n");
            writeStatus("exited",-1);
        } catch(Exception ignored) {}
        runOnUiThread(() -> {
            TextView text = new TextView(this); text.setText("游戏启动失败\n" + error + "\n日志可在启动器中查看");
            setContentView(text);
        });
    }
    @Override public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        if (launched) { if (nativeReady) { CallbackBridge.setupBridgeWindow(surface); inputReady=true; } return; }
        launched = true;
        new Thread(() -> {
            try {
                File root = GameRuntime.install(this);
                writeStatus("running",null);
                exitGame(GameRuntime.launch(this,request,root,new File(sessionDir,"game.log")));
            } catch(Throwable error) { showError(error); }
        },"RCL-JVM").start();
    }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) {
        width=w; height=h;
        if (inputReady) CallbackBridge.nativeSendScreenSize(w,h);
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { inputReady=false; }
    @Override public void onBackPressed() {
        if(inputReady) { CallbackBridge.nativeSendKey(256,0,1,0); CallbackBridge.nativeSendKey(256,0,0,0); }
        else { finish(); android.os.Process.killProcess(android.os.Process.myPid()); }
    }
}
