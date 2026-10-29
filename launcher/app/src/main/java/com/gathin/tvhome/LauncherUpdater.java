package com.gathin.tvhome;
import android.content.*;import android.content.pm.*;import android.net.Uri;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.security.MessageDigest;import org.json.*;
/** One updater owns TV/Web controls, persistent policy, verified downloads and recovery bookkeeping. */
final class LauncherUpdater {
 private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"aurora-updater");t.setDaemon(true);return t;});
 private static volatile JSONObject preparedRelease;
 private static JSONObject saved;private static boolean started,busy;private static volatile String message="尚未检查更新",phase="idle",capabilityReason="正在检查维护权限";
 private static volatile boolean maintenance,useRunAs;private static volatile int percent;private static volatile JSONArray lines=new JSONArray();
 private static File stateFile(Context c){return new File(c.getFilesDir(),"updater-state.json");}
 static synchronized JSONObject load(Context c)throws Exception{if(saved==null){try{saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(stateFile(c).toPath()),"UTF-8"));}catch(Exception absent){saved=new JSONObject().put("autoCheck",true).put("automatic",false);}}return saved;}
 private static synchronized void persist(Context c)throws Exception{File target=stateFile(c),tmp=new File(target.getPath()+".next");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(load(c).toString().getBytes("UTF-8"));out.getFD().sync();}if(!tmp.renameTo(target))throw new IOException("更新设置保存失败");}
 static synchronized long code(Context c)throws Exception{return UpdatePackages.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0));}
 static String version(Context c)throws Exception{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}
 static synchronized void start(Context c){
  if(started)return;started=true;Context app=c.getApplicationContext();
  worker.scheduleWithFixedDelay(()->{try{if(preparedRelease!=null)return;refreshResult(app);probe(app);JSONObject config=load(app);JSONObject uploaded=config.optJSONObject("uploaded");if(uploaded!=null&&uploaded.optLong("expiresAt")<System.currentTimeMillis()){config.remove("uploaded");persist(app);}if(!isBusy(app)&&config.optBoolean("autoCheck",true)&&System.currentTimeMillis()-config.optLong("lastAttempt")>12*3600000L)checkNow(app);
   if(!isBusy(app)&&!config.has("pendingInstaller")&&!config.has("uploaded")&&config.optBoolean("automatic")&&maintenance&&config.has("latest")&&config.getJSONObject("latest").optLong("versionCode")>code(app)&&config.getJSONObject("latest").optLong("versionCode")!=config.optLong("blockedVersionCode")&&(MainActivity.isHomeVisible()||AuroraActivity.visible)&&IdleStandby.idleMillis()>120000&&!StandbyPlayback.protectedNow(app)){busy=true;try{installNow(app,"update",true);}finally{busy=false;}}
  }catch(Exception error){message="检查更新失败："+String.valueOf(error.getMessage());}},15,60,TimeUnit.SECONDS);
 }
 private static void probe(Context c){
  try{if(!TvAdb.status().optBoolean("connected"))throw new IOException("本机 ADB 未连接；可检查更新和使用系统确认安装，自动更新/回退不可用");
   String uid=maintenanceShell(c,"id -u");boolean root=uid.matches("(?s).*\\b0\\s*$"),debug=(c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
   useRunAs=debug&&maintenanceShell(c,"run-as "+c.getPackageName()+" id -u").trim().equals(String.valueOf(android.os.Process.myUid()));
   boolean direct=root&&maintenanceShell(c,"test -w "+UpdatePolicy.quote(c.getFilesDir().getPath())+" && echo PRIVATE_OK").contains("PRIVATE_OK");
   if(!useRunAs&&!direct)throw new IOException("当前系统不允许恢复私有设置，自动回退不可用");
   String external=UpdatePolicy.quote(c.getExternalFilesDir(null).getPath());
   String storageTest="test -d "+external+" && test -w "+external+" && echo EXTERNAL_OK";
   if(useRunAs)storageTest="run-as "+c.getPackageName()+" sh -c "+UpdatePolicy.quote(storageTest);
   if(!maintenanceShell(c,storageTest).contains("EXTERNAL_OK"))throw new IOException("系统限制维护进程访问外部存储，请使用系统确认安装；独立回退和清空安装不可用");
   if(android.os.Process.myUid()/100000!=0)throw new IOException("当前维护后端仅支持主用户");
   maintenance=true;capabilityReason="可独立安装、保留设置及启动失败回退";
  }catch(Exception e){maintenance=false;capabilityReason=String.valueOf(e.getMessage());}
 }
 private static synchronized boolean isBusy(Context c)throws Exception{JSONObject pending=load(c).optJSONObject("pending");return busy||pending!=null&&System.currentTimeMillis()-pending.optLong("created")<10*60000;}
 private static synchronized void refreshResult(Context c)throws Exception{
  File result=new File(c.getFilesDir(),"updater-result.json");if(!result.isFile())return;JSONObject data;try{data=new JSONObject(new String(java.nio.file.Files.readAllBytes(result.toPath()),"UTF-8"));}catch(Exception incomplete){return;}
  if(data.optLong("timestamp")<=load(c).optLong("lastResultAt"))return;String state=data.optString("phase");
  phase=state;message=data.optString("message");if(!Arrays.asList("success","rolled_back","failed").contains(state))return;
  load(c).put("lastResultAt",data.optLong("timestamp")).put("lastResult",data);load(c).remove("pending");
  if(state.equals("success")){load(c).put("previous",new JSONObject().put("runDir",data.getString("runDir")).put("version",data.getString("previousVersion")).put("versionCode",data.getLong("previousCode")).put("sha256",data.getString("previousSha")).put("backupSha",data.getString("backupSha")).put("fullBackup",data.optBoolean("fullBackup")));}
  if(state.equals("rolled_back")){load(c).put("blockedVersionCode",data.optLong("targetCode"));load(c).remove("previous");}
  persist(c);WebAudit.record(c,"127.0.0.1","桌面更新结果",message,state.equals("failed")?503:200);
 }
 static synchronized JSONObject state(Context c)throws Exception{
  load(c);JSONObject pending=load(c).optJSONObject("pendingInstaller");if(pending!=null&&c.getPackageManager().getPackageInfo(c.getPackageName(),0).lastUpdateTime>pending.optLong("createdAt")&&code(c)>=pending.getJSONObject("release").optLong("versionCode")){load(c).remove("pendingInstaller");persist(c);pending=null;}if(pending!=null&&phase.equals("idle")){phase="install_permission";message="已保留校验过的安装包，点击继续安装";}refreshResult(c);JSONObject out=new JSONObject().put("currentVersion",version(c)).put("currentVersionCode",code(c)).put("hasPendingInstaller",pending!=null).put("autoCheck",load(c).optBoolean("autoCheck",true)).put("automatic",load(c).optBoolean("automatic")).put("busy",isBusy(c)).put("phase",phase).put("message",message).put("progress",percent).put("maintenanceAvailable",maintenance).put("maintenanceReason",capabilityReason).put("lastChecked",load(c).optLong("lastChecked")).put("sources",new JSONArray(Arrays.asList(UpdatePolicy.manifestSources()))).put("routes",lines).put("repository","https://github.com/"+UpdatePolicy.REPO).put("lastResult",load(c).optJSONObject("lastResult"));
  JSONObject latest=load(c).optJSONObject("latest"),previous=load(c).optJSONObject("previous");out.put("latest",latest==null?JSONObject.NULL:latest).put("newVersion",latest!=null&&latest.optLong("versionCode")>code(c)).put("previous",previous==null?JSONObject.NULL:previous).put("rollbackAvailable",maintenance&&previous!=null);
  return out;
 }
 static synchronized JSONObject configure(Context c,JSONObject input)throws Exception{
  AuroraSettings.only(input,"autoCheck","automatic");
  for(String key:new String[]{"autoCheck","automatic"})if(input.has(key)&&!(input.get(key) instanceof Boolean))throw new IllegalArgumentException("开关必须为布尔值");
  if(input.optBoolean("automatic")&&!maintenance)throw new IllegalArgumentException(capabilityReason);
  JSONObject before=new JSONObject(load(c).toString());
  try{
   if(input.has("autoCheck")){load(c).put("autoCheck",input.getBoolean("autoCheck"));if(!input.getBoolean("autoCheck"))load(c).put("automatic",false);}if(input.has("automatic")){load(c).put("automatic",input.getBoolean("automatic"));if(input.getBoolean("automatic"))load(c).put("autoCheck",true);}
   persist(c);
  }catch(Exception failure){saved=before;throw failure;}
  return state(c);
 }
 static synchronized JSONObject request(Context c,String action)throws Exception{
  if(!Arrays.asList("check","download","cancel","install","rollback","clean").contains(action))throw new IllegalArgumentException("未知更新操作");
  if(isBusy(c))throw new IllegalArgumentException("已有桌面更新操作正在运行");
  if((action.equals("rollback")||action.equals("clean"))&&!maintenance)throw new IllegalArgumentException(capabilityReason);
  if(action.equals("rollback")&&load(c).optJSONObject("previous")==null)throw new IllegalArgumentException("尚未保存上一版本；完成一次受管更新后可回退");
  if(action.equals("cancel")){if(preparedRelease!=null){load(c).put("blockedVersionCode",preparedRelease.optLong("versionCode"));persist(c);}preparedRelease=null;load(c).remove("uploaded");load(c).remove("pendingInstaller");persist(c);phase="idle";message="已取消安装";return state(c);}
  if(action.equals("download")){load(c).remove("pendingInstaller");persist(c);}
  Context app=c.getApplicationContext();busy=true;phase="queued";message="正在准备";percent=0;
  worker.execute(()->{try{if(action.equals("check")){checkNow(app);synchronized(LauncherUpdater.class){JSONObject found=load(app).optJSONObject("latest");if(found!=null&&found.optLong("versionCode")>code(app)){load(app).put("blockedVersionCode",found.optLong("versionCode"));persist(app);}}}else if(action.equals("rollback"))rollback(app);else if(action.equals("install")&&load(app).has("pendingInstaller"))resumeInstaller(app);else installNow(app,action.equals("clean")?"clean":action.equals("download")?"download":"update",false);}
   catch(Exception e){phase="failed";message=String.valueOf(e.getMessage());WebAudit.record(app,"127.0.0.1","桌面更新失败",message,503);}finally{synchronized(LauncherUpdater.class){busy=false;}}});
  return state(c);
 }
 private static void checkNow(Context c)throws Exception{
  phase="checking";message="正在检查 GitHub 更新";synchronized(LauncherUpdater.class){load(c).put("lastAttempt",System.currentTimeMillis());persist(c);}
  JSONObject data=UpdateNetwork.manifest(),release=data.getJSONObject("latest");synchronized(LauncherUpdater.class){load(c).put("latest",release).put("metadataSource",data.getString("source")).put("lastChecked",System.currentTimeMillis());persist(c);}
  phase="idle";message=release.getLong("versionCode")>code(c)?"发现新版本 v"+release.getString("version"):"当前已是最新版本";WebAudit.record(c,"127.0.0.1","检查桌面更新",message,200);
 }
 private static File directory(Context c){File d=new File(c.getFilesDir(),"updater");d.mkdirs();return d;}
 private static void installNow(Context c,String mode,boolean automatic)throws Exception{
  JSONObject release; synchronized(LauncherUpdater.class){release=load(c).optJSONObject("latest");}
  if(!automatic&&mode.equals("update")&&preparedRelease!=null)release=preparedRelease;
  if(!mode.equals("clean")&&preparedRelease==null&&(release==null||System.currentTimeMillis()-load(c).optLong("lastChecked")>3600000)){checkNow(c);synchronized(LauncherUpdater.class){release=load(c).optJSONObject("latest");}}
  File target=new File(directory(c),"download.apk");
  if(!automatic&&mode.equals("update")&&preparedRelease!=null){validate(c,target,preparedRelease);release=preparedRelease;preparedRelease=null;}
  else if(mode.equals("clean")&&(release==null||release.optLong("versionCode")<=code(c))){copy(new File(c.getApplicationInfo().sourceDir),target);release=new JSONObject().put("version",version(c)).put("versionCode",code(c)).put("sha256",UpdatePolicy.sha(target)).put("minSdk",26);}
  else{
   if(release==null)throw new IOException("没有可用的更新信息");
   if(release.getLong("versionCode")<=code(c)){phase="idle";message="当前已是最新版本";return;}
   if(release.optInt("minSdk",26)>android.os.Build.VERSION.SDK_INT)throw new IOException("新版不支持当前 Android 版本");
   phase="testing";message="正在测试原始和加速下载线路";lines=UpdateNetwork.rank(release.getString("url"));
   phase="downloading";message="正在下载更新";String route=UpdateNetwork.download(release,target,lines,(n,total)->percent=(int)(n*100/total));message="下载完成："+route;
  }
  validate(c,target,release);if(mode.equals("download")){preparedRelease=new JSONObject(release.toString());phase="ready";message="下载并校验完成，请选择安装或取消";return;}if(automatic&&!load(c).optBoolean("automatic")){phase="idle";message="已停止自动安装";return;}
  installPrepared(c,target,release,mode,automatic);
 }
 private static void installPrepared(Context c,File target,JSONObject release,String mode,boolean automatic)throws Exception{
  validate(c,target,release);
  // A manual update must never launch a detached process that restores/clears app data.
  if(!automatic&&!mode.equals("clean")){synchronized(LauncherUpdater.class){load(c).put("pendingInstaller",new JSONObject().put("file",target.getName()).put("release",release).put("createdAt",System.currentTimeMillis()));persist(c);}openInstaller(c,target);return;}
  probe(c);if(!maintenance)throw new IOException(capabilityReason);
  phase="backing_up";message=mode.equals("clean")?"正在备份全部桌面数据，确认可恢复后才清空":"正在保存上一版本和设置";
  File backup=UpdateBackup.create(c,mode.equals("clean"));if(c.getFilesDir().getUsableSpace()<backup.length()+target.length()+new File(c.getApplicationInfo().sourceDir).length()+128L*1024*1024)throw new IOException("独立恢复包和安装所需空间不足，未清空数据");Map<String,File> files=new HashMap<>();files.put("/target",target);files.put("/previous",new File(c.getApplicationInfo().sourceDir));files.put("/backup",backup);
  UpdateTransfer transfer=new UpdateTransfer(files);
  JSONObject plan=basePlan(c,mode).put("transferPort",transfer.port()).put("transferToken",transfer.token).put("targetCode",release.getLong("versionCode")).put("targetSha",release.getString("sha256")).put("previousCode",code(c)).put("previousVersion",version(c)).put("previousSha",UpdatePolicy.sha(files.get("/previous"))).put("backupSha",UpdatePolicy.sha(backup));
  try{launch(c,plan);}catch(Exception e){transfer.close();throw e;}
 }
 static JSONObject upload(Context c,InputStream input,long length)throws Exception{
  UpdatePolicy.uploadLength(length);
  synchronized(LauncherUpdater.class){if(isBusy(c))throw new IllegalArgumentException("已有更新任务正在运行");preparedRelease=null;load(c).remove("uploaded");persist(c);busy=true;phase="uploading";message="正在接收并校验手动更新包";}
  File part=new File(directory(c),"uploaded.part.apk"),target=new File(directory(c),"uploaded.apk");
  try{
   if(c.getFilesDir().getUsableSpace()<length+64L*1024*1024)throw new IOException("上传空间不足");
   try(OutputStream out=new FileOutputStream(part)){byte[] buffer=new byte[65536];long remaining=length;while(remaining>0){int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException("安装包未上传完整");out.write(buffer,0,n);remaining-=n;}}
   PackageInfo archive=c.getPackageManager().getPackageArchiveInfo(part.getPath(),0);if(archive==null)throw new IOException("无法识别APK");
   JSONObject release=new JSONObject().put("version",archive.versionName).put("versionCode",UpdatePackages.code(archive)).put("sha256",UpdatePolicy.sha(part)).put("bytes",length);
   validate(c,part,release);UpdatePolicy.uploadVersion(code(c),UpdatePackages.code(archive));
   if((target.exists()&&!target.delete())||!part.renameTo(target))throw new IOException("无法保存上传包");
   release.put("uploadToken",AuroraRules.token()).put("expiresAt",System.currentTimeMillis()+15*60*1000);
   synchronized(LauncherUpdater.class){load(c).put("uploaded",release);persist(c);phase="uploaded";message="上传校验通过，等待确认安装（保留设置）";}
   return new JSONObject(release.toString());
  }catch(Exception error){phase="failed";message=String.valueOf(error.getMessage());throw error;}
  finally{part.delete();synchronized(LauncherUpdater.class){busy=false;}}
 }
 static synchronized JSONObject requestUploaded(Context c,JSONObject input)throws Exception{
  AuroraSettings.only(input,"uploadToken","confirmed");if(!input.optBoolean("confirmed"))throw new IllegalArgumentException("请先确认上传包信息");
  if(isBusy(c))throw new IllegalArgumentException("已有更新任务正在运行");JSONObject release=load(c).optJSONObject("uploaded");
  if(release==null||release.optLong("expiresAt")<System.currentTimeMillis()||!MessageDigest.isEqual(release.optString("uploadToken").getBytes("UTF-8"),input.optString("uploadToken").getBytes("UTF-8")))throw new IllegalArgumentException("上传包已失效，请重新上传");
  File target=new File(directory(c),"uploaded.apk");validate(c,target,release);UpdatePolicy.uploadVersion(code(c),release.getLong("versionCode"));
  load(c).remove("uploaded");persist(c);Context app=c.getApplicationContext();busy=true;phase="queued";message="正在准备手动更新，保留现有设置";
  worker.execute(()->{try{installPrepared(app,target,release,"update",false);}catch(Exception e){phase="failed";message=String.valueOf(e.getMessage());WebAudit.record(app,"127.0.0.1","上传包更新失败",message,503);}finally{synchronized(LauncherUpdater.class){busy=false;}}});return state(c);
 }
 private static void validate(Context c,File apk,JSONObject release)throws Exception{
  PackageManager pm=c.getPackageManager();int flags=android.os.Build.VERSION.SDK_INT>=28?(PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES):PackageManager.GET_SIGNATURES;
  PackageInfo archive=pm.getPackageArchiveInfo(apk.getPath(),flags),installed=pm.getPackageInfo(c.getPackageName(),flags);
  if(archive==null||!c.getPackageName().equals(archive.packageName)||UpdatePackages.code(archive)!=release.getLong("versionCode")||archive.applicationInfo.minSdkVersion>android.os.Build.VERSION.SDK_INT||!sameSigners(archive,installed))throw new IOException("APK 包名、版本、系统要求或签名与当前桌面不匹配");
  if(!UpdatePolicy.sha(apk).equals(release.getString("sha256")))throw new IOException("APK 校验失败");
  if(useRunAs&&(archive.applicationInfo.flags&ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new IOException("新包不支持当前系统的私有设置恢复适配，拒绝受保护更新");
 }
 private static boolean sameSigners(PackageInfo a,PackageInfo b){
  android.content.pm.Signature[] x=android.os.Build.VERSION.SDK_INT>=28&&a.signingInfo!=null?a.signingInfo.getApkContentsSigners():a.signatures,y=android.os.Build.VERSION.SDK_INT>=28&&b.signingInfo!=null?b.signingInfo.getApkContentsSigners():b.signatures;
  if(x==null||y==null||x.length==0||x.length!=y.length)return false;Set<String> first=new HashSet<>(),second=new HashSet<>();for(android.content.pm.Signature s:x)first.add(s.toCharsString());for(android.content.pm.Signature s:y)second.add(s.toCharsString());return first.equals(second);
 }
 private static JSONObject basePlan(Context c,String mode)throws Exception{
  String id=java.util.UUID.randomUUID().toString().replace("-","").substring(0,24);
  return new JSONObject().put("id",id).put("mode",mode).put("dataDir",c.getDataDir().getPath()).put("externalDir",c.getExternalFilesDir(null).getPath()).put("uid",android.os.Process.myUid()).put("useRunAs",useRunAs).put("healthToken",AuroraRules.token()).put("healthTimeoutSeconds",120).put("healthPort",mode.equals("clean")?8899:ConsoleSettings.port(c));
 }
 private static void rollback(Context c)throws Exception{
  probe(c);if(!maintenance)throw new IOException(capabilityReason);
  JSONObject previous=load(c).getJSONObject("previous");JSONObject plan=basePlan(c,"rollback").put("previousRun",previous.getString("runDir")).put("targetCode",previous.getLong("versionCode")).put("targetSha",previous.getString("sha256")).put("backupSha",previous.getString("backupSha")).put("fullBackup",previous.optBoolean("fullBackup")).put("previousCode",code(c)).put("previousVersion",version(c));launch(c,plan);
 }
 private static void launch(Context c,JSONObject plan)throws Exception{
  ensureHelper(c);pruneRecovery(c,plan);String dir=LauncherUpdateAgent.ROOT+plan.getString("id"),source=c.getPackageManager().getApplicationInfo(LauncherUpdateAgent.GUARD,0).sourceDir;
  synchronized(LauncherUpdater.class){load(c).put("pending",new JSONObject().put("id",plan.getString("id")).put("runDir",dir).put("created",System.currentTimeMillis()));persist(c);}
  String encoded=android.util.Base64.encodeToString(plan.toString().getBytes("UTF-8"),android.util.Base64.NO_WRAP);
  String guard=LauncherUpdateAgent.GUARD;
  String command="run-as "+guard+" mkdir -p "+dir+" && run-as "+guard+" chmod 700 "+dir+" && printf '%s' '"+encoded+"' | run-as "+guard+" sh -c "+UpdatePolicy.quote("base64 -d > "+UpdatePolicy.quote(dir+"/plan.json"))+" && run-as "+guard+" chmod 600 "+dir+"/plan.json && (CLASSPATH="+UpdatePolicy.quote(source)+" /system/bin/setsid /system/bin/app_process /system/bin com.gathin.tvhome.LauncherUpdateAgent "+dir+" >/sdcard/Download/aurora-update-agent.log 2>&1 </dev/null &); for i in 1 2 3 4 5; do run-as "+guard+" test -f "+dir+"/status.json && echo AGENT_READY && break; sleep 1; done";

  try{String output=new String(TvAdb.runQueued(c,"exec:"+command,65536,15000),"UTF-8");if(!output.contains("AGENT_READY"))throw new IOException("独立维护进程未启动，未执行清空。请检查系统权限。");phase="installing";message="独立维护进程已启动，稍后会重新打开桌面";}
  catch(Exception e){synchronized(LauncherUpdater.class){load(c).remove("pending");persist(c);}throw e;}
 }
 /** Only remove finished transactions that are no longer the retained rollback point. */
 private static void pruneRecovery(Context c,JSONObject next){
  try{
   Set<String> keep=new HashSet<>();
   JSONObject previous=load(c).optJSONObject("previous");
   if(previous!=null)keep.add(previous.optString("runDir"));
   keep.add(next.optString("previousRun"));
   String root=LauncherUpdateAgent.ROOT,guard=LauncherUpdateAgent.GUARD;
   String listing=maintenanceShell(c,"run-as "+guard+" ls "+UpdatePolicy.quote(root));
   for(String id:listing.split("\\s+")){
    if(!id.matches("[a-f0-9]{24}"))continue;String dir=root+id;
    if(keep.contains(dir))continue;
    JSONObject status=new JSONObject(maintenanceShell(c,"run-as "+guard+" cat "+UpdatePolicy.quote(dir+"/status.json")));
    if(Arrays.asList("success","rolled_back","failed").contains(status.optString("phase")))
     maintenanceShell(c,"run-as "+guard+" rm -rf "+UpdatePolicy.quote(dir));
   }
  }catch(Exception ignored){/* Retention failure must never remove an unverified recovery point. */}
 }
 private static void ensureHelper(Context c)throws Exception{
  PackageManager pm=c.getPackageManager();try{PackageInfo installed=pm.getPackageInfo(LauncherUpdateAgent.GUARD,PackageManager.GET_SIGNATURES),owner=pm.getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNATURES);
   if(!java.util.Arrays.equals(installed.signatures,owner.signatures))throw new IOException("更新维护组件签名不匹配");
   if(UpdatePackages.code(installed)>=code(c))return;
  }catch(PackageManager.NameNotFoundException absent){}
  String source=UpdatePolicy.quote(c.getApplicationInfo().sourceDir);
  String output=maintenanceShell(c,"CLASSPATH="+source+" app_process /system/bin com.gathin.tvhome.LauncherUpdateAgent --install-helper "+source);
  if(!output.contains("HELPER_INSTALLED"))throw new IOException("维护组件安装失败："+output);
  if(!maintenanceShell(c,"run-as "+LauncherUpdateAgent.GUARD+" id -u").matches("\\d+"))throw new IOException("维护组件私有存储不可访问");
 }
 private static String maintenanceShell(Context c,String command)throws Exception{return new String(TvAdb.runQueued(c,"shell:"+command,1024*1024,15000),"UTF-8").trim();}
 private static void resumeInstaller(Context c)throws Exception{
  openInstaller(c,pendingApk(c));
 }
 private static boolean openInstaller(Context c,File apk)throws Exception{
  installerStatus(c,"install_permission","安装包已保留，正在打开电视安装确认");
  c.startActivity(new Intent(c,UpdateInstallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;
 }
 static synchronized File pendingApk(Context c)throws Exception{JSONObject pending=load(c).getJSONObject("pendingInstaller");String file=pending.getString("file");if(!file.equals("uploaded.apk")&&!file.equals("download.apk"))throw new IOException("待安装文件无效");File apk=new File(directory(c),file);validate(c,apk,pending.getJSONObject("release"));return apk;}
 static void installerStatus(Context c,String value,String text){phase=value;message=text;WebAudit.record(c,"127.0.0.1","系统安装流程",text,200);}
 private static void copy(File source,File target)throws Exception{try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(target)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
}
