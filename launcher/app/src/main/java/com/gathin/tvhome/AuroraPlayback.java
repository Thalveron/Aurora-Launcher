package com.gathin.tvhome;
import android.content.*;import android.os.*;import android.support.v4.media.MediaBrowserCompat;
/** Connect the existing music service before asking it to restore its saved queue. */
final class AuroraPlayback {
 private static MediaBrowserCompat warmBrowser;
 static void warm(Context context){Context c=context.getApplicationContext();if(!installed(c))return;new Handler(Looper.getMainLooper()).post(()->{
  if(warmBrowser!=null)return;
  warmBrowser=new MediaBrowserCompat(c,new ComponentName("com.craftworks.music.tvcustom","com.craftworks.music.player.ChoraMediaLibraryService"),new MediaBrowserCompat.ConnectionCallback(){
   @Override public void onConnectionFailed(){cool();}
   @Override public void onConnectionSuspended(){cool();}
  },null);
  try{warmBrowser.connect();}catch(Exception e){cool();}
 });}
 static android.media.session.MediaController controller(Context c){try{if(warmBrowser!=null&&warmBrowser.isConnected())return new android.media.session.MediaController(c,(android.media.session.MediaSession.Token)warmBrowser.getSessionToken().getToken());}catch(Exception ignored){}return null;}
 static void cool(){new Handler(Looper.getMainLooper()).post(()->{if(warmBrowser!=null){warmBrowser.disconnect();warmBrowser=null;}});}
 private static MediaBrowserCompat browser;private static long started,sequence;private static String failure="";
 static boolean installed(Context c){try{c.getPackageManager().getApplicationInfo("com.craftworks.music.tvcustom",0);return true;}catch(Exception e){return false;}}
 static synchronized boolean pending(){return browser!=null&&SystemClock.elapsedRealtime()-started<12000;}
 static synchronized String failure(){return failure;}
 static synchronized void start(Context context){if(pending())return;Context c=context.getApplicationContext();started=SystemClock.elapsedRealtime();final long request=++sequence;failure="";Handler h=new Handler(Looper.getMainLooper());h.post(()->{
  synchronized(AuroraPlayback.class){if(browser!=null)browser.disconnect();browser=new MediaBrowserCompat(c,new ComponentName("com.craftworks.music.tvcustom","com.craftworks.music.player.ChoraMediaLibraryService"),new MediaBrowserCompat.ConnectionCallback(){
   @Override public void onConnected(){c.sendBroadcast(new Intent("com.craftworks.music.tvcustom.DESKTOP_PLAY").setPackage("com.craftworks.music.tvcustom"));h.postDelayed(()->{if(sequence==request)finish("");},1500);}
   @Override public void onConnectionFailed(){finish("无法连接 Aurora 音乐，请打开播放器检查本地歌曲");}
   @Override public void onConnectionSuspended(){finish("播放器连接已中断");}
  },null);try{browser.connect();}catch(Exception e){finish("播放器启动失败");}}
 });h.postDelayed(()->{if(sequence==request&&pending())finish("播放器连接超时，请打开 Aurora 音乐检查");},10000);}
 private static synchronized void finish(String error){if(browser!=null){browser.disconnect();browser=null;}failure=error;}
}
