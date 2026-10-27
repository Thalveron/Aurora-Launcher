package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.app.ActivityOptions;
import android.os.Build;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {
    static volatile HomeKeyService current;

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    private long lastHomeLaunch;
    private int idleWakeKey=-1;
    private final BroadcastReceiver homeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.ENTER_HOME".equals(intent.getAction())) openHome();
            else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())
                    && "homekey".equals(intent.getStringExtra("reason"))) openHome();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction("android.intent.action.ENTER_HOME");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(homeReceiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(homeReceiver, filter);
    }

    @Override public void onDestroy() {
        current=null;
        unregisterReceiver(homeReceiver);
        super.onDestroy();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        current=this;
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if(IdleStandby.key(this,event))return true;
        if(event.getKeyCode()==idleWakeKey){if(event.getAction()==KeyEvent.ACTION_UP)idleWakeKey=-1;return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN&&MainActivity.wakeAmbientIfIdle()){
            idleWakeKey=event.getKeyCode();return true;
        }
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : SystemSettings.intent(this, Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS && event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !event.isCanceled()) {
            openHome();
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!"com.tcl.cyberui".contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        if (!"com.tcl.cyberui.MainActivity".contentEquals(event.getClassName() == null ? "" : event.getClassName())) return;
        if (android.os.SystemClock.uptimeMillis() - event.getEventTime() > 750) return;
        openHome();
    }

    private void openHome() {
        openHome(true);
    }
    private void openHome(boolean userInitiated) {
        if(IdleStandby.home(this))return;
        if (!HomeCompatibility.enabled(this)) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHomeLaunch < 100) return;
        lastHomeLaunch = now;
        try {
            boolean nativeHome=AuroraThemes.current(this).equals("legacy");
            if(nativeHome&&MainActivity.focusHomeInPlace())return;
            if(!nativeHome&&AuroraActivity.focusHomeInPlace())return;
            startActivity(new Intent(this, nativeHome?MainActivity.class:AuroraActivity.class)
                    .addCategory(Intent.CATEGORY_HOME)
                    .putExtra("user_home",userInitiated)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        } catch (RuntimeException failure) {
            android.util.Log.w("TvHome", "Could not open home", failure);
        }
    }

    @Override
    public void onInterrupt() {
    }
}
