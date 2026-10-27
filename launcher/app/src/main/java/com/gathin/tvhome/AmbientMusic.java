package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Local desktop-only music, separate from the user's Navidrome player. */
final class AmbientMusic {
    private static long sharedSyncAt;static SharedPreferences prefs(Context c){SharedPreferences p=c.getSharedPreferences("ambient_music",0);long now=android.os.SystemClock.elapsedRealtime();if(now-sharedSyncAt<1000)return p;sharedSyncAt=now;try{org.json.JSONObject a=AuroraSettings.stored(c);SharedPreferences b=AuroraBgm.themePrefs(c,AuroraThemes.current(c));String id=b.getString("selected","");SharedPreferences.Editor e=p.edit().putBoolean("enabled",a.optBoolean("saverEnabled")).putBoolean("continuous",a.optString("saverMode").equals("continuous")).putInt("minutes",a.optInt("saverMinutes",5)).putString("greeting",a.optString("greeting")).putInt("greeting_x",a.optInt("greetingX",50)).putInt("greeting_y",a.optInt("greetingY",32)).putInt("info_mode",a.optString("saverInfo").equals("hidden")?2:a.optString("saverInfo").equals("minimal")?1:0).putBoolean("lyrics_visible",!a.optString("saverLyricsLayout").equals("hidden")).putBoolean("spectrum_visible",a.getJSONObject("saverSpectrum").optInt("opacity",50)>0);e.putString("selected",id);if(!id.isEmpty()){String lyric=b.getString("lyrics","");if(!lyric.isEmpty())try{e.putString("lyrics_"+id,new String(java.nio.file.Files.readAllBytes(AuroraLibrary.file(c,lyric,"lyrics").toPath()),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}android.media.AudioManager am=(android.media.AudioManager)c.getSystemService(Context.AUDIO_SERVICE);e.putInt("volume_index",Math.round(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*b.getInt("volume",15)/100f));}e.apply();}catch(Exception ignored){}return p;}
    static File directory(Context c){File d=new File(c.getFilesDir(),"ambient-music");d.mkdirs();return d;}
    static List<File> items(Context c){
        File[] all=directory(c).listFiles((d,n)->n.matches("[a-f0-9-]+\\.audio"));
        List<File> result=new ArrayList<>();if(all!=null)result.addAll(Arrays.asList(all));
        result.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));return result;
    }
    static File byId(Context c,String id){try{return AuroraBgm.file(c,id);}catch(Exception ignored){}for(File f:items(c))if(f.getName().equals(id))return f;return null;}
    static String name(Context c,File f){return prefs(c).getString("name_"+f.getName(),"上传的纯音乐");}
    static void save(Context c,File temp,String name)throws Exception{
        if(items(c).size()>=30)throw new IOException("最多保存30首背景音乐，请先删除不用的文件");
        long total=temp.length();for(File existing:items(c))total+=existing.length();
        if(total>500L*1024*1024)throw new IOException("背景音乐库限500 MB");
        android.media.MediaExtractor extractor=new android.media.MediaExtractor();
        boolean audio=false;
        try{extractor.setDataSource(temp.getPath());for(int i=0;i<extractor.getTrackCount();i++){
            String mime=extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
            if(mime!=null&&mime.startsWith("video/"))throw new IOException("请选择音频文件");
            if(mime!=null&&mime.startsWith("audio/"))audio=true;
        }}finally{extractor.release();}
        if(!audio)throw new IOException("电视无法识别这个音频，请使用 MP3、M4A、FLAC 或 WAV");
        File target=new File(directory(c),java.util.UUID.randomUUID()+".audio");
        if(!temp.renameTo(target))throw new IOException("保存失败");
        prefs(c).edit().putString("name_"+target.getName(),name).putString("selected",target.getName()).commit();
    }
    static void delete(Context c,File file){
        if(file.delete()){
            SharedPreferences.Editor e=prefs(c).edit().remove("name_"+file.getName()).remove("lyrics_"+file.getName());
            if(file.getName().equals(prefs(c).getString("selected","")))e.remove("selected");e.apply();
        }
    }
    private final Context context;
    private final AudioManager audio;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PcmLoopPlayer player;
    private boolean visible,focus,screenKept,idlePresentation,started;
    private final java.util.function.Consumer<Boolean> idleChanged;
    private String playingId;
    private String configuration;
    private boolean heldAfterKey;
    private int priorVolume=-1,appliedVolume=-1;
    private boolean stopping;
    private static long outputSettlingUntil;
    private static AmbientMusic volumeOwner;
    private static final String RESTORE_VOLUME="pending_desktop_volume";
    private long lastInput=SystemClock.elapsedRealtime(),retryAt;
    private final Runnable suspend=()->{if(!this.visible)stop();};
    private final AudioManager.OnAudioFocusChangeListener focusChange=change->{
        if(change<0){stop();lastInput=SystemClock.elapsedRealtime();retryAt=lastInput+3000;}
    };
    AmbientMusic(Context context,java.util.function.Consumer<Boolean> idleChanged){this.context=context;this.idleChanged=idleChanged;audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if(volumeOwner==null&&prefs(context).contains(RESTORE_VOLUME)){
            stopping=true;outputSettlingUntil=SystemClock.elapsedRealtime()+AmbientVolume.OUTPUT_SETTLE_MS;
            handler.postDelayed(()->{if(volumeOwner==null&&prefs(context).contains(RESTORE_VOLUME)){int value=prefs(context).getInt(RESTORE_VOLUME,-1);if(value>=0)audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.min(value,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),0);prefs(context).edit().remove(RESTORE_VOLUME).commit();}stopping=false;},AmbientVolume.OUTPUT_SETTLE_MS);
        }
    }
    void resume(){
        SharedPreferences p=prefs(context);
        String next=p.getBoolean("enabled",false)+":"+p.getBoolean("continuous",false)+":"+p.getString("selected","")+":"+p.getInt("volume_index",-1)+":"+p.getLong("play_request",0);
        if(!next.equals(configuration)){configuration=next;heldAfterKey=false;retryAt=0;}
        visible=true;handler.removeCallbacks(suspend);lastInput=SystemClock.elapsedRealtime();handler.removeCallbacks(tick);handler.post(tick);
    }
    void pause(){visible=false;handler.removeCallbacks(tick);keepScreen(false);handler.removeCallbacks(suspend);handler.postDelayed(suspend,400);}
    void close(){pause();handler.removeCallbacks(suspend);stop();}
    private void keepScreen(boolean enabled){
        if(screenKept==enabled)return;
        if(!(context instanceof android.app.Activity))return;
        screenKept=enabled;
        android.view.Window window=((android.app.Activity)context).getWindow();
        if(enabled)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    long positionMs(){return player==null?0:player.positionMs();}
    String currentLyrics(){return prefs(context).getString("lyrics_"+(playingId==null?"":playingId),"");}
    boolean isIdlePresentation(){return idlePresentation;}
    void settingsChanged(){
        configuration=null;heldAfterKey=false;
        if(!prefs(context).getBoolean("enabled",false)){stop();return;}
        if(player!=null&&started){int next=Math.max(0,Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),prefs(context).getInt("volume_index",appliedVolume)));if(next!=appliedVolume){appliedVolume=next;audio.setStreamVolume(AudioManager.STREAM_MUSIC,next,0);}}
    }
    boolean interaction(){
        boolean waking=idlePresentation;
        lastInput=SystemClock.elapsedRealtime();
        if(started){heldAfterKey=true;stop();}
        return waking;
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(!visible)return;
        SharedPreferences p=prefs(context);long now=SystemClock.elapsedRealtime();
        if(AuroraThemes.current(context).equals("legacy")){
            keepScreen(p.getBoolean("enabled",false));
            long delay=p.getBoolean("continuous",false)?10000:p.getInt("minutes",5)*60_000L;
            if(audio.isMusicActive())lastInput=now;
            if(p.getBoolean("enabled",false)&&now-lastInput>=delay){lastInput=now;try{AuroraSaver.preview(context);}catch(Exception ignored){}}
            handler.postDelayed(this,1000);return;
        }
        String selected=p.getString("selected","");
        android.os.PowerManager power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);
        keepScreen(p.getBoolean("enabled",false));
        if(!p.getBoolean("enabled",false)||(power!=null&&!power.isInteractive())){stop();}
        else if(player!=null&&!selected.equals(playingId)){stop();}
        else if(player==null&&!stopping&&now>=retryAt){
            if(audio.isMusicActive())lastInput=now;
            else if((p.getBoolean("continuous",false)&&!heldAfterKey)||now-lastInput>=p.getInt("minutes",5)*60_000L)start(selected);
        }
        else if(player!=null&&started&&!idlePresentation&&now-lastInput>=p.getInt("minutes",5)*60_000L){
            idlePresentation=true;idleChanged.accept(true);
        }
        handler.postDelayed(this,1000);
    }};
    private void start(String selected){
        if(SystemClock.elapsedRealtime()<outputSettlingUntil||volumeOwner!=null&&volumeOwner!=this)return;
        if(audio.isMusicActive())return;
        if(audio.requestAudioFocus(focusChange,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return;
        focus=true;playingId=selected;
        priorVolume=prefs(context).getInt(RESTORE_VOLUME,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        if(!prefs(context).edit().putInt(RESTORE_VOLUME,priorVolume).commit()){focus=false;audio.abandonAudioFocus(focusChange);return;}
        volumeOwner=this;
        appliedVolume=Math.max(0,Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),prefs(context).getInt("volume_index",Math.min(priorVolume,3))));
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,appliedVolume,0);
        PcmLoopPlayer[] reference=new PcmLoopPlayer[1];
        reference[0]=new PcmLoopPlayer(context,selected,prefs(context).getBoolean("crossfade",true),()->{
            if(player!=reference[0])return;
            SharedPreferences p=prefs(context);
            if(!visible||(!p.getBoolean("continuous",false)&&SystemClock.elapsedRealtime()-lastInput<p.getInt("minutes",5)*60_000L)){stop();return;}
            started=true;heldAfterKey=false;idlePresentation=true;idleChanged.accept(true);
        },()->{if(player==reference[0]){stop();retryAt=SystemClock.elapsedRealtime()+60_000;android.widget.Toast.makeText(context,"背景音乐处理失败，请更换本地音频",android.widget.Toast.LENGTH_LONG).show();}});
        player=reference[0];player.start();
    }
    private void stop(){
        started=false;
        if(idlePresentation){idlePresentation=false;idleChanged.accept(false);}
        if(stopping)return;
        PcmLoopPlayer old=player;player=null;
        if(old==null&&priorVolume<0&&!focus)return;
        stopping=true;
        outputSettlingUntil=SystemClock.elapsedRealtime()+AmbientVolume.OUTPUT_SETTLE_MS;
        Runnable restore=()->{
            if(priorVolume>=0){
                int current=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                int restored=AmbientVolume.afterStop(priorVolume,appliedVolume,current);
                if(restored!=current)audio.setStreamVolume(AudioManager.STREAM_MUSIC,restored,0);
                prefs(context).edit().remove(RESTORE_VOLUME).commit();
                priorVolume=-1;appliedVolume=-1;
                if(volumeOwner==this)volumeOwner=null;
            }
            if(focus){focus=false;audio.abandonAudioFocus(focusChange);}
            stopping=false;
        };
        // Releasing AudioTrack does not empty the TV's downstream output buffer.
        // Keep its low volume through a quiet guard before restoring the shared gain.
        Runnable released=()->{
            long releasedAt=SystemClock.elapsedRealtime();
            outputSettlingUntil=releasedAt+AmbientVolume.OUTPUT_SETTLE_MS;
            handler.postDelayed(()->{
                restore.run();
                long restoredAt=SystemClock.elapsedRealtime();
                new Thread(()->{
                    java.io.File dir=context.getExternalFilesDir(null);
                    if(dir!=null)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"ambient-stop-state.txt"))){out.write(("released_ms="+releasedAt+"\nrestored_ms="+restoredAt+"\nquiet_guard_ms="+(restoredAt-releasedAt)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException ignored){}
                },"ambient-stop-timing").start();
            },AmbientVolume.OUTPUT_SETTLE_MS);
        };
        if(old!=null)old.stop(released);else released.run();
    }
}
