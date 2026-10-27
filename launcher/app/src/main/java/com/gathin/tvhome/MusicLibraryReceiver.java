package com.gathin.tvhome;
import android.content.*;

/** Signed source changes clear stale card text even while Home is in the background. */
public final class MusicLibraryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        context.getSharedPreferences(Config.SETTINGS_NAME,0).edit().remove("music_last_title").remove("music_last_artist").remove("music_last_duration").remove("music_last_position").apply();
        context.sendBroadcast(new Intent(MusicNotificationListener.CHANGED).setPackage(context.getPackageName()));
    }
}
