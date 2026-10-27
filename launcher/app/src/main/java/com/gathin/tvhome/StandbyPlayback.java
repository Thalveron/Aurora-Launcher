package com.gathin.tvhome;
import android.content.*;import android.media.session.*;import java.util.regex.*;
/** Protect active external playback, never the launcher's own screensaver music/background video. */
final class StandbyPlayback {
 static boolean protectedNow(Context c)throws Exception{
  try{
   for(MediaController session:((MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE)).getActiveSessions(new ComponentName(c,MusicNotificationListener.class))){
    PlaybackState state=session.getPlaybackState();if(!c.getPackageName().equals(session.getPackageName())&&state!=null&&(state.getState()==PlaybackState.STATE_PLAYING||state.getState()==PlaybackState.STATE_BUFFERING||state.getState()==PlaybackState.STATE_CONNECTING))return true;
   }
  }catch(SecurityException noListener){/* The shell snapshot below remains available. */}
  // Many TV video players do not publish a MediaSession. AudioTrack still reports active playback,
  // even with volume muted. Ignore notification sounds and our own BGM/video UID.
  if(externalAudio(TvAdb.shell(c,"dumpsys audio"),android.os.Process.myUid()))return true;
  return videoFrames(c);
 }
 private static boolean videoFrames(Context c)throws Exception{
  String resources=TvAdb.shell(c,"dumpsys media.resource_manager");java.util.List<Integer> pids=videoPids(resources);
  if(pids.isEmpty())return false;
  String layers=null;
  for(int pid:pids){
   if(pid==android.os.Process.myPid())continue;
   String pkg=TvAdb.shell(c,"cat /proc/"+pid+"/cmdline").split("\\x00",2)[0].trim();
   if(!pkg.matches("[A-Za-z0-9_.]+")||pkg.equals(c.getPackageName()))continue;
   if(layers==null)layers=TvAdb.shell(c,"dumpsys SurfaceFlinger --list");
   int checked=0;
   for(String layer:layers.split("\\n")){
    if(!videoLayer(layer,pkg))continue;
    String quoted="'"+layer.replace("'","'\\''")+"'";
    String latency=TvAdb.shell(c,"dumpsys SurfaceFlinger --latency "+quoted);
    if(recentFrames(latency,android.os.SystemClock.uptimeMillis()))return true;
    if(++checked>=4)break;
   }
  }
  return false;
 }
 static boolean videoLayer(String layer,String pkg){
  // Main app windows can keep redrawing clocks/spinners while a video is paused.
  // On this TCL the dedicated video child is "package/activity#1"; other TVs use SurfaceView.
  return layer.length()<=512&&layer.contains(pkg+"/")&&!layer.startsWith("AppWindowToken")&&!layer.matches("^[0-9a-fA-F]+\\s+.*");
 }
 static java.util.List<Integer> videoPids(String dump){
  java.util.List<Integer> result=new java.util.ArrayList<>();int end=dump.indexOf("Events logs");if(end>=0)dump=dump.substring(0,end);
  Matcher m=Pattern.compile("(?ms)^\\s*Pid: (\\d+)\\s*$(.*?)(?=^\\s*Pid:|\\z)").matcher(dump);
  while(m.find()&&result.size()<4)if(m.group(2).contains("video-codec:"))result.add(Integer.parseInt(m.group(1)));return result;
 }
 static boolean recentFrames(String latency,long now){
  Matcher m=Pattern.compile("(?m)^\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$").matcher(latency);long newest=0;
  while(m.find())try{long time=Long.parseLong(m.group(2));if(time>0&&time<Long.MAX_VALUE)newest=Math.max(newest,time/1_000_000);}catch(NumberFormatException ignored){}
  return newest>0&&now>=newest&&now-newest<10_000;

 }
 static boolean externalAudio(String audio,int ownUid){
  int start=audio.indexOf("  players:"),end=audio.indexOf("  ducked players",Math.max(0,start));
  if(start<0)return false;String players=audio.substring(start,end>start?end:audio.length());
  Matcher m=Pattern.compile("(?m)^.*u/pid:(\\d+)/\\d+.*state:started.*usage=USAGE_(MEDIA|GAME|UNKNOWN)\\b.*$").matcher(players);
  while(m.find())if(Integer.parseInt(m.group(1))!=ownUid)return true;return false;
 }
}
