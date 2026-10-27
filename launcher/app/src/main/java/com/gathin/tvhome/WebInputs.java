package com.gathin.tvhome;
import android.content.*;import java.io.*;import org.json.*;
final class WebInputs{
 static boolean legacySupported(Context c){try{c.getPackageManager().getActivityInfo(new ComponentName("com.tcl.tv","com.tcl.tv.TVActivity"),0);return true;}catch(Exception unavailable){return false;}}
 static boolean supported(Context c){android.media.tv.TvInputManager tm=(android.media.tv.TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);if(tm!=null)for(android.media.tv.TvInputInfo input:tm.getTvInputList())if(input.isPassthroughInput())return true;return legacySupported(c);}

 private static String command(Context c)throws Exception{File file=new File(WebApps.staging(c),"tcl-inputs.jar");try(InputStream in=c.getAssets().open("tcl-inputs.jar");OutputStream out=new FileOutputStream(file)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return "CLASSPATH="+file.getPath()+" app_process /system/bin TclInputs";}
 private static String valid(String id){if(!id.matches("EN_TCL_(HDMI[1-4]|AV[12])"))throw new IllegalArgumentException("输入源无效");return id;}
 static void name(Context c,String id,String name){valid(id);if(name.length()>60||name.matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException("设备名称限60字");android.content.SharedPreferences.Editor e=c.getSharedPreferences("input_devices",0).edit();if(name.trim().isEmpty())e.remove(id);else e.putString(id,name.trim());if(!e.commit())throw new IllegalStateException("设备名称保存失败");}
 static synchronized JSONArray list(Context c)throws Exception{
  JSONArray result;String error="";
  try{if(!legacySupported(c))throw new IOException("No legacy input API");String s=TvAdb.shell(c,command(c));int start=s.indexOf("TV_INPUT_JSON:");if(start<0)throw new IOException("电视没有返回输入源状态");result=new JSONArray(s.substring(start+14).split("[\\r\\n]",2)[0]);}
  catch(Exception e){error="输入源状态暂不可读取，请检查电视调试连接";result=new JSONArray();if(legacySupported(c))for(String id:new String[]{"EN_TCL_HDMI1","EN_TCL_HDMI2","EN_TCL_HDMI3","EN_TCL_HDMI4","EN_TCL_AV1","EN_TCL_AV2"})result.put(new JSONObject().put("id",id).put("label",id.replace("EN_TCL_","")).put("connected",false).put("active",false));}
  for(int i=0;i<result.length();i++){JSONObject item=result.getJSONObject(i);String name=c.getSharedPreferences("input_devices",0).getString(item.getString("id"),"");item.put("kind","input").put("device",name).put("registered",!name.isEmpty()).put("status",item.optBoolean("connected")?"connected":"unknown").put("note",error.isEmpty()?"此机型的旧接口无法可靠报告HDMI插拔状态；状态未知不代表未连接。":error);}
  android.os.storage.StorageManager sm=(android.os.storage.StorageManager)c.getSystemService(Context.STORAGE_SERVICE);
  for(File root:UsbMusic.volumes()){String label="USB 存储",uuid=root.getName();try{for(android.os.storage.StorageVolume v:sm.getStorageVolumes())if(uuid.equalsIgnoreCase(v.getUuid())){label=v.getDescription(c);break;}}catch(Exception ignored){}result.put(new JSONObject().put("id","usb:"+uuid).put("label",label).put("kind","usb").put("connected",true).put("status","mounted").put("active",false).put("registered",false).put("uuid",uuid).put("free",root.getUsableSpace()).put("total",root.getTotalSpace()));}
  return result;
 }
 static synchronized void open(Context c,String id)throws Exception{valid(id);String s=TvAdb.shell(c,command(c)+" "+id);if(!s.contains("TV_INPUT_OK"))throw new IOException("切换输入源失败");c.startActivity(new Intent("android.intent.action.TV").setComponent(new ComponentName("com.tcl.tv","com.tcl.tv.TVActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
}
