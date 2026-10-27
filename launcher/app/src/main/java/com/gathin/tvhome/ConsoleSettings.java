package com.gathin.tvhome;
import android.content.*;import java.net.*;import org.json.*;
/** One credential/port/path transaction, with a read-only fallback for older installations. */
final class ConsoleSettings {
 static final int INTERNAL_PORT=8899;
 static String path(Context c){return UploadSecurity.prefs(c).getString("path",c.getSharedPreferences("background",0).getString("upload_path",""));}
 static int port(Context c){return UploadSecurity.prefs(c).getInt("port",INTERNAL_PORT);}
 static String base(Context c){String path=path(c);return path.isEmpty()?"/":"/"+path+"/";}
 static JSONObject get(Context c)throws Exception{return new JSONObject().put("username",UploadSecurity.username(c)).put("port",port(c)).put("path",path(c)).put("initialized",UploadSecurity.initialized(c));}
 static synchronized JSONObject save(Context c,JSONObject body)throws Exception{
  AuroraSettings.only(body,"username","password","passwordConfirm","port","path");
  String name=ConsoleAccessRules.username(body.optString("username",UploadSecurity.username(c)));
  String path=ConsoleAccessRules.path(body.optString("path",path(c)));
  int oldPort=port(c),port=oldPort;
  if(body.has("port")){Object raw=body.get("port");if(!(raw instanceof Number)||((Number)raw).doubleValue()!=((Number)raw).intValue())throw new IllegalArgumentException("端口必须是整数");port=ConsoleAccessRules.port(((Number)raw).intValue());}
  String password=body.optString("password","");if(!password.isEmpty())ConsoleAccessRules.password(password,body.optString("passwordConfirm",""));
  if(!UploadSecurity.initialized(c))throw new IllegalStateException("请先初始化账号");
  if(port!=oldPort&&port!=INTERNAL_PORT)try(ServerSocket probe=new ServerSocket()){probe.setReuseAddress(true);probe.bind(new InetSocketAddress(port));}catch(java.io.IOException busy){throw new IllegalArgumentException("该端口已被占用，设置未保存");}
  boolean changed=!name.equals(UploadSecurity.username(c))||!path.equals(path(c))||port!=oldPort||!password.isEmpty();
  SharedPreferences.Editor e=UploadSecurity.prefs(c).edit().putString("username",name).putString("path",path).putInt("port",port);
  if(!password.isEmpty())e.putString("password",password);
  if(!e.commit())throw new IllegalStateException("安全设置保存失败");
  if(changed)AuroraLogin.revokeAll();
  if(port!=oldPort)UploadService.reconfigure(c,oldPort);
  return get(c).put("loginRequired",changed).put("restartPending",port!=oldPort).put("loginPath",(path(c).isEmpty()?"":path(c)+"/")+"console/login.html");
 }
}
