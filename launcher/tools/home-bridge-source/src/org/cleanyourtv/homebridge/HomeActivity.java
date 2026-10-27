package org.cleanyourtv.homebridge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.widget.TextView;

public final class HomeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retry = this::openDesktop;
    private boolean waiting;
    private android.widget.ImageView starting;
    private final BroadcastReceiver unlocked = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (hasWindowFocus()) openDesktop(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        getWindow().setFlags(1024,1024);
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);
        frame.setBackgroundColor(0xff101419);
        new android.util.AtomicFile(new java.io.File(createDeviceProtectedStorageContext().getFilesDir(),"startup-preview.jpg")).delete();
        starting=new android.widget.ImageView(this);
        try { starting.setImageBitmap(android.graphics.BitmapFactory.decodeStream(getAssets().open("startup_message.png"))); } catch(java.io.IOException ignored) {}
        starting.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        android.widget.FrameLayout.LayoutParams textParams=new android.widget.FrameLayout.LayoutParams(-1,100,android.view.Gravity.BOTTOM);textParams.bottomMargin=40;frame.addView(starting,textParams);
        setContentView(frame);
        registerReceiver(unlocked, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
    }

    @Override protected void onResume() {
        super.onResume();
        openDesktop();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openDesktop();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(retry);
        super.onPause();
    }
    @Override protected void onDestroy() { unregisterReceiver(unlocked); super.onDestroy(); }

    private void openDesktop() {
        handler.removeCallbacks(retry);
        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        if (users != null && !users.isUserUnlocked()) {
            if (!waiting) {
            waiting = true;
            starting.setVisibility(android.view.View.VISIBLE);
            }
            handler.postDelayed(retry, 100);
            return;
        }
        try {
            // Keep the entry message visible until the desktop window takes over.
            Intent desktop = new Intent(Intent.ACTION_MAIN);
            desktop.addCategory(Intent.CATEGORY_HOME);
            desktop.putExtra("user_home",getIntent()!=null&&getIntent().getBooleanExtra("android.intent.extra.FROM_HOME_KEY",false));
            boolean homeKey=getIntent()!=null&&getIntent().getBooleanExtra("android.intent.extra.FROM_HOME_KEY",false);
            int boot=android.provider.Settings.Global.getInt(getContentResolver(),"boot_count",-1);
            android.content.SharedPreferences route=createDeviceProtectedStorageContext().getSharedPreferences("boot_route",0);
            boolean first=!homeKey&&boot>=0&&route.getInt("handled",-2)!=boot;
            route.edit().putInt("handled",boot).commit();
            desktop.setComponent(new ComponentName("com.gathin.tvhome", "com.gathin.tvhome.MainActivity"));
            desktop.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(desktop);
            overridePendingTransition(0, 0);
        } catch (RuntimeException error) {
            TextView recovery = new TextView(this);
            recovery.setText("极简桌面暂时无法打开。按确定进入系统设置。");
            recovery.setTextColor(0xffffffff);
            recovery.setTextSize(26);
            recovery.setGravity(17);
            recovery.setFocusable(true);
            recovery.setOnClickListener(view -> {
                Intent settings = new Intent();
                settings.setComponent(new ComponentName("com.tcl.settings", "com.tcl.settings.ui.MainActivity"));
                try { startActivity(settings); } catch (RuntimeException ignored) { }
            });
            setContentView(recovery);
            recovery.requestFocus();
        }
    }
}
