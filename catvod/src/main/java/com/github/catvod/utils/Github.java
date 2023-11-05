package com.github.catvod.utils;

import com.github.catvod.net.OkHttp;

import java.io.File;

public class Github {

    public static final String URL = "https://fongmi.cachefly.net/FongMi/Release/main";

    private static String getUrl(String path, String name) {
        return URL + "/" + path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl("apk/" + (dev ? "dev" : "release"), name + ".json");
    }

    public static String getApk(boolean dev, String name) {
        return getUrl("apk/" + (dev ? "dev" : "release"), name + ".apk");
    }

    public static String getSo(String name) {
        try {
            File file = Path.so(name);
            moveExist(Path.externalCache(), file);
            moveExist(Path.externalFiles(), file);
            String url = name.startsWith("http") ? name : getUrl("so", file.getName());
            if (file.length() < 300) Path.write(file, OkHttp.newCall(url).execute().body().bytes());
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    private static void moveExist(File path, File file) {
        File temp = new File(path, file.getName());
        if (temp.exists()) Path.move(temp, file);
    }
}
