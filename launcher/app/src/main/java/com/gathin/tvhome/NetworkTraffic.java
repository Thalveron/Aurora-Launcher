package com.gathin.tvhome;
import android.content.*;import android.content.pm.*;import android.database.*;import android.database.sqlite.*;import android.os.SystemClock;import org.json.*;import java.util.*;
/** One background sampler serves all console viewers; no packet contents or firewall changes. */
final class NetworkTraffic {
 private static final java.util.concurrent.ScheduledExecutorService worker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"aurora-network-monitor");t.setDaemon(true);return t;});
 private static boolean started;private static Context app;private static volatile JSONObject latest=new JSONObject();
 private static JSONObject previous;private static String boot="";private static volatile String error="";private static volatile long sampled;private static long lastFlush;private static volatile int failures;
 private static final ArrayDeque<JSONObject> chart=new ArrayDeque<>();private static final HashMap<Integer,long[]> sessions=new HashMap<>();
 private static final HashMap<String,long[]> pending=new HashMap<>();private static Map<Integer,JSONArray> labels=new TreeMap<>();private static long labelTime;
 private static final java.util.concurrent.ExecutorService dnsWorker=java.util.concurrent.Executors.newFixedThreadPool(2);
 private static final java.util.concurrent.ConcurrentHashMap<String,String> dnsCache=new java.util.concurrent.ConcurrentHashMap<>();
 private static final java.util.Set<String> dnsPending=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String,Boolean>());
 private static final HashMap<String,long[]> seenLinks=new HashMap<>();
 private static long cleanedAt;
 private static DB database;private static int probePort;private static String probeToken;private static long probeLaunchingAt;
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("network_monitor",0);}
 static synchronized void start(Context c){if(started)return;started=true;app=c.getApplicationContext();worker.scheduleWithFixedDelay(()->{try{if(prefs(app).getBoolean("enabled",false)&&SystemClock.elapsedRealtime()-sampled>=prefs(app).getInt("interval",5)*1000L)sample(app);}catch(Exception e){error=String.valueOf(e.getMessage());}},0,1,java.util.concurrent.TimeUnit.SECONDS);}
 static JSONObject state(Context c)throws Exception{start(c);JSONObject snapshot=latest;JSONObject result=new JSONObject(snapshot.toString());result.put("enabled",prefs(c).getBoolean("enabled",false)).put("intervalSeconds",prefs(c).getInt("interval",5)).put("historyEnabled",prefs(c).getBoolean("history",true)).put("available",snapshot.has("sampleAt")||TvAdb.status().optBoolean("connected")).put("error",error).put("sampleAgeMs",sampled==0?-1:SystemClock.elapsedRealtime()-sampled).put("hasSample",snapshot.has("sampleAt")).put("retentionDays",prefs(c).getInt("retention",7)).put("resolveDomains",prefs(c).getBoolean("dns",false)).put("lastAutoCleanupAt",prefs(c).getLong("lastCleanup",0)).put("failures",failures);return result;}
 static JSONObject configure(Context c,JSONObject b)throws Exception{
  AuroraSettings.only(b,"enabled","intervalSeconds","historyEnabled","retentionDays","resolveDomains");SharedPreferences.Editor edit=prefs(c).edit();
  if(b.has("enabled")){if(!(b.get("enabled")instanceof Boolean))throw new IllegalArgumentException("enabled 必须为布尔值");edit.putBoolean("enabled",b.getBoolean("enabled"));}
  if(b.has("intervalSeconds")){int n=b.getInt("intervalSeconds");if(!Arrays.asList(2,5,10,30).contains(n))throw new IllegalArgumentException("采样间隔仅支持2、5、10、30秒");edit.putInt("interval",n);}
  if(b.has("historyEnabled")){if(!(b.get("historyEnabled")instanceof Boolean))throw new IllegalArgumentException("historyEnabled 必须为布尔值");edit.putBoolean("history",b.getBoolean("historyEnabled"));}
  if(b.has("retentionDays")){int n=b.getInt("retentionDays");if(!Arrays.asList(1,3,7,30).contains(n))throw new IllegalArgumentException("保留天数支持1、3、7、30天");edit.putInt("retention",n);}
  if(b.has("resolveDomains")){if(!(b.get("resolveDomains") instanceof Boolean))throw new IllegalArgumentException("resolveDomains 必须为布尔值");edit.putBoolean("dns",b.getBoolean("resolveDomains"));}
  edit.commit();start(c);worker.execute(()->{previous=null;sampled=0;try{flush(c);if(!prefs(c).getBoolean("enabled",false)&&probePort!=0){try{probeRequest("STOP");}catch(Exception ignored){}probePort=0;}}catch(Exception ignored){}});return state(c);
 }
 static void clear(Context c,JSONObject b)throws Exception{AuroraSettings.only(b,"confirm");if(!Boolean.TRUE.equals(b.opt("confirm")))throw new IllegalArgumentException("请确认清空监控历史");worker.execute(()->{try{db(c).delete("history",null,null);pending.clear();sessions.clear();chart.clear();seenLinks.clear();dnsCache.clear();previous=null;latest=new JSONObject();sampled=0;}catch(Exception e){error=e.toString();}});}
 private static synchronized SQLiteDatabase db(Context c){if(database==null)database=new DB(c);return database.getWritableDatabase();}
 private static final class DB extends SQLiteOpenHelper{DB(Context c){super(c,"network-monitor.db",null,1);}public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE history(t INTEGER,uid INTEGER,rx INTEGER,tx INTEGER,PRIMARY KEY(t,uid))");}public void onUpgrade(SQLiteDatabase d,int a,int b){}}
 static JSONObject history(Context c,int uid,int hours)throws Exception{
  if(hours!=1&&hours!=24&&hours!=168&&hours!=720)throw new IllegalArgumentException("历史范围仅支持1、24、168、720小时");
  long bucket=hours==1?60000:hours==24?300000:1800000;JSONArray points=new JSONArray();long from=System.currentTimeMillis()-hours*3600000L;
  synchronized(NetworkTraffic.class){try(Cursor cur=db(c).rawQuery("SELECT (t / ?)*?,SUM(rx),SUM(tx) FROM history WHERE uid=? AND t>=? GROUP BY (t / ?) ORDER BY 1",new String[]{""+bucket,""+bucket,""+uid,""+from,""+bucket})){while(cur.moveToNext())points.put(new JSONObject().put("time",cur.getLong(0)).put("rxBytes",cur.getLong(1)).put("txBytes",cur.getLong(2)).put("seconds",bucket/1000));}}
  return new JSONObject().put("uid",uid).put("hours",hours).put("bucketSeconds",bucket/1000).put("points",points).put("note","历史从开启采集后记录，不是安装前的用量；仅含非回环网卡，断电/待机间隔不插值。");
 }
 private static void labels(Context c)throws Exception{
  if(SystemClock.elapsedRealtime()-labelTime<60000&&!labels.isEmpty())return;
  Map<Integer,JSONArray> next=new TreeMap<>();PackageManager pm=c.getPackageManager();
  for(ApplicationInfo a:pm.getInstalledApplications(0)){JSONArray list=next.get(a.uid);if(list==null){list=new JSONArray();next.put(a.uid,list);}list.put(new JSONObject().put("package",a.packageName).put("name",String.valueOf(pm.getApplicationLabel(a))).put("system",(a.flags&ApplicationInfo.FLAG_SYSTEM)!=0).put("enabled",a.enabled));}
  labels=next;labelTime=SystemClock.elapsedRealtime();
 }
 private static Map<Integer,long[]> counters(JSONObject raw)throws Exception{
  Map<Integer,long[]> map=new TreeMap<>();JSONArray rows=raw.getJSONArray("uidRows");
  for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(r.optInt("tag")!=0||"lo".equals(r.optString("iface")))continue;int uid=r.getInt("uid");long[] a=map.get(uid);if(a==null){a=new long[4];map.put(uid,a);}a[0]+=r.optLong("rxBytes");a[1]+=r.optLong("txBytes");a[2]+=r.optLong("rxPackets");a[3]+=r.optLong("txPackets");}return map;
 }
 private static Map<String,long[]> interfaces(JSONObject raw)throws Exception{Map<String,long[]> result=new TreeMap<>();JSONArray list=raw.getJSONArray("interfaces");for(int i=0;i<list.length();i++){JSONObject n=list.getJSONObject(i);if("lo".equals(n.getString("name")))continue;result.put(n.getString("name"),new long[]{n.getLong("rxBytes"),n.getLong("txBytes")});}return result;}
 private static long delta(long now,long old){return now>=old?now-old:0;}
 private static void accumulate(Context c,long time,int uid,long rx,long tx){
  if(!prefs(c).getBoolean("history",true)||rx+tx<=0)return;long minute=time/60000*60000;String key=minute+":"+uid;long[] p=pending.get(key);if(p==null){p=new long[]{minute,uid,0,0};pending.put(key,p);}p[2]+=rx;p[3]+=tx;
 }
 private static synchronized void flush(Context c)throws Exception{
  if(pending.isEmpty())return;SQLiteDatabase d=db(c);d.beginTransaction();try{for(long[] a:pending.values()){d.execSQL("INSERT OR IGNORE INTO history(t,uid,rx,tx) VALUES(?,?,0,0)",new Object[]{a[0],a[1]});d.execSQL("UPDATE history SET rx=rx+?,tx=tx+? WHERE t=? AND uid=?",new Object[]{a[2],a[3],a[0],a[1]});}d.delete("history","t<?",new String[]{""+(System.currentTimeMillis()-prefs(c).getInt("retention",7)*86400000L)});d.setTransactionSuccessful();pending.clear();lastFlush=SystemClock.elapsedRealtime();}finally{d.endTransaction();}
 }
 private static String probeRequest(String command)throws Exception{
  try(java.net.Socket socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress("127.0.0.1",probePort),1000);socket.setSoTimeout(8000);socket.getOutputStream().write((probeToken+" "+command+String.valueOf((char)10)).getBytes("UTF-8"));socket.getOutputStream().flush();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=socket.getInputStream().read(buffer))!=-1){if(out.size()+n>2*1024*1024)throw new java.io.IOException("流量快照过大");out.write(buffer,0,n);}return out.toString("UTF-8");}
 }
 private static JSONObject probe(Context c)throws Exception{
  StringBuilder uidList=new StringBuilder();int listed=0;for(int uid:labels.keySet()){if(listed++>=2000)break;if(uidList.length()>0)uidList.append(',');uidList.append(uid);}String sampleCommand="SAMPLE "+uidList;
  if(probePort!=0)try{JSONObject snapshot=new JSONObject(probeRequest(sampleCommand));probeLaunchingAt=0;return snapshot;}catch(Exception unavailable){
   if(probeLaunchingAt>0&&android.os.SystemClock.elapsedRealtime()-probeLaunchingAt<90000)throw new java.io.IOException("采集进程正在启动，稍后自动重试");
   probePort=0;
  }
  try(java.net.ServerSocket reserve=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){probePort=reserve.getLocalPort();}
  byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);StringBuilder hex=new StringBuilder();for(byte b:key)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));probeToken=hex.toString();
  String apk=c.getApplicationInfo().sourceDir;String cmd="CLASSPATH='"+apk.replace("'","'\\''")+"' $(if [ -x /system/bin/setsid ]; then echo /system/bin/setsid; fi) /system/bin/app_process /system/bin com.gathin.tvhome.NetworkTrafficProbe --serve "+probePort+" "+probeToken+" >/sdcard/Download/aurora-network-probe.log 2>&1 </dev/null & sleep 1";
  // A raw exec stream has no terminal lifecycle to terminate the detached sampler.
  try{TvAdb.run(c,"exec:"+cmd,65536);}catch(Exception failure){probePort=0;throw failure;}
  probeLaunchingAt=android.os.SystemClock.elapsedRealtime();
  for(int i=0;i<48;i++){try{JSONObject snapshot=new JSONObject(probeRequest(sampleCommand));probeLaunchingAt=0;return snapshot;}catch(Exception e){Thread.sleep(250);}}
  // Cold ART startup may exceed the first wait. Keep its port/token and retry the
  // same child instead of spawning another VM and abandoning the previous one.
  throw new java.io.IOException("采集进程正在启动，稍后自动重试");
 }
 private static final String sdkBoot="sdk-"+(System.currentTimeMillis()-android.os.SystemClock.elapsedRealtime());
 private static JSONObject publicSnapshot(Context c,String diagnostic)throws Exception{
  long rx=android.net.TrafficStats.getTotalRxBytes(),tx=android.net.TrafficStats.getTotalTxBytes(),ownRx=android.net.TrafficStats.getUidRxBytes(c.getApplicationInfo().uid),ownTx=android.net.TrafficStats.getUidTxBytes(c.getApplicationInfo().uid);
  JSONArray interfaces=new JSONArray(),rows=new JSONArray();if(rx>=0&&tx>=0)interfaces.put(new JSONObject().put("name","系统合计").put("rxBytes",rx).put("txBytes",tx));if(ownRx>=0&&ownTx>=0)rows.put(new JSONObject().put("iface","all").put("uid",c.getApplicationInfo().uid).put("tag",0).put("set",0).put("rxBytes",ownRx).put("txBytes",ownTx));
  if(interfaces.length()==0&&rows.length()==0)throw new IllegalStateException("系统未提供网络计数："+diagnostic);
  return new JSONObject().put("timestamp",System.currentTimeMillis()).put("uptimeMs",SystemClock.elapsedRealtime()).put("bootId",sdkBoot).put("source","Android SDK 基础采集").put("interfaces",interfaces).put("uidRows",rows).put("connections",new JSONArray()).put("uidAvailable",rows.length()>0).put("uidComplete",false).put("packetsAvailable",false).put("connectionsAvailable",false).put("diagnostics",new JSONArray().put(diagnostic)).put("compatibilityNote","基础模式仅提供系统总量及 Aurora 自身流量，其他应用及连接IP显示为不可用。完整统计需要本机ADB及系统支持。原因："+diagnostic);
 }
 private static void enrich(Context c,JSONArray links,long at)throws Exception{
  HashSet<String> present=new HashSet<>();
  for(int i=0;i<links.length();i++){JSONObject link=links.getJSONObject(i);String ip=link.optString("remote");
   String key=link.optInt("uid")+"|"+link.optString("protocol")+"|"+link.optString("local")+":"+link.optInt("localPort")+"|"+ip+":"+link.optInt("remotePort");
   present.add(key);long[] times=seenLinks.get(key);if(times==null){times=new long[]{at,at};seenLinks.put(key,times);}times[1]=at;
   link.put("firstSeenAt",times[0]).put("lastSeenAt",at).put("observedSeconds",Math.max(0,(at-times[0])/1000));
   if(!"public".equals(link.optString("scope")))continue;
   String host=dnsCache.get(ip);
   if(prefs(c).getBoolean("dns",false)){
    link.put("reverseName",host==null?"":host).put("domainSource","PTR reverse DNS; not original requested hostname");
    if(host==null&&dnsPending.size()<32&&dnsPending.add(ip)){dnsWorker.execute(()->{try{String found=java.net.InetAddress.getByName(ip).getCanonicalHostName();if(dnsCache.size()>512)dnsCache.clear();dnsCache.put(ip,found.equals(ip)?"":found);}catch(Exception ignored){dnsCache.put(ip,"");}finally{dnsPending.remove(ip);}});}
   }else link.put("reverseName","").put("domainSource","disabled");
  }
  seenLinks.entrySet().removeIf(e->!present.contains(e.getKey()));
  if(at-cleanedAt>=3600000L){db(c).delete("history","t<?",new String[]{""+(at-prefs(c).getInt("retention",7)*86400000L)});cleanedAt=at;prefs(c).edit().putLong("lastCleanup",at).apply();}
 }
 private static void sample(Context c){
  long begin=SystemClock.elapsedRealtime();
  try{
   labels(c);JSONObject raw;if(!TvAdb.status().optBoolean("connected"))raw=publicSnapshot(c,"本机调试未连接");else try{raw=probe(c);if(raw.has("error"))raw=publicSnapshot(c,raw.getString("error"));}catch(Exception failed){raw=publicSnapshot(c,String.valueOf(failed.getMessage()));}
   String id=raw.getString("bootId");if(!id.equals(boot)||(previous!=null&&!raw.getString("source").equals(previous.optString("source")))){boot=id;previous=null;sessions.clear();chart.clear();}
   double seconds=previous==null?0:(raw.getLong("uptimeMs")-previous.getLong("uptimeMs"))/1000d;
   boolean valid=seconds>0&&seconds<=Math.max(90,prefs(c).getInt("interval",5)*4);
   Map<Integer,long[]> now=counters(raw),before=previous==null?new TreeMap<>():counters(previous);
   TreeSet<Integer> uids=new TreeSet<>(labels.keySet());uids.addAll(now.keySet());
   JSONArray apps=new JSONArray(),links=raw.getJSONArray("connections");enrich(c,links,raw.getLong("timestamp"));
   Map<Integer,Integer> publicLinks=new HashMap<>(),allLinks=new HashMap<>();Map<Integer,Set<String>> publicAddresses=new HashMap<>();
   for(int i=0;i<links.length();i++){JSONObject link=links.getJSONObject(i);int uid=link.getInt("uid");allLinks.put(uid,allLinks.getOrDefault(uid,0)+1);if("public".equals(link.optString("scope"))&&!Arrays.asList("LISTEN","TIME_WAIT","CLOSED").contains(link.optString("state"))){publicLinks.put(uid,publicLinks.getOrDefault(uid,0)+1);Set<String> addresses=publicAddresses.get(uid);if(addresses==null){addresses=new HashSet<>();publicAddresses.put(uid,addresses);}addresses.add(link.optString("remote"));}}
   long at=raw.getLong("timestamp");
   for(int uid:uids){
    long[] n=now.getOrDefault(uid,new long[4]),old=before.get(uid),session=sessions.get(uid);if(session==null){session=new long[6];sessions.put(uid,session);}
    boolean countersAvailable=raw.optBoolean("uidAvailable",true)&&(raw.optBoolean("uidComplete",true)||now.containsKey(uid));long rx=valid&&countersAvailable&&old!=null?delta(n[0],old[0]):0,tx=valid&&countersAvailable&&old!=null?delta(n[1],old[1]):0;session[0]+=rx;session[1]+=tx;
    double rr=valid?rx/seconds:0,tr=valid?tx/seconds:0;session[2]=Math.max(session[2],Math.round(rr));session[3]=Math.max(session[3],Math.round(tr));
    session[4]=valid&&tr>=32768?session[4]+Math.round(seconds):0;if(rx+tx>0)session[5]=at;
    JSONArray packages=labels.get(uid);if(packages==null)packages=new JSONArray();
    String name=packages.length()>0?packages.getJSONObject(0).getString("name"):uid==0?"root / ADB":"系统 UID "+uid;
    if(packages.length()>1)name+=" 等 "+packages.length()+" 个组件";
    JSONObject item=new JSONObject().put("uid",uid).put("name",name).put("packages",packages).put("sharedUid",packages.length()>1).put("rxBytes",countersAvailable?n[0]:JSONObject.NULL).put("txBytes",countersAvailable?n[1]:JSONObject.NULL).put("rxPackets",countersAvailable&&raw.optBoolean("packetsAvailable",true)?n[2]:JSONObject.NULL).put("txPackets",countersAvailable&&raw.optBoolean("packetsAvailable",true)?n[3]:JSONObject.NULL).put("rxRate",valid&&countersAvailable?rr:JSONObject.NULL).put("txRate",valid&&countersAvailable?tr:JSONObject.NULL).put("sessionRx",countersAvailable?session[0]:JSONObject.NULL).put("sessionTx",countersAvailable?session[1]:JSONObject.NULL).put("peakRx",countersAvailable?session[2]:JSONObject.NULL).put("peakTx",countersAvailable?session[3]:JSONObject.NULL).put("lastActiveAt",session[5]).put("connections",allLinks.getOrDefault(uid,0)).put("publicConnections",publicLinks.getOrDefault(uid,0)).put("sustainedUploadSeconds",session[4]).put("uploadHint",session[4]>=60&&publicLinks.getOrDefault(uid,0)>0?"持续上传，建议核对用途；不是PCDN定论":"");
    Set<String> publicIps=publicAddresses.get(uid);item.put("publicIpCount",publicIps==null?0:publicIps.size());
    if(uid==c.getApplicationInfo().uid&&session[4]>=60)item.put("uploadHint","含网页控制台/预览传输，请结合连接地址核对");
    apps.put(item);accumulate(c,at,uid,rx,tx);
   }
   Map<String,long[]> ints=interfaces(raw),oldInts=previous==null?new TreeMap<>():interfaces(previous);JSONArray nets=new JSONArray();long totalRx=0,totalTx=0,deltaRx=0,deltaTx=0;
   for(String iface:ints.keySet()){long[] n=ints.get(iface),old=oldInts.get(iface);long rx=valid&&old!=null?delta(n[0],old[0]):0,tx=valid&&old!=null?delta(n[1],old[1]):0;totalRx+=n[0];totalTx+=n[1];deltaRx+=rx;deltaTx+=tx;nets.put(new JSONObject().put("name",iface).put("rxBytes",n[0]).put("txBytes",n[1]).put("rxRate",valid?rx/seconds:JSONObject.NULL).put("txRate",valid?tx/seconds:JSONObject.NULL));}
   JSONObject total=new JSONObject().put("rxBytes",ints.isEmpty()?JSONObject.NULL:totalRx).put("txBytes",ints.isEmpty()?JSONObject.NULL:totalTx).put("rxRate",valid&&!ints.isEmpty()?deltaRx/seconds:JSONObject.NULL).put("txRate",valid&&!ints.isEmpty()?deltaTx/seconds:JSONObject.NULL);
   if(valid&&!ints.isEmpty()){chart.add(new JSONObject().put("time",at).put("rxRate",deltaRx/seconds).put("txRate",deltaTx/seconds));while(chart.size()>360)chart.removeFirst();accumulate(c,at,-1,deltaRx,deltaTx);}
   if(SystemClock.elapsedRealtime()-lastFlush>=60000)flush(c);
   latest=new JSONObject().put("sampleAt",at).put("sampleDurationMs",SystemClock.elapsedRealtime()-begin).put("sampleSeconds",seconds).put("rateReady",valid).put("source",raw.getString("source")).put("compatibilityNote",raw.optString("compatibilityNote")).put("diagnostics",raw.optJSONArray("diagnostics")).put("uidAvailable",raw.optBoolean("uidAvailable",true)).put("connectionsAvailable",raw.optBoolean("connectionsAvailable",true)).put("bootId",boot).put("total",total).put("interfaces",nets).put("apps",apps).put("connections",links).put("series",new JSONArray(chart)).put("note","速率由相邻采样差值计算；本次开机累计可能在系统网络服务重置时归零。可识别时排除 lo 回环，兼容接口的统计口径由系统决定；网卡总量与UID合计可能因统计口径不同而不相等。公网连接也不能证明PCDN。");
   previous=raw;error="";failures=0;
  }catch(Exception e){error=String.valueOf(e.getMessage());failures++;}
  finally{sampled=SystemClock.elapsedRealtime();}
 }
}
