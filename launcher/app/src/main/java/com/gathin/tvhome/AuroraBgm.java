package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Main-thread-owned, local-only audio; attenuates decoded PCM without changing the system media-volume index. */
final class AuroraBgm {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static AuroraBgm instance;
    private final Context context;
    private final AudioManager audio;private final AuroraTvVolume tvVolume;
    private final AudioFocusRequest request;
    private SharedPreferences prefs;private String boundTheme;
    private ExoPlayer current,outgoing,preparedLoop;private AuroraPcmSpectrum pcm,outgoingPcm,preparedPcm;
    private long loopRetryAt;private int loopCount;private final java.util.WeakHashMap<ExoPlayer,Integer> fadeTokens=new java.util.WeakHashMap<>();
    private final Runnable loopWatch=new Runnable(){public void run(){try{maintainLoop();}catch(Exception error){discardPrepared();loopRetryAt=android.os.SystemClock.elapsedRealtime()+5000;}main.postDelayed(this,50);}};
    private double[] mixedSpectrum(){
        double[] result=new double[32];boolean playing=false;
        if(current!=null&&current.isPlaying()&&pcm!=null){playing=true;for(int i=0;i<32;i++)result[i]=pcm.bands[i]*current.getVolume();}
        if(outgoing!=null&&outgoing.isPlaying()&&outgoingPcm!=null){playing=true;for(int i=0;i<32;i++)result[i]=Math.min(1,result[i]+outgoingPcm.bands[i]*outgoing.getVolume());}
        return playing?result:null;
    }
    static double[] spectrum(Context c)throws Exception{return onMain(c,()->instance.mixedSpectrum());}
    private boolean foreground,wanted,focused,manualStop;
    private int operation;
    private String reason="stopped",selected="",diagnostic="";
    private final Runnable observe=new Runnable(){public void run(){
        if(foreground&&wanted){
            android.media.session.MediaController other=AuroraData.controller(context);
            boolean busy=other!=null&&!other.getPackageName().equals(context.getPackageName())&&other.getPlaybackState()!=null
                    &&other.getPlaybackState().getState()==android.media.session.PlaybackState.STATE_PLAYING;
            if(busy&&!reason.equals("other_media"))pauseInternal("other_media",false);
            else if(!busy&&reason.equals("other_media"))try{play();}catch(Exception e){reason=e.getMessage();}
        }
        main.postDelayed(this,1000);
    }};
    private AuroraBgm(Context c) {
        context=c.getApplicationContext();boundTheme=AuroraThemes.current(c);prefs=themePrefs(c,boundTheme);audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        tvVolume=new AuroraTvVolume(c);if(!prefs.contains("volume_index"))prefs.edit().putInt("volume_index",Math.min(tvVolume.max(),prefs.getInt("volume",Math.min(3,tvVolume.current())))).commit();
        if(!prefs.contains("volume_percent")&&!prefs.edit().putInt("volume_percent",BgmPercent.fromIndex(prefs.getInt("volume_index",3),tvVolume.max())).commit())throw new IllegalStateException("保存屏保音量百分比失败");
        request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(change->{
                    if(change==AudioManager.AUDIOFOCUS_GAIN){focused=true;if(foreground&&wanted)try{play();}catch(Exception e){reason=e.getMessage();}}
                    else {focused=false;pauseInternal(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK?"ducked":"focus_lost",false);}
                },main).build();
        main.postDelayed(observe,1000);
        main.postDelayed(loopWatch,50);
    }
    static SharedPreferences themePrefs(Context c,String id){SharedPreferences p=c.getSharedPreferences("aurora_bgm_shared_saver",0);if(!p.getBoolean("_initialized",false)){SharedPreferences.Editor e=p.edit();SharedPreferences old=c.getSharedPreferences("aurora_bgm_theme_"+id,0);if(!old.getBoolean("_initialized",false))old=c.getSharedPreferences("aurora_bgm",0);for(String key:new String[]{"enabled","autoplay","volume","fadeMs","selected","lyrics"}){Object v=old.getAll().get(key);if(v instanceof String)e.putString(key,(String)v);else if(v instanceof Integer)e.putInt(key,(Integer)v);else if(v instanceof Boolean)e.putBoolean(key,(Boolean)v);}if(old.getString("selected","").isEmpty())try{SharedPreferences nativePrefs=c.getSharedPreferences("ambient_music",0);String nativeId=nativePrefs.getString("selected","");if(nativeId.matches("[a-f0-9-]+[.]audio")){File source=new File(c.getFilesDir(),"ambient-music/"+nativeId);if(source.isFile()){String imported=java.util.UUID.randomUUID()+".mp3";java.nio.file.Files.copy(source.toPath(),new File(directory(c),imported).toPath());e.putString("selected",imported);c.getSharedPreferences("aurora_bgm",0).edit().putString("name:"+imported,nativePrefs.getString("name_"+nativeId,"屏保音乐")).commit();}}}catch(Exception ignored){}e.putBoolean("_initialized",true).commit();}return p;}
    private float softwareVolume(){return BgmPercent.gain(prefs.getInt("volume_percent",5));}
    private void applySoftwareVolume(){float gain=softwareVolume();if(pcm!=null)pcm.gain(gain);if(preparedPcm!=null)preparedPcm.gain(gain);if(outgoingPcm!=null)outgoingPcm.gain(gain);}
    private void releaseOutputs(){discardPrepared();if(current!=null){current.setVolume(0);current.pause();current.release();current=null;}if(outgoing!=null){outgoing.setVolume(0);outgoing.release();outgoing=null;outgoingPcm=null;}selected="";pcm=null;outgoingPcm=null;tvVolume.restoreAfterRelease();}
    private void bindTheme(String id){if(id.equals(boundTheme))return;operation++;releaseOutputs();audio.abandonAudioFocusRequest(request);focused=false;wanted=false;manualStop=false;reason="stopped";boundTheme=id;prefs=themePrefs(context,id);}

