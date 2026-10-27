package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Resolves standard settings, including manufacturer-specific entry points. */
final class SystemSettings {
    private SystemSettings() {}
    static Intent intent(Context context, String action) {
        Intent standard = new Intent(action);
        if (standard.resolveActivity(context.getPackageManager()) != null) return standard;
        Intent tcl = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(
                "com.tcl.settings", "com.tcl.settings.ui.MainActivity"));
        if (tcl.resolveActivity(context.getPackageManager()) != null) {
            if (Settings.ACTION_WIFI_SETTINGS.equals(action))
                tcl.setAction("android.intent.action.NETWORKSETTINGS");
            return tcl;
        }
        Intent xiaomi = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        return xiaomi.resolveActivity(context.getPackageManager()) != null ? xiaomi : standard;
    }
}
