package com.gathin.tvhome;
import android.content.*;import android.media.AudioManager;
/** Independent software gain, never changes STREAM_MUSIC. */
final class BgmVolume {
 static int percent(Context c){SharedPreferences p=c.getSharedPreferences("aurora_bgm_shared_saver",0);if(!p.contains("software_volume")){AudioManager a=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);int value=p.contains("volume_index")?Math.round(p.getInt("volume_index",0)*100f/Math.max(1,a.getStreamMaxVolume(AudioManager.STREAM_MUSIC))):p.getInt("volume",15);value=Math.max(0,Math.min(100,value));p.edit().putInt("software_volume",value).commit();}return Math.max(0,Math.min(100,p.getInt("software_volume",15)));}
 static float gain(Context c){return percent(c)/100f;}
 static void set(Context c,int value){if(value<0||value>100)throw new IllegalArgumentException("屏保独立音量范围0～100%");if(!c.getSharedPreferences("aurora_bgm_shared_saver",0).edit().putInt("software_volume",value).commit())throw new IllegalStateException("音量保存失败");}
}
