package com.gathin.tvhome;
import android.content.*;import android.media.tv.*;import org.json.*;
/** Prefer Android TV input state; TCL legacy fallback explicitly reports unknown. */
final class AuroraInputs {
 static JSONArray list(Context c)throws Exception{
  JSONArray out=new JSONArray();TvInputManager tm=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
  if(tm!=null)for(TvInputInfo info:tm.getTvInputList())if(info.isPassthroughInput()){int state=tm.getInputState(info.getId());out.put(new JSONObject().put("id",info.getId()).put("label",info.loadLabel(c)).put("kind","input").put("status",state==TvInputManager.INPUT_STATE_CONNECTED?"connected":state==TvInputManager.INPUT_STATE_CONNECTED_STANDBY?"standby":"disconnected").put("connected",state!=TvInputManager.INPUT_STATE_DISCONNECTED).put("switchable",true).put("source","android"));}
  if(out.length()==0){out=WebInputs.list(c);for(int i=0;i<out.length();i++)out.getJSONObject(i).put("switchable",out.getJSONObject(i).optString("kind").equals("input"));}
  return out;
 }
 static void open(Context c,String id)throws Exception{
  if(id.startsWith("EN_TCL_")){WebInputs.open(c,id);return;}
  TvInputManager tm=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);boolean valid=false;if(tm!=null)for(TvInputInfo info:tm.getTvInputList())if(info.getId().equals(id)&&info.isPassthroughInput())valid=true;if(!valid)throw new IllegalArgumentException("输入源不存在");
  c.startActivity(new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 }
}
