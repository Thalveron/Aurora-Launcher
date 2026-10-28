package com.gathin.tvhome;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;import java.lang.reflect.*;import java.util.zip.*;import org.json.*;import android.content.pm.PackageInstaller;
/** Detached shell/root process, loaded from its own immutable APK copy outside app data. */
public final class LauncherUpdateAgent {
 static final String GUARD="com.gathin.tvhome.updater",ROOT="/data/user/0/"+GUARD+"/files/jobs/";
 private static final String PKG="com.gathin.tvhome";private static File run;private static JSONObject plan;private static boolean root;
 public static void main(String[] args){
  try{execute(args);}catch(Throwable failure){System.err.println("Maintenance error: "+safe(failure));System.exit(1);}
 }
 private static void execute(String[] args)throws Exception{
  if(args.length==2&&args[0].equals("--install-helper")){installHelper(new File(args[1]));System.out.println("HELPER_INSTALLED");return;}
  if(args.length==1&&args[0].equals("--create-session")){System.out.println(createSession());return;}
  if(args.length!=1||!args[0].matches(java.util.regex.Pattern.quote(ROOT)+"[a-f0-9]{24}"))throw new IllegalArgumentException("Invalid update workspace");
  run=new File(args[0]);plan=read(new File(run,"plan.json"));root=android.os.Process.myUid()==0;
  if(!plan.getString("dataDir").matches("/data/(?:user/0|data)/com\\.gathin\\.tvhome")||!plan.getString("externalDir").matches("/(?:storage/emulated/0|sdcard)/Android/data/com\\.gathin\\.tvhome/files")||!plan.getString("id").equals(run.getName()))throw new IllegalArgumentException("Invalid app workspace");
  String mode=plan.getString("mode");if(!Arrays.asList("update","clean","rollback").contains(mode))throw new IllegalArgumentException("Invalid mode");
  try{
   if(mode.equals("rollback")){
    File previousRun=new File(plan.getString("previousRun"));if(!previousRun.getPath().matches(java.util.regex.Pattern.quote(ROOT)+"[a-f0-9]{24}"))throw new IOException("Invalid rollback workspace");
    File apk=new File(previousRun,"previous.apk"),backup=new File(previousRun,"backup.zip");verify(apk,plan.getString("targetSha"));verify(backup,plan.getString("backupSha"));status("rolling_back","正在回退到上一版本");
    install(apk);command("am","force-stop",PKG);restore(backup,plan.optBoolean("fullBackup"));syncAssets(apk);launch(false);status("rolled_back","已回退到上一版本并恢复设置");return;
   }
   status("preparing","正在保存独立恢复包");
   receive("/target",new File(run,"target.apk"),plan.getString("targetSha"));receive("/previous",new File(run,"previous.apk"),plan.getString("previousSha"));receive("/backup",new File(run,"backup.zip"),plan.getString("backupSha"));
   status("installing","正在覆盖安装，原设置已保存");
   try{install(new File(run,"target.apk"));if(mode.equals("clean")){String clear=command("pm","clear",PKG);if(!clear.contains("Success"))throw new IOException("清空设置失败");}syncAssets(new File(run,"target.apk"));launch(true);status("verifying","新版已安装，正在观察启动状态");
    if(!healthy())throw new IOException("新版未通过启动健康检查");
    status("success",mode.equals("clean")?"全新安装完成":"更新成功，设置已保留");
   }catch(Exception failed){
    status("rolling_back","更新异常，正在自动回退："+safe(failed));
    install(new File(run,"previous.apk"));command("am","force-stop",PKG);restore(new File(run,"backup.zip"),mode.equals("clean"));syncAssets(new File(run,"previous.apk"));launch(false);status("rolled_back","更新异常，已自动恢复上一版本和设置");
   }
  }catch(Throwable failure){status("failed","维护失败："+safe(failure));}
 }
 private static String safe(Throwable e){while(e.getCause()!=null)e=e.getCause();String s=e.toString();return s.length()>500?s.substring(0,500):s;}
 private static JSONObject read(File f)throws Exception{return new JSONObject(command("run-as",GUARD,"cat",f.getPath()));}
 private static void verify(File f,String sha)throws Exception{UpdatePolicy.digest(sha);if(!command("run-as",GUARD,"sha256sum",f.getPath()).trim().startsWith(sha+" "))throw new IOException("恢复文件校验失败");}
 private static void receive(String path,File out,String sha)throws Exception{
  int port=plan.getInt("transferPort");if(port<1024||port>65535)throw new IOException("Invalid transfer port");
  HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(15000);c.setRequestProperty("X-Aurora-Transfer",plan.getString("transferToken"));
  try{if(c.getResponseCode()!=200)throw new IOException("私有恢复文件传输失败");try(InputStream in=c.getInputStream()){writeGuard(out,in);}}finally{c.disconnect();}verify(out,sha);
 }
 private static int createSession()throws Exception{return createSession(PKG);}
 private static int createSession(String packageName)throws Exception{
  Object pm=Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null);Method get=pm.getClass().getMethod("getPackageInstaller");get.setAccessible(true);Object installer=get.invoke(pm);
  PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(packageName);Field flags=PackageInstaller.SessionParams.class.getDeclaredField("installFlags");flags.setAccessible(true);flags.setInt(params,2|128);
  Integer id=null;for(Method method:installer.getClass().getMethods())if(method.getName().equals("createSession")){
   Class<?>[] types=method.getParameterTypes();Object[] values=new Object[types.length];int strings=0;boolean valid=true;
   for(int i=0;i<types.length;i++){if(types[i]==PackageInstaller.SessionParams.class)values[i]=params;else if(types[i]==String.class)values[i]=strings++==0?"com.android.shell":null;else if(types[i]==int.class)values[i]=0;else{valid=false;break;}}
   if(valid){method.setAccessible(true);id=(Integer)method.invoke(installer,values);break;}
  }if(id==null)throw new IOException("系统没有可用的安装会话接口");return id;

 }
 private static void installHelper(File source)throws Exception{
  byte[] bytes;try(ZipFile zip=new ZipFile(source)){ZipEntry entry=zip.getEntry("assets/update-agent.apk");if(entry==null||entry.getSize()<1000||entry.getSize()>1024*1024)throw new IOException("维护组件资源无效");try(InputStream in=zip.getInputStream(entry)){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)b.write(data,0,n);bytes=b.toByteArray();}}
  int id=createSession(GUARD);boolean done=false;
  try{String out=process(new String[]{"pm","install-write","-S",String.valueOf(bytes.length),String.valueOf(id),"base.apk","-"},null,120,bytes);if(!out.contains("Success"))throw new IOException(out);out=command("pm","install-commit",String.valueOf(id));if(!out.contains("Success"))throw new IOException(out);done=true;}
  finally{if(!done)try{command("pm","install-abandon",String.valueOf(id));}catch(Exception ignored){}}
 }
 private static void install(File apk)throws Exception{
  int id=createSession();long size=Long.parseLong(command("run-as",GUARD,"stat","-c","%s",apk.getPath()).trim());
  boolean committed=false;try{
   String output=process(new String[]{"pm","install-write","-S",String.valueOf(size),String.valueOf(id),"base.apk","-"},apk,120);
   if(!output.contains("Success"))throw new IOException("写入安装包失败："+output);
   output=command("pm","install-commit",String.valueOf(id));if(!output.contains("Success"))throw new IOException("提交安装失败："+output);committed=true;
  }finally{if(!committed)try{command("pm","install-abandon",String.valueOf(id));}catch(Exception ignored){}}
 }
 private static String command(String... args)throws Exception{return process(args,null,120);}
 private static String process(String[] args,File stdin,int seconds)throws Exception{return process(args,stdin,seconds,null);}
 private static String process(String[] args,File stdin,int seconds,byte[] literal)throws Exception{
  Process p=new ProcessBuilder(args).redirectErrorStream(true).start();ByteArrayOutputStream output=new ByteArrayOutputStream();
  Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)synchronized(output){if(output.size()<131072)output.write(b,0,Math.min(n,131072-output.size()));}}catch(Exception ignored){}});reader.setDaemon(true);reader.start();
  Thread writer=new Thread(()->{try(OutputStream out=p.getOutputStream()){if(literal!=null)out.write(literal);else if(stdin!=null)try(InputStream in=openGuard(stdin)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}catch(Exception ignored){}});writer.setDaemon(true);writer.start();
  if(!p.waitFor(seconds,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("系统安装操作超时");}reader.join(2000);String text=output.toString("UTF-8").trim();if(p.exitValue()!=0)throw new IOException(text);return text;
 }
 private static void launch(boolean probation)throws Exception{
  command("am","start-foreground-service","-n",PKG+"/.UploadService");
  List<String> args=new ArrayList<>(Arrays.asList("am","start","-n",PKG+"/.MainActivity"));
  if(probation){args.addAll(Arrays.asList("--es","aurora_update_token",plan.getString("healthToken"),"--ei","aurora_update_code",String.valueOf(plan.getInt("targetCode"))));}
  command(args.toArray(new String[0]));
 }
 private static boolean healthy()throws Exception{
  int timeout=Math.max(30,Math.min(180,plan.optInt("healthTimeoutSeconds",120)));long deadline=System.nanoTime()+timeout*1_000_000_000L,stable=0;
  while(System.nanoTime()<deadline){
   try{HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+Math.max(1024,Math.min(65535,plan.optInt("healthPort",8899)))+"/api/v1/updates/health").openConnection();c.setConnectTimeout(1500);c.setReadTimeout(2000);c.setRequestProperty("X-Aurora-Update-Token",plan.getString("healthToken"));
    try{if(c.getResponseCode()!=200)throw new IOException();try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(out.size()>8192)throw new IOException();out.write(b,0,n);}JSONObject result=new JSONObject(out.toString("UTF-8")).getJSONObject("data");if(!result.getBoolean("ready")||result.getInt("versionCode")!=plan.getInt("targetCode"))throw new IOException();}}
    finally{c.disconnect();}
    if(stable==0)stable=System.nanoTime();if(System.nanoTime()-stable>=20_000_000_000L)return true;
   }catch(Exception notReady){stable=0;}Thread.sleep(2000);
  }return false;
 }
 private static void validateBackup(File backup)throws Exception{
  int count=0;long size=0;boolean privateData=false;
  try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(openGuard(backup)))){ZipEntry entry;byte[] buffer=new byte[65536];
   while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(name.contains("..")||name.contains("\\")||name.startsWith("/")||!(name.startsWith("private/files/")||name.startsWith("private/shared_prefs/")||name.startsWith("private/databases/")||name.startsWith("external/")))throw new IOException("恢复快照目录无效");
    if(entry.isDirectory())continue;if(++count>100000)throw new IOException("恢复快照文件过多");privateData|=name.startsWith("private/");int n;while((n=zip.read(buffer))!=-1){size+=n;if(size>3L*1024*1024*1024)throw new IOException("恢复快照过大");}
   }
  }if(count==0||!privateData)throw new IOException("恢复快照为空，拒绝清除当前设置");
 }
 private static void restore(File backup,boolean full)throws Exception{
  validateBackup(backup);
  String data=plan.getString("dataDir"),external=plan.getString("externalDir");
  appCommand("rm -rf "+UpdatePolicy.quote(data+"/files")+" "+UpdatePolicy.quote(data+"/shared_prefs")+" "+UpdatePolicy.quote(data+"/databases")+(full?" "+UpdatePolicy.quote(external):""));
  try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(openGuard(backup)))){ZipEntry entry;int count=0;
   while((entry=zip.getNextEntry())!=null){if(entry.isDirectory())continue;if(++count>100000)throw new IOException("快照文件数量无效");String name=entry.getName();if(name.contains("..")||name.contains("\\")||name.startsWith("/"))throw new IOException("快照路径无效");
    File target;if(name.startsWith("private/")){String tail=name.substring(8);if(!(tail.startsWith("files/")||tail.startsWith("shared_prefs/")||tail.startsWith("databases/")))throw new IOException("快照目录无效");target=new File(data,tail);}else if(name.startsWith("external/"))target=new File(external,name.substring(9));else throw new IOException("快照目录无效");
    appCommand("mkdir -p "+UpdatePolicy.quote(target.getParent()));writeAsApp(target,zip);
   }
  }
 }
 private static void appCommand(String script)throws Exception{
  if(root&&!plan.optBoolean("useRunAs"))command("sh","-c",script);else command("run-as",PKG,"sh","-c",script);
 }
 private static void writeAsApp(File file,InputStream in)throws Exception{
  if(root&&!plan.optBoolean("useRunAs")){file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(file.getPath().startsWith(plan.getString("dataDir"))){command("chown",plan.getInt("uid")+":"+plan.getInt("uid"),file.getPath());command("chmod","600",file.getPath());}}
  else{Process p=new ProcessBuilder("run-as",PKG,"sh","-c","cat > "+UpdatePolicy.quote(file.getPath())).redirectErrorStream(true).start();try(OutputStream out=p.getOutputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(!p.waitFor(30,TimeUnit.SECONDS)||p.exitValue()!=0)throw new IOException("无法恢复应用设置");}
 }
 private static void syncAssets(File apk)throws Exception{
  String external=plan.getString("externalDir");
  try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(openGuard(apk)))){ZipEntry e;
   while((e=zip.getNextEntry())!=null){String name=e.getName();if(e.isDirectory()||name.contains("..")||!(name.startsWith("assets/aurora/console/")||name.startsWith("assets/aurora/core/")||name.equals("assets/aurora/themes/cards/theme.js")))continue;File target=new File(external,name.substring("assets/".length()));appCommand("mkdir -p "+UpdatePolicy.quote(target.getParent()));writeAsApp(target,zip);}
  }
  if(root&&!plan.optBoolean("useRunAs")){String data=plan.getString("dataDir");for(String dir:new String[]{"files","shared_prefs","databases"})command("chown","-R",plan.getInt("uid")+":"+plan.getInt("uid"),data+"/"+dir);}
 }
 private static InputStream openGuard(File file)throws Exception{
  Process p=new ProcessBuilder("run-as",GUARD,"cat",file.getPath()).start();
  return new FilterInputStream(p.getInputStream()){public void close()throws IOException{super.close();try{if(!p.waitFor(5,TimeUnit.SECONDS))p.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}}};
 }
 private static void writeGuard(File file,InputStream in)throws Exception{
  Process p=new ProcessBuilder("run-as",GUARD,"sh","-c","cat > "+UpdatePolicy.quote(file.getPath())).redirectErrorStream(true).start();
  try(OutputStream out=p.getOutputStream()){byte[] b=new byte[65536];int n;long size=0;while((n=in.read(b))!=-1){size+=n;if(size>3L*1024*1024*1024)throw new IOException("恢复文件过大");out.write(b,0,n);}}
  if(!p.waitFor(30,TimeUnit.SECONDS)||p.exitValue()!=0)throw new IOException("无法保存独立恢复文件");
 }
 private static void status(String phase,String message)throws Exception{
  JSONObject status=new JSONObject().put("id",plan.getString("id")).put("phase",phase).put("message",message).put("mode",plan.getString("mode")).put("runDir",run.getPath()).put("previousCode",plan.optLong("previousCode")).put("previousVersion",plan.optString("previousVersion")).put("previousSha",plan.optString("previousSha")).put("backupSha",plan.optString("backupSha")).put("fullBackup",plan.getString("mode").equals("clean")).put("targetCode",plan.optLong("targetCode")).put("timestamp",System.currentTimeMillis());
  File rootFile=new File(run,"status.json");writeGuard(rootFile,new ByteArrayInputStream(status.toString().getBytes(StandardCharsets.UTF_8)));
  try{File appFile=new File(plan.getString("dataDir"),"files/updater-result.json");appCommand("mkdir -p "+UpdatePolicy.quote(appFile.getParent()));File temp=new File(appFile.getPath()+".next");writeAsApp(temp,new ByteArrayInputStream(status.toString().getBytes(StandardCharsets.UTF_8)));appCommand("mv "+UpdatePolicy.quote(temp.getPath())+" "+UpdatePolicy.quote(appFile.getPath()));}catch(Exception ignored){}
 }
}
