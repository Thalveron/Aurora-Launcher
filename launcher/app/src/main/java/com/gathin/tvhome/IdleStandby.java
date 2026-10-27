package com.gathin.tvhome;
import android.content.Context;import android.os.*;import android.view.*;import org.json.JSONObject;
/** Global remote inactivity, excluding active external playback. */
final class IdleStandby {
 private static final java.util.concurrent.ScheduledExecutorService worker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"aurora-idle-standby");t.setDaemon(true);return t;});
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static boolean started;private static volatile long enabledAt=SystemClock.uptimeMillis(),lastActivity=enabledAt,lastAttempt,lastPoll,warningDeadline;
 private static volatile String reason="尚未启用";private static volatile boolean playbackProtected;
 private static android.view.WindowManager overlayManager;
 private static volatile long previewUntil,cancelHomeUntil;
 private static Runnable warningTask;private static long armedDeadline;
 private static StandbyCountdownView overlay;private static int consumedKey=-1;
 static synchronized void start(Context c){if(started)return;started=true;Context app=c.getApplicationContext();worker.scheduleWithFixedDelay(()->poll(app),2,1,java.util.concurrent.TimeUnit.SECONDS);}
 static void desktopResumed(){enabledAt=lastActivity=SystemClock.uptimeMillis();dismiss();}
 static long idleMillis(){return Math.max(0,SystemClock.uptimeMillis()-Math.max(enabledAt,lastActivity));}
 static boolean enabled(Context c){return c.getSharedPreferences("idle_standby",0).getBoolean("enabled",false);}
 static int minutes(Context c){int m=c.getSharedPreferences("idle_standby",0).getInt("minutes",15);return m==20||m==30?m:15;}
 static JSONObject state(Context c)throws Exception{
  boolean on=enabled(c);long delay=StandbyTiming.delay(minutes(c)),idle=Math.max(0,SystemClock.uptimeMillis()-Math.max(enabledAt,lastActivity));
  return new JSONObject().put("enabled",on).put("minutes",minutes(c)).put("available",(TvAdb.status().optBoolean("connected")&&HomeKeyService.current!=null)).put("idleSeconds",on?idle/1000:0).put("remainingSeconds",on?Math.max(0,(delay-idle+999)/1000):delay/1000).put("countdown",warningDeadline>0).put("warningSeconds",60).put("reason",on?reason:"已关闭").put("scope","global_except_playback").put("paused",on&&playbackProtected).put("wake","power_key");
 }
 static JSONObject save(Context c,JSONObject body)throws Exception{
  AuroraSettings.only(body,"enabled","minutes");
  if(body.has("enabled")&&!(body.get("enabled") instanceof Boolean))throw new IllegalArgumentException("enabled 必须为布尔值");
  int selected=minutes(c);if(body.has("minutes")){Object value=body.get("minutes");if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue())throw new IllegalArgumentException("分钟数必须是整数");selected=StandbyTiming.minutes(((Number)value).intValue());}
  boolean on=body.optBoolean("enabled",enabled(c));if(on&&!(TvAdb.status().optBoolean("connected")&&HomeKeyService.current!=null))throw new IllegalArgumentException("请先连接电视调试并启用桌面主页辅助服务，才能全局监测按键并显示倒计时");
  if(!c.getSharedPreferences("idle_standby",0).edit().putBoolean("enabled",on).putInt("minutes",selected).commit())throw new IllegalStateException("设置保存失败");
  desktopResumed();lastAttempt=lastPoll=0;reason=on?"计时中":"已关闭";start(c);return state(c);
 }
 static boolean key(Context c,KeyEvent event){
  if(event.getKeyCode()==consumedKey){if(event.getAction()==KeyEvent.ACTION_UP)consumedKey=-1;return true;}
  if(event.getAction()!=KeyEvent.ACTION_DOWN||event.getKeyCode()==KeyEvent.KEYCODE_UNKNOWN)return false;
  boolean cancel=warningDeadline>0;enabledAt=lastActivity=SystemClock.uptimeMillis();
  if(cancel){if(event.getKeyCode()==KeyEvent.KEYCODE_HOME||event.getKeyCode()==KeyEvent.KEYCODE_ALL_APPS)cancelHomeUntil=SystemClock.uptimeMillis()+1000;consumedKey=event.getKeyCode();dismiss();reason="已取消待机，重新计时";WebAudit.record(c,"127.0.0.1","遥控器取消自动待机","",200);return true;}return false;
 }
 static boolean home(Context c){if(SystemClock.uptimeMillis()<cancelHomeUntil)return true;if(warningDeadline==0)return false;cancelHomeUntil=SystemClock.uptimeMillis()+1000;desktopResumed();reason="已取消待机，重新计时";WebAudit.record(c,"127.0.0.1","主页键取消自动待机","",200);return true;}
 private static void dismiss(){warningDeadline=previewUntil=0;main.post(()->{if(warningTask!=null)main.removeCallbacks(warningTask);warningTask=null;armedDeadline=0;if(overlay!=null){try{overlayManager.removeViewImmediate(overlay);}catch(RuntimeException ignored){}overlay=null;overlayManager=null;}});}
 private static void arm(Context c,long deadline){main.post(()->{
  if(armedDeadline==deadline)return;if(warningTask!=null)main.removeCallbacks(warningTask);
  armedDeadline=deadline;warningTask=()->worker.execute(()->{lastPoll=0;poll(c);});
  main.postDelayed(warningTask,Math.max(1,deadline-SystemClock.uptimeMillis()-60_000));
 });}
 private static void show(long deadline){warningDeadline=deadline;main.post(()->{
  if(warningDeadline!=deadline)return;HomeKeyService service=HomeKeyService.current;if(service==null){warningDeadline=0;return;}
  if(overlay==null){
   overlay=new StandbyCountdownView(service);overlayManager=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
   WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);
   params.setTitle("Aurora 待机倒计时");
   try{overlayManager.addView(overlay,params);}catch(RuntimeException unavailable){overlay=null;overlayManager=null;warningDeadline=0;reason="无法显示倒计时，已暂停待机";enabledAt=SystemClock.uptimeMillis();return;}
  }
  overlay.deadline(deadline);
 });}
 static JSONObject preview(Context c)throws Exception{
  if(HomeKeyService.current==null)throw new IllegalArgumentException("请先启用桌面主页辅助服务");
  previewUntil=SystemClock.uptimeMillis()+60_000;show(previewUntil);
  WebAudit.record(c,"127.0.0.1","预览待机倒计时","仅预览，不待机",200);return new JSONObject().put("preview",true).put("seconds",60);
 }
 static long activity(String power){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^\\s*mLastUserActivityTime(?:NoChangeLights)?=(\\d+)").matcher(power);long result=-1;while(m.find())result=Math.max(result,Long.parseLong(m.group(1)));return result;}
 private static void poll(Context c){
  if(previewUntil>0){if(SystemClock.uptimeMillis()>=previewUntil){dismiss();desktopResumed();}return;}
  if(!enabled(c)){if(warningDeadline>0)dismiss();return;}
  long now=SystemClock.uptimeMillis();if(now-lastPoll<(warningDeadline>0?1000:5000))return;lastPoll=now;
  try{
   if(!(TvAdb.status().optBoolean("connected")&&HomeKeyService.current!=null)){dismiss();enabledAt=now;reason="电视调试未连接，暂停自动待机";return;}
   String power=TvAdb.shell(c,"dumpsys power");if(!power.contains("mWakefulness=Awake")){dismiss();enabledAt=now;reason="电视已在待机";return;}
   long systemActivity=activity(power);if(systemActivity<0)throw new IllegalStateException("无法读取系统操作时间");
   lastActivity=Math.max(lastActivity,systemActivity);
   playbackProtected=StandbyPlayback.protectedNow(c);
   if(playbackProtected){enabledAt=SystemClock.uptimeMillis();dismiss();reason="正在播放音视频，暂停计时";return;}
   long deadline=Math.max(enabledAt,lastActivity)+StandbyTiming.delay(minutes(c));now=SystemClock.uptimeMillis();
   if(!StandbyTiming.warning(deadline,now)){if(warningDeadline>0)dismiss();arm(c,deadline);reason="计时中";return;}
   if(warningDeadline==0){show(deadline);WebAudit.record(c,"127.0.0.1","自动待机倒计时",minutes(c)+"分钟 · 最后60秒",200);}
   else if(warningDeadline!=deadline){dismiss();reason="已取消待机，重新计时";return;}
   reason="一分钟待机倒计时";
   if(now<deadline||now-lastAttempt<60000)return;
   String fresh=TvAdb.shell(c,"dumpsys power");long latest=activity(fresh);
   if(latest<0||!fresh.contains("mWakefulness=Awake")){dismiss();return;}
   lastActivity=Math.max(lastActivity,latest);
   if(!enabled(c)||HomeKeyService.current==null||StandbyPlayback.protectedNow(c)||warningDeadline!=deadline||SystemClock.uptimeMillis()-Math.max(enabledAt,lastActivity)<StandbyTiming.delay(minutes(c)))return;
   lastAttempt=now;SystemControl backend=AuroraHttp.controls(c).select("standby");
   if(warningDeadline!=deadline)return;
   WebAudit.record(c,"127.0.0.1",minutes(c)+"分钟无操作自动待机",backend.name(),200);reason="已发送待机指令";backend.powerStandby();desktopResumed();
  }catch(Exception e){dismiss();enabledAt=SystemClock.uptimeMillis();String next="自动待机暂停："+String.valueOf(e.getMessage());if(!next.equals(reason))WebAudit.record(c,"127.0.0.1","自动待机失败",String.valueOf(e.getMessage()),503);reason=next;}
 }
}
