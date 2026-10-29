package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Iterator;

/** Versioned adapter for the original desktop; never exposes raw preference stores. */
final class ConsoleNativeSettings {
    private ConsoleNativeSettings() {}

    static JSONObject read(Context context) throws Exception {
        requireNative(context);
        JSONObject state = WebSettings.state(context);
        state.remove("uploadPath"); // Security configuration belongs to /security only.
        return state;
    }

    static JSONObject save(Context context, JSONObject body) throws Exception {
        requireNative(context);
        AuroraSettings.only(body, "changes");
        JSONObject changes = body.getJSONObject("changes");
        // Shared screensaver and location now have one authoritative service.
        for (Iterator<String> keys = changes.keys(); keys.hasNext();) {
            String key = keys.next();
            if (key.startsWith("ambient:") || key.startsWith("location:"))
                throw new IllegalArgumentException("屏保请在屏保页面设置，时间与城市请在天气与时间页面设置");
        }
        boolean rebuild = WebSettings.apply(context, changes);
        JSONObject result = read(context);
        JSONArray fields = result.getJSONArray("fields");
        for (Iterator<String> keys = changes.keys(); keys.hasNext();) {
            String key = keys.next();
            Object actual = null;
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (key.equals(field.getString("id"))) actual = field.get("value");
            }
            if (actual == null || !String.valueOf(changes.get(key)).equalsIgnoreCase(String.valueOf(actual)))
                throw new IOException("保存后回读不一致：" + key + "，请刷新后检查");
        }
        context.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED")
                .setPackage(context.getPackageName()).putExtra("kind", rebuild ? "settings-layout" : "settings"));
        return result.put("verified", true);
    }

    static void requireNative(Context context) throws Exception {
        if (!"legacy".equals(AuroraThemes.current(context)))
            throw new UnsupportedOperationException("当前主题不支持原生桌面设置，请使用当前主题设置");
    }

    static JSONObject resetColor(Context context, JSONObject body) throws Exception {
        requireNative(context);AuroraSettings.only(body,"id");
        boolean rebuild=WebSettings.resetColor(context,body.getString("id"));
        context.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(context.getPackageName()).putExtra("kind",rebuild?"settings-layout":"settings"));
        return read(context);
    }
}
