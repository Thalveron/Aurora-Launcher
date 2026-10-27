package com.gathin.tvhome;
import android.content.*;import java.io.*;import org.json.*;import java.security.MessageDigest;
/** Private, time-limited handshake. A running server alone does not count as a rendered desktop. */
final class UpdateHealth {
 private static volatile String token="";private static volatile boolean ready;private static volatile long expires;
 static void begin(Context c,Intent intent){
  try{
   File file=new File(c.getFilesDir(),"update-probation.json");
   if(intent!=null&&intent.hasExtra("aurora_update_token")){
    String value=intent.getStringExtra("aurora_update_token");int expected=intent.getIntExtra("aurora_update_code",-1);
    if(value==null||!value.matches("[A-Za-z0-9_-]{32}"))return;
    ready=false;JSONObject data=new JSONObject().put("token",value).put("code",expected).put("expires",System.currentTimeMillis()+600000);
    try(FileOutputStream out=new FileOutputStream(file)){out.write(data.toString().getBytes("UTF-8"));}
   }
   if(file.isFile()){JSONObject data=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),"UTF-8"));if(data.getLong("code")!=UpdatePackages.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0)))return;token=data.getString("token");expires=data.getLong("expires");}
  }catch(Exception ignored){}
 }
 static void uiReady(Context c){if(!token.isEmpty()&&System.currentTimeMillis()<expires)ready=true;}
 static JSONObject sample(Context c,String value)throws Exception{
  if(token.isEmpty()||value==null||System.currentTimeMillis()>expires||!MessageDigest.isEqual(token.getBytes("UTF-8"),value.getBytes("UTF-8")))throw new SecurityException("无效的更新健康检查");
  return new JSONObject().put("versionCode",UpdatePackages.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0))).put("ready",ready);
 }
}
