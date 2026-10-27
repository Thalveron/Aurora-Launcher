package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Restores the owner's opt-in home helper after TCL resets accessibility at boot. */
final class HomeCompatibility {
    private static final String SERVICE = "com.gathin.tvhome/com.gathin.tvhome.HomeKeyService";
    private static boolean pending;
    private static android.content.SharedPreferences preferences(Context c) {
        Context protectedContext = c.createDeviceProtectedStorageContext();
        android.os.UserManager users = (android.os.UserManager) c.getSystemService(Context.USER_SERVICE);
        if (users != null && users.isUserUnlocked())
            protectedContext.moveSharedPreferencesFrom(c, "home_compat");
        return protectedContext.getSharedPreferences("home_compat", 0);
    }
    static boolean enabled(Context c) {
        return preferences(c).getBoolean("enabled", true);
    }
    static void setEnabled(Context c, boolean value) {
        preferences(c).edit().putBoolean("enabled", value).apply();
        if (value) ensureEnabled(c);
        else if (canWrite(c)) {
            List<String> items = services(c);
            items.removeIf(HomeCompatibility::ours);
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", items));
            if (items.isEmpty()) Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        }
    }
    private static boolean canWrite(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }
    private static boolean ours(String s) {
        return s.equals(SERVICE) || s.equals("com.gathin.tvhome/.HomeKeyService");
    }
    private static List<String> services(Context c) {
        String raw = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        List<String> items = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) for (String s : raw.split(":")) if (!s.isEmpty()) items.add(s);
        return items;
    }
    static void ensureEnabled(Context context) {
        Context c = context.getApplicationContext();
        if (!enabled(c) || !canWrite(c) || pending) return;
        List<String> items = services(c);
        if (items.stream().anyMatch(HomeCompatibility::ours)
                && Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) return;
        pending = true;
        items.removeIf(HomeCompatibility::ours);
        Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", items));
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            pending = false;
            if (!enabled(c)) return;
            List<String> current = services(c);
            current.removeIf(HomeCompatibility::ours);
            current.add(SERVICE);
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", current));
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        }, 250);
    }
}
