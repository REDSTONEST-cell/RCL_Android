package com.tungsten.fclauncher.utils;

import android.content.Context;

import java.io.File;

/** RCL：只保留游戏内按键与菜单用到的路径，全部放在应用私有目录，不需要存储权限。 */
public class FCLPath {
    public static String FILES_DIR;
    public static String CONTROLLER_DIR;
    public static String LT_BACKGROUND_PATH;
    public static String DK_BACKGROUND_PATH;

    public static void loadPaths(Context context) {
        FILES_DIR = context.getFilesDir().getAbsolutePath();
        CONTROLLER_DIR = FILES_DIR + "/control";
        LT_BACKGROUND_PATH = FILES_DIR + "/game-menu/lt.png";
        DK_BACKGROUND_PATH = FILES_DIR + "/game-menu/dk.png";
        new File(CONTROLLER_DIR).mkdirs();
    }
}
