package com.gathin.tvhome;
import android.content.*;import android.media.AudioManager;import android.os.*;
/** Owns TV STREAM_MUSIC only while BGM plays; restore follows output release and a quiet guard. */
final class AuroraTvVolume{
 private final AudioManager audio;private final SharedPreferences prefs;private final Handler main=new Handler(Looper.getMainLooper());
 private int prior=-1,revision;private long quietUntil;
 AuroraTvVolume(Context c){audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);prefs=c.getSharedPreferences("aurora_bgm_tv_volume",0);prior=prefs.getInt("restore",-1);if(prior>=0)restoreAfterRelease();}
 int max(){return audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);}int current(){return audio.getStreamVolume(AudioManager.STREAM_MUSIC);}int prior(){return prior;}
 long waitMs(){return Math.max(0,quietUntil-SystemClock.elapsedRealtime());}
 boolean begin(int value){if(waitMs()>0)return false;if(prior<0){prior=current();prefs.edit().putInt("restore",prior).commit();}audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max(),value)),0);return true;}
 void adjust(int value){if(prior>=0&&waitMs()==0)audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max(),value)),0);}
 void restoreAfterRelease(){if(prior<0||waitMs()>0)return;quietUntil=SystemClock.elapsedRealtime()+AmbientVolume.OUTPUT_SETTLE_MS;int token=++revision;main.postDelayed(()->{if(revision!=token)return;int value=prior;prior=-1;quietUntil=0;if(value>=0)audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.min(max(),value),0);prefs.edit().remove("restore").commit();},AmbientVolume.OUTPUT_SETTLE_MS);}
}
