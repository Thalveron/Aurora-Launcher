package com.gathin.tvhome;
import android.content.*;import android.media.tv.*;import org.json.*;
/** Prefer Android TV input state; TCL legacy fallback explicitly reports unknown. */
final class AuroraInputs {
 static JSONArray list(Context c)throws Exception{
  JSONArray out=new JSONArray();TvInputManager tm=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
  if(tm!=null)for(TvInputInfo info:tm.getTvInputList())if(info.isPassthroughInput()){int state=tm.getInputState(info.getId());out.put(new JSONObject().put("id",info.getId()).put("label",info.loadLabel(c)).put("kind","input").put("status",state==TvInputManager.INPUT_STATE_CONNECTED?"connected":state==TvInputManager.INPUT_STATE_CONNECTED_STANDBY?"standby":"disconnected").put("connected",state!=TvInputManager.INPUT_STATE_DISCONNECTED).put("switchable",true).put("source","android"));}
  if(out.length()==0){out=WebInputs.list(c);for(int i=0;i<out.length();i++)out.getJSONObject(i).put("switchable",out.getJSONObject(i).optString("kind").equals("input"));}
  boolean usb=false;for(int i=0;i<out.length();i++)if(out.getJSONObject(i).optString("kind").equals("usb"))usb=true;
  if(!usb)for(java.io.File root:UsbMusic.volumes())out.put(new JSONObject().put("id","usb:"+root.getName()).put("label","USB 存储 "+root.getName()).put("kind","usb").put("status","mounted").put("connected",true).put("switchable",false).put("free",root.getUsableSpace()).put("total",root.getTotalSpace()));
  for(int i=0;i<out.length();i++){JSONObject item=out.getJSONObject(i);if(item.optString("kind").equals("input")){String name=c.getSharedPreferences("input_devices",0).getString(item.getString("id"),"");item.put("device",name).put("registered",!name.isEmpty());}}
  return out;
 }
 static JSONArray name(Context c,JSONObject body)throws Exception{
  AuroraSettings.only(body,"id","name");String id=body.getString("id"),name=body.getString("name").trim();boolean valid=false;
  JSONArray inputs=list(c);for(int i=0;i<inputs.length();i++){JSONObject item=inputs.getJSONObject(i);if(id.equals(item.getString("id"))&&item.optString("kind").equals("input"))valid=true;}
  if(!valid||name.length()>60||name.matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException("输入源无效或名称超过 60 字");
  android.content.SharedPreferences.Editor editor=c.getSharedPreferences("input_devices",0).edit();if(name.isEmpty())editor.remove(id);else editor.putString(id,name);if(!editor.commit())throw new IllegalStateException("设备名称保存失败");return list(c);
 }
 static void open(Context c,String id)throws Exception{
  if(id.startsWith("EN_TCL_")){WebInputs.open(c,id);return;}
  TvInputManager tm=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);boolean valid=false;if(tm!=null)for(TvInputInfo info:tm.getTvInputList())if(info.getId().equals(id)&&info.isPassthroughInput())valid=true;if(!valid)throw new IllegalArgumentException("输入源不存在");
  c.startActivity(new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 }
}
