package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)) return;
        Context app = context.getApplicationContext();
        HomeCompatibility.ensureEnabled(app);
        UserManager users = (UserManager) app.getSystemService(Context.USER_SERVICE);
        if (users != null && users.isUserUnlocked()) {
            launch(app, action);
            return;
        }
        PendingResult pending = goAsync();
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean done = new AtomicBoolean();
        BroadcastReceiver unlock = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (!Intent.ACTION_USER_UNLOCKED.equals(i.getAction()) || !done.compareAndSet(false, true)) return;
                app.unregisterReceiver(this);
                launch(app, action);
                pending.finish();
            }
        };
        app.registerReceiver(unlock, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        if (users != null && users.isUserUnlocked()) unlock.onReceive(app, new Intent(Intent.ACTION_USER_UNLOCKED));
        handler.postDelayed(() -> {
            if (done.compareAndSet(false, true)) {
                app.unregisterReceiver(unlock);
                pending.finish();
            }
        }, 8000);
    }

    private static void launch(Context context, String action) {
        UploadService.ensureStarted(context);
        // Android's configured HOME activity owns initial display selection.
        // A late BOOT_COMPLETED must never cover an HDMI/CEC or app selected meanwhile.
        android.util.Log.i("TvHome", "Boot services ready; foreground left to system HOME/CEC");
    }
}
