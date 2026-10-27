package com.gathin.tvhome;
import android.content.*;
import java.util.concurrent.*;

/** Compatibility cleanup signal: the home entry no longer stores wallpaper previews. */
final class BridgePreview {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    static void sync(Context context) {
        Context c=context.getApplicationContext();
        worker.execute(() -> {
            try {
                byte[] data = new byte[0];
                c.sendBroadcast(new Intent("org.cleanyourtv.homebridge.UPDATE_PREVIEW")
                    .setComponent(new ComponentName("com.alibaba.dingtalk.focus","org.cleanyourtv.homebridge.PreviewReceiver"))
                    .putExtra("preview",data));
            } catch (Exception e) { android.util.Log.w("TvHome","Home preview update failed",e); }
        });
    }
}
