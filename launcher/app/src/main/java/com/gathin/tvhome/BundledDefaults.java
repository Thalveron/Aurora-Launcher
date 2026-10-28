package com.gathin.tvhome;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import android.media.AudioManager;
import java.io.*;import org.json.*;
/** Optional owner template. An explicitly enabled owner-template migration may also seed once on upgrade; credentials are never imported. */
final class BundledDefaults {
 private static volatile boolean running;private static volatile String error="";
 static synchronized void start(Context context){
  Context c=context.getApplicationContext();
  try{
   if(running)return;
   try(InputStream ignored=c.getAssets().open("private-defaults/bundle.json")){}catch(FileNotFoundException absent){return;}
   JSONObject metadata=read(c);String templateId=metadata.getString("templateId");if(!templateId.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("Invalid template id");
   if(templateId.equals(c.getSharedPreferences("bundled_defaults",0).getString("appliedTemplate","")))return;
   File external=c.getExternalFilesDir(null);
   boolean existing=new File(c.getFilesDir(),"local-adb-key").isFile()
    ||!c.getSharedPreferences("launcher_settings",0).getAll().isEmpty()
    ||!c.getSharedPreferences("upload_security",0).getAll().isEmpty()
    ||(external!=null&&new File(external,"aurora/config.json").isFile());
   boolean upgrade=metadata.optBoolean("applyOnUpgrade",false);
   if(existing&&!upgrade&&!c.getSharedPreferences("bundled_defaults",0).getBoolean("pending",false)){c.getSharedPreferences("bundled_defaults",0).edit().putBoolean("done",true).putString("appliedTemplate",templateId).commit();return;}
   if(!c.getSharedPreferences("bundled_defaults",0).edit().putBoolean("pending",true).commit())throw new IOException("Cannot start default migration");running=true;error="";
   new Thread(()->{try{apply(c);if(!c.getSharedPreferences("bundled_defaults",0).edit().putBoolean("done",true).putString("appliedTemplate",templateId).remove("pending").commit())throw new IOException("Cannot finish default migration");}
    catch(Exception failure){error="默认素材初始化失败，请检查可用空间后重新打开";android.util.Log.e("AuroraDefaults",error);}
    finally{running=false;}},"aurora-defaults").start();
  }catch(Exception unavailable){error="默认配置不可用";}
 }
 static boolean showLoading(Activity activity){
  if(!running){if(!error.isEmpty())Toast.makeText(activity,error,Toast.LENGTH_LONG).show();return false;}
  TextView loading=new TextView(activity);loading.setText("正在准备 Aurora Launcher…");loading.setTextColor(-1);loading.setGravity(17);loading.setBackgroundColor(0xff101925);
  loading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,Math.max(18,activity.getResources().getDisplayMetrics().heightPixels/30f));activity.setContentView(loading);
  Handler handler=new Handler(Looper.getMainLooper());handler.postDelayed(new Runnable(){public void run(){if(activity.isFinishing()||activity.isDestroyed())return;if(running)handler.postDelayed(this,100);else activity.recreate();}},100);return true;
 }
 static void awaitReady()throws IOException,InterruptedException{while(running)Thread.sleep(50);if(!error.isEmpty())throw new IOException(error);}
 private static JSONObject read(Context c)throws Exception{try(InputStream in=c.getAssets().open("private-defaults/bundle.json")){return new JSONObject(new String(AuroraThemes.read(in,1024*1024),java.nio.charset.StandardCharsets.UTF_8));}}
 private static void apply(Context c)throws Exception{
  JSONObject bundle=read(c);if(bundle.getInt("schema")!=1)throw new IOException("Unsupported defaults");
  JSONArray files=bundle.getJSONArray("files");long total=0;for(int i=0;i<files.length();i++)total+=files.getJSONObject(i).getLong("bytes");
  if(c.getFilesDir().getUsableSpace()<total+32L*1024*1024)throw new IOException("Insufficient storage");
  for(int i=0;i<files.length();i++){
   JSONObject item=files.getJSONObject(i);String path=item.getString("target"),asset=item.getString("asset");
   if(!asset.matches("media/[a-f0-9]{64}[.][A-Za-z0-9]+")||path.contains("..")||path.contains("\\\\"))throw new IOException("Invalid default path");
   File target;if(path.startsWith("private/backgrounds/")||path.matches("private/(order[.]txt|library_apps[.]txt|library_home_removed[.]txt)"))target=new File(c.getFilesDir(),path.substring(8));
   else if(path.startsWith("external/aurora/library/")||path.startsWith("external/aurora/bgm/"))target=new File(c.getExternalFilesDir(null),path.substring(9));else throw new IOException("Forbidden default path");
   target.getParentFile().mkdirs();File part=new File(target.getPath()+".seed");
   try(InputStream in=c.getAssets().open("private-defaults/"+asset);OutputStream out=new FileOutputStream(part)){byte[] b=new byte[65536];int n;long bytes=0;while((n=in.read(b))!=-1){bytes+=n;if(bytes>item.getLong("bytes"))throw new IOException("Invalid default size");out.write(b,0,n);}if(bytes!=item.getLong("bytes"))throw new IOException("Incomplete default file");}
   if(!UpdatePolicy.sha(part).equals(item.getString("sha256"))||!part.renameTo(target))throw new IOException("Default file integrity failure");
  }
  JSONObject all=bundle.getJSONObject("preferences");
  java.util.Set<String> allowed=new java.util.HashSet<>(java.util.Arrays.asList("launcher_settings","desktop_appearance","desktop_language","background","ambient_music","idle_standby","input_shortcut","aurora_bgm_shared_saver","aurora_bgm_theme_cards","aurora_bgm_theme_foundation","aurora_voice","aurora_library","aurora_bgm","network_monitor"));
  for(java.util.Iterator<String> names=all.keys();names.hasNext();){
   String name=names.next();if(!allowed.contains(name))throw new IOException("Forbidden preferences");SharedPreferences.Editor edit=c.getSharedPreferences(name,0).edit();if(bundle.optBoolean("applyOnUpgrade",false))edit.clear();JSONObject values=all.getJSONObject(name);
   for(java.util.Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();JSONObject v=values.getJSONObject(key);switch(v.getString("type")){
    case "string":edit.putString(key,v.getString("value"));break;case "boolean":edit.putBoolean(key,v.getBoolean("value"));break;case "int":edit.putInt(key,v.getInt("value"));break;case "long":edit.putLong(key,v.getLong("value"));break;case "float":edit.putFloat(key,(float)v.getDouble("value"));break;default:throw new IOException("Invalid preference type");
   }}if(!edit.commit())throw new IOException("Cannot save defaults");
  }
  JSONObject format=bundle.optJSONObject("timeFormat");if(format!=null&&format.has("hour24")&&!c.getSharedPreferences("desktop_location",0).edit().putBoolean("hour24",format.getBoolean("hour24")).commit())throw new IOException("Cannot save time format");
  if(bundle.has("homeHelperEnabled")){Context device=c.createDeviceProtectedStorageContext();device.moveSharedPreferencesFrom(c,"home_compat");if(!device.getSharedPreferences("home_compat",0).edit().putBoolean("enabled",bundle.getBoolean("homeHelperEnabled")).commit())throw new IOException("Cannot save home helper preference");}
  JSONObject serviceDefaults=bundle.optJSONObject("serviceDefaults");if(serviceDefaults!=null){JSONObject services=AuroraServices.config(c);for(String section:new String[]{"weather","time"}){JSONObject values=serviceDefaults.optJSONObject(section);if(values==null)continue;for(java.util.Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();if(java.util.Arrays.asList("city","lat","lon","key","headers").contains(key))throw new IOException("Device identity in service defaults");if(key.equals("timezone")&&c.getSharedPreferences("desktop_location",0).contains("timezone"))continue;services.getJSONObject(section).put(key,values.get(key));}}if(!c.getSharedPreferences("aurora_services",0).edit().putString("config",services.toString()).commit())throw new IOException("Cannot save service defaults");}
  JSONObject config=bundle.getJSONObject("themeConfig");
  File dir=new File(c.getExternalFilesDir(null),"aurora");dir.mkdirs();File target=new File(dir,"config.json"),part=new File(dir,"config.json.seed");
  try(FileOutputStream out=new FileOutputStream(part)){out.write(config.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
  if(!part.renameTo(target))throw new IOException("Cannot save theme defaults");
  WebAudit.record(c,"127.0.0.1","应用内置桌面模板","保留本机位置与安全设置",200);
 }
}
