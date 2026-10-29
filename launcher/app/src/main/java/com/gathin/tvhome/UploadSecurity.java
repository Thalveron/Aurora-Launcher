package com.gathin.tvhome;
import android.content.Context;import android.content.SharedPreferences;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
/** Existing credentials migrate in place; fresh installs never generate a hidden password. */
final class UploadSecurity {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("upload_security",0);}
 static boolean openAccess(Context c){return prefs(c).getBoolean("openAccess",false);}
 static boolean initialized(Context c){return !password(c).isEmpty();}
 static String password(Context c){return prefs(c).getString("password","");}
 static String username(Context c){return prefs(c).getString("username",initialized(c)?"tv":"");}
 static synchronized void initialize(Context c,String name,String value,String confirmation){
  if(initialized(c)||openAccess(c))throw new IllegalStateException("访问方式已经设置，请在安全设置修改");
  ConsoleAccessRules.username(name);ConsoleAccessRules.password(value,confirmation);
  if(!prefs(c).edit().putString("username",name).putString("password",value).commit())throw new IllegalStateException("账号保存失败");
 }
 static synchronized void initializeOpen(Context c){
  if(initialized(c)||openAccess(c))throw new IllegalStateException("访问方式已经设置，请在安全设置修改");
  if(!prefs(c).edit().putBoolean("openAccess",true).commit())throw new IllegalStateException("访问方式保存失败");
 }
 static boolean authorized(Context c,String header){
  if(!initialized(c)||header==null||!header.startsWith("Basic "))return false;
  try{String credentials=new String(android.util.Base64.decode(header.substring(6),android.util.Base64.DEFAULT),StandardCharsets.UTF_8);int colon=credentials.indexOf(':');return colon>0&&credentials.substring(0,colon).equals(username(c))&&MessageDigest.isEqual(password(c).getBytes(StandardCharsets.UTF_8),credentials.substring(colon+1).getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException e){return false;}
 }
}