    static <T> T onMain(Context c,Callable<T> action)throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper()){if(instance==null)instance=new AuroraBgm(c);instance.bindTheme(AuroraThemes.current(c));return action.call();}
        FutureTask<T> task=new FutureTask<>(()->{if(instance==null)instance=new AuroraBgm(c);instance.bindTheme(AuroraThemes.current(c));return action.call();});main.post(task);
        try{return task.get(5,TimeUnit.SECONDS);}
        catch(java.util.concurrent.ExecutionException error){if(error.getCause() instanceof Exception)throw (Exception)error.getCause();throw error;}
        catch(java.util.concurrent.TimeoutException error){task.cancel(false);throw new IOException("桌面正在忙，请稍后重试");}
    }
    private static boolean saverEnabled(Context c){try{return AuroraSettings.get(c).optBoolean("saverEnabled");}catch(Exception e){return false;}}
    static void foreground(Context c,boolean value) {
        main.post(()->{
            if(instance==null)instance=new AuroraBgm(c);instance.bindTheme(AuroraThemes.current(c));instance.foreground=value;
            if(!value)instance.pauseInternal("desktop_hidden",true);
            else if(!saverEnabled(c)&&!instance.manualStop&&instance.prefs.getBoolean("enabled",false)&&instance.prefs.getBoolean("autoplay",instance.prefs.getBoolean("themeAutoplay",false))){
                instance.wanted=true;try{instance.play();}catch(Exception error){instance.reason=error.getMessage();}
            }else if(value&&instance.wanted&&instance.reason.equals("desktop_hidden"))try{instance.play();}catch(Exception e){instance.reason=e.getMessage();}
        });
    }
    static void silence(Context c){main.post(()->{if(instance==null)return;AuroraBgm self=instance;self.wanted=false;self.manualStop=true;self.operation++;self.releaseOutputs();self.audio.abandonAudioFocusRequest(self.request);self.focused=false;self.reason="saver_exit";});}
    static void theme(Context c,String theme){
        main.post(()->{try{
            if(!theme.equals(AuroraThemes.current(c)))return;
            if(instance==null)instance=new AuroraBgm(c);
            instance.bindTheme(theme);
        }catch(Exception error){if(instance!=null)instance.reason="主题音乐不可用："+error.getMessage();}});
    }
    static File directory(Context c)throws IOException {
        File folder=new File(AuroraThemes.root(c),"bgm");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建背景音乐目录");return folder;
    }
    static File file(Context c,String id)throws Exception {
        if(!id.matches("[a-f0-9-]{36}\\.(ogg|flac|wav|mp3|m4a|aac|opus)"))throw new IllegalArgumentException("背景音乐编号无效");
        File file=AuroraRules.inside(directory(c),id);if(!file.isFile())throw new FileNotFoundException("背景音乐已丢失，请重新选择");return file;
    }
    static JSONArray library(Context c)throws Exception {
        File[] files=directory(c).listFiles((dir,name)->name.matches("[a-f0-9-]{36}\\.(ogg|flac|wav|mp3|m4a|aac|opus)"));
        JSONArray result=new JSONArray();if(files==null)return result;Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        SharedPreferences prefs=c.getSharedPreferences("aurora_bgm",0);
        for(File file:files)result.put(new JSONObject().put("id",file.getName()).put("name",prefs.getString("name:"+file.getName(),file.getName())).put("bytes",file.length()));return result;
    }
    static JSONObject upload(Context c,InputStream input,int length,String name)throws Exception {
        if(length<=0||length>256*1024*1024)throw new IllegalArgumentException("单个音乐文件需在256 MB以内");
        String extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT);
        if(!extension.matches("ogg|flac|wav|mp3|m4a|aac|opus"))throw new IllegalArgumentException("请选择OGG、FLAC、WAV、MP3、M4A、AAC或OPUS音频");
        JSONArray all=library(c);long total=length;for(int i=0;i<all.length();i++)total+=all.getJSONObject(i).getLong("bytes");
        if(all.length()>=30||total>500L*1024*1024)throw new IllegalArgumentException("背景音乐库限30首、500 MB");
        String id=java.util.UUID.randomUUID()+"."+extension;File temp=new File(directory(c),id+".part"),target=new File(directory(c),id);
        boolean saved=false;try {
            try(FileOutputStream out=new FileOutputStream(temp)){byte[] buffer=new byte[8192];int left=length;while(left>0){int n=input.read(buffer,0,Math.min(buffer.length,left));if(n<0)throw new EOFException("上传未完成");out.write(buffer,0,n);left-=n;}out.getFD().sync();}
            android.media.MediaExtractor extractor=new android.media.MediaExtractor();boolean audio=false;
            try{extractor.setDataSource(temp.getPath());for(int i=0;i<extractor.getTrackCount();i++){
                String mime=extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/"))throw new IOException("不接受视频文件");if(mime!=null&&mime.startsWith("audio/"))audio=true;
            }}finally{extractor.release();}
            if(!audio)throw new IOException("电视不能识别此音频");
            if(!temp.renameTo(target))throw new IOException("保存失败");saved=true;
            c.getSharedPreferences("aurora_bgm",0).edit().putString("name:"+id,name.substring(0,Math.min(name.length(),160))).commit();
            return new JSONObject().put("id",id).put("name",name).put("bytes",length);
        }finally{if(!saved)temp.delete();}
    }
    static JSONObject state(Context c)throws Exception {return onMain(c,()->instance.snapshot());}
    static JSONObject command(Context c,String command,JSONObject body)throws Exception {
        return onMain(c,()->{
            AuroraBgm self=instance;
            switch(command){
                case "play":
                    if(body.has("id")){file(c,body.getString("id"));self.prefs.edit().putString("selected",body.getString("id")).commit();}
                    self.prefs.edit().putBoolean("enabled",true).commit();self.manualStop=false;self.wanted=true;self.play();break;
                case "pause":self.manualStop=true;self.wanted=false;self.pauseInternal("manual_pause",true);break;
                case "stop":self.manualStop=true;self.wanted=false;self.stop();break;
                case "volume":
                    int volume=body.getInt("volume");if(volume<0||volume>100)throw new IllegalArgumentException("屏保音量范围为 0～100%");
                    if(!self.prefs.edit().putInt("volume_percent",volume).commit())throw new IllegalStateException("保存屏保音量失败");self.applySoftwareVolume();break;
                case "settings":
                    SharedPreferences.Editor edit=self.prefs.edit();
                    for(java.util.Iterator<String> keys=body.keys();keys.hasNext();){String key=keys.next();
                        switch(key){case "enabled":case "autoplay":edit.putBoolean(key,body.getBoolean(key));break;
                            case "fadeMs":int duration=body.getInt(key);if(duration<0||duration>10000)throw new IllegalArgumentException("淡化时长范围0～10000毫秒");edit.putInt(key,duration);break;
                            case "lyrics":String lyric=body.getString(key);if(!lyric.isEmpty())AuroraLibrary.file(c,lyric,"lyrics");edit.putString(key,lyric);break;
                            case "selected":file(c,body.getString(key));edit.putString(key,body.getString(key));break;
                            default:throw new IllegalArgumentException("未知音乐设置："+key);}}
                    edit.commit();if(!self.prefs.getBoolean("enabled",false)){self.wanted=false;self.stop();}break;
                case "delete":
                    String id=body.getString("id");File file=file(c,id);
                    if(id.equals(self.selected))throw new IllegalArgumentException("请先切换其他音乐，或停止后清除当前选择再删除");
                    if(!file.delete())throw new IOException("删除失败");SharedPreferences.Editor removed=self.prefs.edit().remove("name:"+id);if(id.equals(self.prefs.getString("selected","")))removed.remove("selected");removed.commit();break;
                default:throw new IllegalArgumentException("未知背景音乐操作");
            }
            return self.snapshot();
        });
    }
    private void play()throws Exception {
        if(!foreground){reason="desktop_hidden";return;}
        File source=file(context,prefs.getString("selected",prefs.getString("themeSelected","")));
        android.media.session.MediaController other=AuroraData.controller(context);
        if(other!=null&&!other.getPackageName().equals(context.getPackageName())&&other.getPlaybackState()!=null&&other.getPlaybackState().getState()==3){reason="other_media";return;}
        if(!focused){focused=audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!focused){reason="focus_denied";return;}}
        if(current!=null&&current.isPlaying()&&source.getName().equals(selected)&&reason.equals("playing"))return;
        final int revision=++operation;reason="preparing";
        if(current==null||!source.getName().equals(selected)){
            if(outgoing!=null){outgoing.release();outgoing=null;}
            outgoing=current;outgoingPcm=pcm;if(outgoing!=null){ExoPlayer old=outgoing;fade(old,0f,revision,()->{old.release();if(outgoing==old){outgoing=null;outgoingPcm=null;}});}
            discardPrepared();selected=source.getName();pcm=new AuroraPcmSpectrum();pcm.gain(softwareVolume());
            current=createPlayer(source,pcm);
            ExoPlayer player=current;
            player.addListener(new Player.Listener(){
                private boolean started;
                @Override public void onPlaybackStateChanged(int state){
                    // READY also fires after buffering/repeat: fade only on the initial start.
                    if(!started&&current==player&&state==Player.STATE_READY&&wanted&&foreground&&focused){started=true;reason="playing";fade(player,1f,operation,null);}
                }
            });
            player.prepare();player.play();
        }else{current.play();reason="playing";pcm.gain(softwareVolume());fade(current,1f,revision,null);}
    }
    private ExoPlayer createPlayer(File source,AuroraPcmSpectrum processor){
        androidx.media3.exoplayer.DefaultRenderersFactory factory=new androidx.media3.exoplayer.DefaultRenderersFactory(context){
            @Override protected androidx.media3.exoplayer.audio.AudioSink buildAudioSink(Context c,boolean floats,boolean params){
                return new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(c).setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{processor}).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(params).build();
            }
        };
        ExoPlayer player=new ExoPlayer.Builder(context,factory).setLooper(Looper.getMainLooper()).build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);player.setVolume(0f);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error){
                if(preparedLoop==player){discardPrepared();loopRetryAt=android.os.SystemClock.elapsedRealtime()+5000;return;}
                if(current==player){wanted=false;operation++;reason="播放失败："+error.getErrorCodeName();java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));diagnostic=trace.toString();releaseOutputs();audio.abandonAudioFocusRequest(request);focused=false;}
            }
        });
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(source)));
        return player;
    }
    private void discardPrepared(){if(preparedLoop!=null){preparedLoop.release();preparedLoop=null;}preparedPcm=null;}
    private void maintainLoop()throws Exception{
        if(current==null||!wanted||!foreground||!focused||outgoing!=null||!current.isPlaying())return;
        long duration=current.getDuration();if(duration<=0)return;
        int overlap=(int)Math.min(prefs.getInt("fadeMs",1500),duration/4);
        if(overlap<=0){discardPrepared();return;}
        long remaining=duration-current.getCurrentPosition();
        if(preparedLoop==null&&remaining<=overlap+3000&&android.os.SystemClock.elapsedRealtime()>=loopRetryAt){
            preparedPcm=new AuroraPcmSpectrum();preparedPcm.gain(softwareVolume());
            preparedLoop=createPlayer(file(context,selected),preparedPcm);preparedLoop.prepare();
        }
        if(preparedLoop==null||preparedLoop.getPlaybackState()!=Player.STATE_READY||remaining>overlap)return;
        final ExoPlayer old=current,next=preparedLoop;final AuroraPcmSpectrum oldPcm=pcm;
        final int revision=++operation;final int durationMs=(int)Math.max(100,Math.min(overlap,remaining));
        outgoing=old;outgoingPcm=oldPcm;current=next;pcm=preparedPcm;preparedLoop=null;preparedPcm=null;
        next.play();reason="playing";loopCount++;
        // Wait for the decoder/audio sink to actually start before reducing the old output.
        final long deadline=android.os.SystemClock.elapsedRealtime()+1500;
        main.post(new Runnable(){public void run(){
            if(revision!=operation||current!=next)return;
            if((!next.isPlaying()||pcm.renderedSamples==0)&&android.os.SystemClock.elapsedRealtime()<deadline){main.postDelayed(this,20);return;}
            final long start=android.os.SystemClock.elapsedRealtime();
            main.post(new Runnable(){public void run(){
                if(revision!=operation||current!=next||outgoing!=old)return;
                float progress=Math.min(1f,(android.os.SystemClock.elapsedRealtime()-start)/(float)durationMs);
                next.setVolume((float)Math.sin(progress*Math.PI/2));old.setVolume((float)Math.cos(progress*Math.PI/2));
                if(progress<1f)main.postDelayed(this,20);
                else {old.setVolume(0);old.release();outgoing=null;outgoingPcm=null;}
            }});
        }});
    }

    private void pauseInternal(String why,boolean abandon) {
        int revision=++operation;reason=why;discardPrepared();
        if(outgoing!=null){outgoing.setVolume(0);outgoing.release();outgoing=null;outgoingPcm=null;}
        if(current!=null){ExoPlayer player=current;fade(player,0f,revision,()->{releaseOutputs();if(abandon){audio.abandonAudioFocusRequest(request);focused=false;}});}
        else {tvVolume.restoreAfterRelease();if(abandon){audio.abandonAudioFocusRequest(request);focused=false;}}
    }
    private void stop(){pauseInternal("stopped",true);}
    private void fade(ExoPlayer player,float target,int revision,Runnable done) {
        final int token=fadeTokens.getOrDefault(player,0)+1;fadeTokens.put(player,token);float from=player.getVolume();long start=android.os.SystemClock.elapsedRealtime();int duration=prefs.getInt("fadeMs",1500);
        main.post(new Runnable(){public void run(){if(revision!=operation||!Integer.valueOf(token).equals(fadeTokens.get(player))||(player!=current&&player!=outgoing))return;
            float progress=duration==0?1f:Math.min(1f,(android.os.SystemClock.elapsedRealtime()-start)/(float)duration);
            player.setVolume(from+(target-from)*progress);if(progress<1f)main.postDelayed(this,40);else if(done!=null)done.run();
        }});
    }
    private JSONObject snapshot()throws Exception {
        return new JSONObject().put("engine","Media3 ExoPlayer").put("diagnostic",(context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0?diagnostic:"").put("repeat","one").put("loopMode",prefs.getInt("fadeMs",1500)>0?"crossfade":"gapless").put("loopCount",loopCount).put("crossfading",outgoing!=null).put("loopPrepared",preparedLoop!=null).put("playing",current!=null&&current.isPlaying())
                .put("foreground",foreground).put("reason",reason).put("enabled",prefs.getBoolean("enabled",false)).put("autoplay",prefs.getBoolean("autoplay",prefs.getBoolean("themeAutoplay",false)))
                .put("actualGain",current==null||pcm==null?0:current.getVolume()*pcm.requestedGain()).put("softwareGain",pcm==null?0:pcm.requestedGain()).put("inputRms",pcm==null?0:pcm.inputRms).put("outputRms",pcm==null?0:pcm.outputRms).put("renderedSamples",pcm==null?0:pcm.renderedSamples).put("volume",prefs.getInt("volume_percent",5)).put("volumeMax",100).put("volumeUnit","percent").put("systemVolume",tvVolume.current()).put("desktopVolume",tvVolume.prior()).put("volumeMode","software").put("fadeMs",prefs.getInt("fadeMs",1500)).put("selected",prefs.getString("selected",prefs.getString("themeSelected","")))
                .put("lyrics",prefs.getString("lyrics","")).put("lyricRows",AuroraLyrics.read(context,prefs.getString("lyrics","")))
                .put("positionMs",current==null?0:current.getCurrentPosition()).put("durationMs",current==null?0:Math.max(0,current.getDuration()));
    }
}
