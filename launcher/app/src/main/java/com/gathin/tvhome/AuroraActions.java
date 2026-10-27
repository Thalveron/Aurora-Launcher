package com.gathin.tvhome;
import android.content.Context;import org.json.*;import java.util.*;
/** One registry owns validation, permission and confirmation metadata and dispatch. */
final class AuroraActions {
 interface Executor{Object run(Context c,JSONObject params)throws Exception;}
 static final class Spec{
  final String name,permission;final JSONObject parameters;final boolean confirmation;final Executor executor;
  Spec(String n,String schema,String perm,boolean confirm,Executor e)throws Exception{name=n;parameters=new JSONObject(schema);permission=perm;confirmation=confirm;executor=e;}
  JSONObject json()throws Exception{return new JSONObject().put("action",name).put("parameters",parameters).put("permission",permission).put("requiresConfirmation",confirmation);}
 }
 private static final LinkedHashMap<String,Spec> registry=new LinkedHashMap<>();
 static {try{
  add("launch_app","{\"package\":\"string\"}","control",(c,p)->{String pkg=p.getString("package");WebApps.valid(c,pkg);AuroraHttp.controls(c).select("launch").launch(pkg);return new JSONObject();});
  add("set_appearance","{\"patch\":\"object\"}","settings",(c,p)->AuroraSettings.save(c,p.getJSONObject("patch")));
  add("open_settings","{\"kind\":\"string\"}","control",(c,p)->{String kind=p.getString("kind");AuroraSettings.choice(kind,"wifi","bluetooth","display");String intent=kind.equals("wifi")?android.provider.Settings.ACTION_WIFI_SETTINGS:kind.equals("bluetooth")?android.provider.Settings.ACTION_BLUETOOTH_SETTINGS:android.provider.Settings.ACTION_DISPLAY_SETTINGS;c.startActivity(SystemSettings.intent(c,intent).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return new JSONObject().put("opened",kind);});
  add("switch_theme","{\"theme\":\"string\"}","settings",(c,p)->switchTheme(c,p.getString("theme")));
  for(String transport:new String[]{"play","pause","next","prev"})add("media_"+transport,"{}","control",(c,p)->AuroraData.transport(c,transport,p));
  add("media_seek","{\"positionMs\":\"integer\"}","control",(c,p)->AuroraData.transport(c,"seek",p));
  add("media_mode","{\"mode\":\"integer\"}","control",(c,p)->AuroraData.transport(c,"mode",p));
  add("enter_saver","{}","control",(c,p)->AuroraSaver.preview(c));
  add("enter_saver_auto","{}","control",(c,p)->AuroraSaver.preview(c,true));
  add("exit_saver","{}","control",(c,p)->{AuroraSaver.exit(c);return new JSONObject();});
 }catch(Exception e){throw new ExceptionInInitializerError(e);}}
 private static void add(String n,String s,String permission,Executor e)throws Exception{registry.put(n,new Spec(n,s,permission,false,e));}
 static JSONArray list()throws Exception{JSONArray out=new JSONArray();for(Spec s:registry.values())out.put(s.json());return out;}
 static Object run(Context c,JSONObject body,boolean authorized)throws Exception{
  if(!authorized)throw new SecurityException("需要登录才能执行动作");
  AuroraSettings.only(body,"action","params");Spec s=registry.get(body.getString("action"));if(s==null)throw new IllegalArgumentException("未注册的动作");
  JSONObject params=body.getJSONObject("params");String[] keys=new String[s.parameters.length()];int index=0;for(Iterator<String> i=s.parameters.keys();i.hasNext();)keys[index++]=i.next();AuroraSettings.only(params,keys);
  for(String key:keys){Object value=params.get(key);String type=s.parameters.getString(key);if(type.equals("string")&&!(value instanceof String)||type.equals("object")&&!(value instanceof JSONObject)||type.equals("integer")&&(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||((Number)value).doubleValue()!=((Number)value).longValue()))throw new IllegalArgumentException("动作参数类型无效："+key);}
  return s.executor.run(c,params);
 }
 static JSONObject switchTheme(Context c,String name)throws Exception{
  AuroraThemes.id(name);AuroraThemes.manifest(c,name);if(name.equals("cards"))try{AuroraLibrary.inheritNativeBackground(c);}catch(Exception error){WebAudit.record(c,"127.0.0.1","继承桌面背景失败",String.valueOf(error.getMessage()),503);}
  synchronized(AuroraThemes.class){JSONObject config=AuroraThemes.config(c);config.put("theme",name);config.remove("forcePackaged");AuroraThemes.save(c,config);AuroraSettings.preview=null;AuroraThemeProfiles.read(c,name,"appearance");AuroraBackgrounds.changed(c);return AuroraThemes.publicConfig(c);}
 }
}
