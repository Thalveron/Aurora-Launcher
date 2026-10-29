package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AuroraData {
    private static volatile boolean weatherLoading;
    private static volatile long weatherRetryAt;
    private static volatile JSONArray lyricLines=new JSONArray();
    private static volatile int lyricIndex=-1;private static volatile float lyricProgress;private static volatile String lyricSong="";
    static void lyrics(Intent intent) {
        String[] lines=intent.getStringArrayExtra("window");
        lyricLines=lines==null?new JSONArray():new JSONArray(java.util.Arrays.asList(lines));
        lyricIndex=lines!=null&&lines.length==7?3:intent.getIntExtra("index",-1);lyricProgress=intent.getFloatExtra("progress",0);lyricSong=intent.getStringExtra("song");
    }
    static JSONObject time(Context c)throws Exception {
        AuroraServices.time(c);ZonedDateTime now=java.time.Instant.ofEpochMilli(AuroraServices.now()).atZone(ZoneId.of(AuroraServices.zone(c)));
        return new JSONObject().put("epochMs",AuroraServices.now()).put("timezone",now.getZone().getId())
                .put("time",now.format(DateTimeFormatter.ofPattern(LocationSettings.prefs(c).getBoolean("hour24",true)?"HH:mm":"hh:mm")))
                .put("date",now.toLocalDate().toString()).put("weekday",now.format(DateTimeFormatter.ofPattern("EEEE",Locale.forLanguageTag(DesktopLanguage.code(c)))))
                .put("lunar",DesktopLanguage.lunar(c)).put("festival",ChineseFestivals.on(now.toLocalDate()));
    }
    static JSONArray apps(Context c)throws Exception {
        Map<String,ApplicationInfo> installed=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo info:c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))
                if(info.activityInfo.applicationInfo.enabled)installed.put(info.activityInfo.packageName,info.activityInfo.applicationInfo);
        List<ApplicationInfo> list=new ArrayList<>(installed.values());
        Collections.sort(list,(a,b)->a.loadLabel(c.getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(c.getPackageManager()).toString()));
        JSONArray result=new JSONArray();
        for(ApplicationInfo app:list) result.put(new JSONObject().put("package",app.packageName)
                .put("name",AppManagement.label(c,app.packageName,app.loadLabel(c.getPackageManager()).toString())).put("icon",AppManagement.iconUrl(c,app.packageName)));
        return result;
    }
    static byte[] icon(Context c,String pkg)throws Exception {
        WebApps.valid(c,pkg);
        byte[] custom=AppManagement.icon(c,pkg);if(custom!=null)return custom;
        Drawable drawable=c.getPackageManager().getApplicationIcon(pkg);
        Bitmap bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        drawable.setBounds(0,0,128,128);drawable.draw(canvas);
        ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray();
    }
    static MediaController controller(Context c) {
        try {
            List<MediaController> controllers=((MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE))
                    .getActiveSessions(new ComponentName(c,MusicNotificationListener.class));
            for(MediaController controller:controllers)if(controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING)return controller;
            for(MediaController controller:controllers){MediaMetadata metadata=controller.getMetadata();if(metadata!=null&&metadata.getString(MediaMetadata.METADATA_KEY_TITLE)!=null&&!metadata.getString(MediaMetadata.METADATA_KEY_TITLE).trim().isEmpty())return controller;}
            return AuroraPlayback.controller(c);
        }catch(SecurityException unavailable){return AuroraPlayback.controller(c);}
    }
    static JSONObject media(Context c)throws Exception {
        MediaController controller=controller(c);
        JSONObject result=new JSONObject().put("available",controller!=null).put("playing",false).put("title","").put("artist","")
                .put("positionMs",0).put("durationMs",0).put("lyrics",new JSONArray()).put("lyricIndex",lyricIndex).put("lyricProgress",lyricProgress);
        if(controller==null)return result.put("reason",AuroraPlayback.pending()?"正在连接 Aurora 音乐…":!AuroraPlayback.failure().isEmpty()?AuroraPlayback.failure():"点击播放可恢复 Aurora 音乐").put("pending",AuroraPlayback.pending()).put("controls",new JSONObject().put("play",AuroraPlayback.installed(c)).put("queue",false));
        MediaMetadata metadata=controller.getMetadata();PlaybackState state=controller.getPlaybackState();
        result.put("package",controller.getPackageName());
        if(controller.getPackageName().equals("com.craftworks.music.tvcustom")&&(metadata==null||lyricSong==null||lyricSong.equals(metadata.getString(MediaMetadata.METADATA_KEY_TITLE))))result.put("lyrics",lyricLines);
        if(metadata!=null)result.put("title",metadata.getString(MediaMetadata.METADATA_KEY_TITLE)).put("artist",metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
                .put("durationMs",metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)).put("cover","/api/v1/media/cover");
        android.support.v4.media.session.MediaControllerCompat compat=new android.support.v4.media.session.MediaControllerCompat(c,android.support.v4.media.session.MediaSessionCompat.Token.fromToken(controller.getSessionToken()));
        long actions=state==null?0:state.getActions();
        result.put("mode",compat.getShuffleMode()==1?2:compat.getRepeatMode()==1?0:1);
        if(state!=null){long position=state.getPosition();boolean playing=state.getState()==PlaybackState.STATE_PLAYING;
            if(playing)position+=(long)((SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());
            long duration=result.optLong("durationMs");position=Math.max(0,duration>0?Math.min(duration,position):position);
            result.put("playing",playing).put("positionMs",position).put("state",state.getState());}
        return result.put("controls",new JSONObject().put("play",(actions&PlaybackState.ACTION_PLAY)!=0).put("pause",(actions&PlaybackState.ACTION_PAUSE)!=0)
                .put("next",(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0).put("prev",(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0)
                .put("mode",(actions&android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_REPEAT_MODE)!=0&&(actions&android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)!=0).put("seek",(actions&PlaybackState.ACTION_SEEK_TO)!=0).put("queue",controller.getQueue()!=null).put("lyrics",lyricLines.length()>0));
    }
    static JSONObject transport(Context c,String action,JSONObject body)throws Exception {
        MediaController controller=controller(c);if(controller==null){if(action.equals("play")&&AuroraPlayback.installed(c)){AuroraPlayback.start(c);return media(c).put("pending",true);}throw new UnsupportedOperationException("没有可控制的播放会话");}
        if(action.equals("play")&&controller.getPackageName().equals("com.craftworks.music.tvcustom")&&(controller.getMetadata()==null||android.text.TextUtils.isEmpty(controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE)))){AuroraPlayback.start(c);return media(c).put("pending",true);}
        if(action.equals("mode")){int mode=body.getInt("mode");AuroraSettings.range(mode,0,2);android.support.v4.media.session.MediaControllerCompat cc=new android.support.v4.media.session.MediaControllerCompat(c,android.support.v4.media.session.MediaSessionCompat.Token.fromToken(controller.getSessionToken()));long flags=cc.getPlaybackState()==null?0:cc.getPlaybackState().getActions();if((flags&android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_REPEAT_MODE)==0||(flags&android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)==0)throw new UnsupportedOperationException("播放器不支持切换模式");cc.getTransportControls().setShuffleMode(mode==2?1:0);cc.getTransportControls().setRepeatMode(mode==0?1:2);return media(c);}
        PlaybackState state=controller.getPlaybackState();long available=state==null?0:state.getActions();
        MediaController.TransportControls controls=controller.getTransportControls();long required;
        switch(action){case "play":required=PlaybackState.ACTION_PLAY;break;case "pause":required=PlaybackState.ACTION_PAUSE;break;
            case "next":required=PlaybackState.ACTION_SKIP_TO_NEXT;break;case "prev":required=PlaybackState.ACTION_SKIP_TO_PREVIOUS;break;
            case "seek":required=PlaybackState.ACTION_SEEK_TO;break;default:throw new UnsupportedOperationException("播放器未提供此控制");}
        if((available&required)==0)throw new UnsupportedOperationException("当前播放器未提供此控制");
        switch(action){case "play":controls.play();break;case "pause":controls.pause();break;case "next":controls.skipToNext();break;
            case "prev":controls.skipToPrevious();break;case "seek":long position=body.getLong("positionMs");
                long duration=controller.getMetadata()==null?0:controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                if(position<0||(duration>0&&position>duration))throw new IllegalArgumentException("播放位置超出范围");controls.seekTo(position);break;}
        return media(c);
    }
    static byte[] cover(Context c)throws Exception {
        MediaController controller=controller(c);MediaMetadata m=controller==null?null:controller.getMetadata();
        if(m==null)throw new java.io.FileNotFoundException("没有封面");
        Bitmap source=m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);if(source==null)source=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(source==null)source=m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if(source==null)throw new java.io.FileNotFoundException("播放器没有提供封面");
        float scale=Math.min(1f,256f/Math.max(source.getWidth(),source.getHeight()));
        Bitmap small=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);
        ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,80,out);if(small!=source)small.recycle();return out.toByteArray();
    }
    static JSONObject status(Context c)throws Exception {
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        java.io.File files=c.getFilesDir();android.net.ConnectivityManager cm=(android.net.ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkCapabilities network=cm.getNetworkCapabilities(cm.getActiveNetwork());
        JSONObject bluetooth=new JSONObject().put("known",false);
        try{android.bluetooth.BluetoothAdapter b=android.bluetooth.BluetoothAdapter.getDefaultAdapter();bluetooth.put("known",true).put("available",b!=null).put("enabled",b!=null&&b.isEnabled());}catch(SecurityException ignored){}
        return new JSONObject().put("storage",new JSONObject().put("total",files.getTotalSpace()).put("free",files.getUsableSpace()))
                .put("memory",new JSONObject().put("total",memory.totalMem).put("available",memory.availMem))
                .put("network",new JSONObject().put("connected",network!=null).put("wifi",network!=null&&network.hasTransport(1)).put("ethernet",network!=null&&network.hasTransport(3)))
                .put("bluetooth",bluetooth);
    }
    static JSONObject weather(Context c)throws Exception {return AuroraServices.weather(c);}
}
