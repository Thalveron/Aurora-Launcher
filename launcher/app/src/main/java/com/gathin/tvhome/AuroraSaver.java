package com.gathin.tvhome;
import android.content.Context;
import org.json.*;
/** Screensaver owns only its own player gain. No system volume write is performed. */
final class AuroraSaver {
    static volatile boolean active;private static long launching;private static boolean automatic;private static long automaticRemoteInput;
    static JSONObject preview(Context c)throws Exception{return preview(c,false);}
    private static void requireIdle(Context c)throws Exception{JSONObject a=AuroraSettings.get(c);long delay="continuous".equals(a.optString("saverMode"))?10000:a.optLong("saverMinutes",5)*60000;if(AuroraActivity.idleMillis()<delay)throw new IllegalArgumentException("刚有遥控操作，重新开始屏保计时");}
    static JSONObject preview(Context c,boolean auto)throws Exception{
        if(auto)requireIdle(c);automatic=auto;automaticRemoteInput=AuroraActivity.lastRemoteInput;
        if(AuroraData.media(c).optBoolean("playing"))throw new IllegalArgumentException("音乐软件正在播放，不进入屏保");
        if(!AuroraSettings.get(c).optBoolean("saverEnabled"))throw new IllegalArgumentException("请先启用并保存屏保");
        synchronized(AuroraSaver.class){if(active)return new JSONObject().put("active",true);long now=android.os.SystemClock.elapsedRealtime();if(now-launching>15000){launching=now;c.startActivity(new android.content.Intent(c,AuroraSaverActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION));}return new JSONObject().put("active",false).put("pending",true);}
    }
    static JSONObject enter(Context c)throws Exception{return AuroraBgm.onMain(c,()->{
        if(automatic&&AuroraActivity.lastRemoteInput>automaticRemoteInput)throw new IllegalArgumentException("刚有遥控操作，取消本次屏保");
        if(!AuroraActivity.visible)throw new IllegalArgumentException("桌面不在前台");
        if(AuroraData.media(c).optBoolean("playing"))throw new IllegalArgumentException("音乐软件正在播放，不进入屏保");
        if(!AuroraSettings.get(c).getBoolean("saverEnabled"))throw new IllegalArgumentException("屏保未启用");
        JSONObject state=AuroraBgm.state(c);
        if(state.optBoolean("enabled")&&!state.optString("selected").isEmpty()){
            AuroraBgm.command(c,"play",new JSONObject());state=AuroraBgm.state(c);
            if(state.optString("reason").equals("other_media"))return new JSONObject().put("active",false);
        }
        launching=0;active=true;AuroraActivity.nativeSaver(true);return new JSONObject().put("active",true);
    });}
    static void exit(Context c){launching=0;active=false;AuroraActivity.nativeSaver(false);AuroraBgm.silence(c);AuroraActivity.finishSaverHost();}
}
