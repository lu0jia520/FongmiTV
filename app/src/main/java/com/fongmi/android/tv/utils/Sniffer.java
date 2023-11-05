package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.utils.Util;
import com.orhanobut.logger.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Sniffer {

    private static final String TAG = Sniffer.class.getSimpleName();

    public static final Pattern CLICKER = Pattern.compile("\\[a=cr:(\\{.*?\\})\\/](.*?)\\[\\/a]");
    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36";
    public static final String RULE = "http((?!http).){12,}?\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg|m4a|mp3)\\?.*|http((?!http).){12,}\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg|m4a|mp3)|http((?!http).)*?video/tos*";
    public static final List<String> PUSH = Arrays.asList("smb", "http", "https", "thunder", "magnet", "ed2k", "mitv", "jianpian");
    public static final List<String> THUNDER = Arrays.asList("thunder", "magnet", "ed2k");

    public static boolean isPush(Uri uri) {
        return PUSH.contains(uri.getScheme());
    }

    public static boolean isThunder(String url) {
        return THUNDER.contains(Util.scheme(url)) || isTorrent(url);
    }

    public static boolean isTorrent(String url) {
        return url.split(";")[0].endsWith(".torrent");
    }

    public static boolean isVideoFormat(String url) {
        return isVideoFormat(url, new HashMap<>());
    }

    public static boolean isVideoFormat(String url, Map<String, String> headers) {
        Logger.t(TAG).d(url);
        if (matchOrContain(url)) return true;
        if (headers.containsKey("Accept") && headers.get("Accept").startsWith("image")) return false;
        if (url.contains("url=http") || url.contains("v=http") || url.contains(".css") || url.contains(".html")) return false;
        return url.matches(RULE);
    }

    private static boolean matchOrContain(String url) {
        List<String> items = getRegex(Uri.parse(url));
        for (String regex : items) if (url.contains(regex)) return true;
        for (String regex : items) if (url.matches(regex)) return true;
        return false;
    }

    public static List<String> getRegex(Uri uri) {
        if (uri.getHost() == null) return Collections.emptyList();
        String hosts = TextUtils.join(",", Arrays.asList(Util.host(uri), Util.host(uri.getQueryParameter("url"))));
        for (Rule rule : ApiConfig.get().getRules()) for (String host : rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule.getRegex();
        return Collections.emptyList();
    }
}
