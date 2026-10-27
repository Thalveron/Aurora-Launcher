package com.gathin.tvhome;
import android.content.*;import android.content.pm.ResolveInfo;import android.os.*;import android.speech.tts.*;import org.json.*;import java.util.*;
/** Android TTS only; no microphone, server key or audio-stream volume changes. */
final class AuroraVoice{
 static volatile String utterance="idle",utteranceId="",utteranceError="";static TextToSpeech tts;static boolean ready;static String engine="",reason="正在检测语音引擎";static int revision;static long lastAttempt;
 static JSONObject config(Context c)throws Exception{return new JSONObject(c.getSharedPreferences("aurora_voice",0).getString("config","{\"enabled\":false,\"language\":\"en-US\",\"engine\":\"\",\"voice\":\"\",\"rate\":1,\"pitch\":1,\"volume\":0.2,\"readFocus\":true}"));}
 static JSONArray engines(Context c)throws Exception{JSONArray out=new JSONArray();for(ResolveInfo info:c.getPackageManager().queryIntentServices(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),0))out.put(new JSONObject().put("id",info.serviceInfo.packageName).put("name",info.loadLabel(c.getPackageManager())));return out;}
 static void ensure(Context c)throws Exception{
  JSONObject cfg=config(c);JSONArray available=engines(c);String wanted=cfg.optString("engine");if(wanted.isEmpty()&&available.length()>0)wanted=available.getJSONObject(0).getString("id");
  if(wanted.isEmpty()){ready=false;reason="电视没有可用的朗读引擎，请安装离线TTS引擎";return;}
  if(tts!=null&&engine.equals(wanted)&&(ready||android.os.SystemClock.elapsedRealtime()-lastAttempt<5000))return;lastAttempt=android.os.SystemClock.elapsedRealtime();
  if(tts!=null)tts.shutdown();ready=false;engine=wanted;reason="正在初始化语音引擎";int generation=++revision;
  tts=new TextToSpeech(c.getApplicationContext(),status->{if(generation!=revision)return;ready=status==TextToSpeech.SUCCESS;reason=ready?"":"语音引擎初始化失败";},wanted);tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){if(id.equals(utteranceId))utterance="speaking";}public void onDone(String id){if(id.equals(utteranceId))utterance="done";}public void onError(String id){if(id.equals(utteranceId)){utterance="error";utteranceError="synthesis_failed";}}});

 }
 static JSONObject state(Context c)throws Exception{return AuroraBgm.onMain(c,()->{ensure(c);JSONArray voices=new JSONArray();if(ready&&tts.getVoices()!=null)for(Voice v:tts.getVoices())if(!v.isNetworkConnectionRequired()&&Arrays.asList("en","zh","cmn").contains(v.getLocale().getLanguage()))voices.put(new JSONObject().put("id",v.getName()).put("language",v.getLocale().toLanguageTag()).put("quality",v.getQuality()).put("offline",true));
  return new JSONObject().put("config",config(c)).put("engines",engines(c)).put("voices",voices).put("ready",ready).put("reason",reason).put("utterance",new JSONObject().put("state",utterance).put("id",utteranceId).put("error",utteranceError));});}
 static JSONObject save(Context c,JSONObject patch)throws Exception{return AuroraBgm.onMain(c,()->{
  AuroraSettings.only(patch,"enabled","language","engine","voice","rate","pitch","volume","readFocus");JSONObject value=config(c);boolean wasEnabled=value.getBoolean("enabled");for(Iterator<String> i=patch.keys();i.hasNext();){String k=i.next();Object v=patch.get(k);if(k.equals("enabled")||k.equals("readFocus")){if(!(v instanceof Boolean))throw new IllegalArgumentException("需要布尔值");}else if(k.equals("language"))AuroraSettings.choice(v,"zh-CN","en-US","en-GB");else if(k.equals("rate")||k.equals("pitch"))AuroraSettings.range(((Number)v).doubleValue(),.5,2);else if(k.equals("volume"))AuroraSettings.range(((Number)v).doubleValue(),0,1);else if(k.equals("engine")){boolean known=v.toString().isEmpty();JSONArray all=engines(c);for(int n=0;n<all.length();n++)if(all.getJSONObject(n).getString("id").equals(v.toString()))known=true;if(!known)throw new IllegalArgumentException("语音引擎不存在");}else if(k.equals("voice")&&v.toString().length()>200)throw new IllegalArgumentException("声音编号过长");value.put(k,v);}
  if(!value.getString("engine").equals(config(c).getString("engine"))&& !patch.has("voice"))value.put("voice","");
  if(!c.getSharedPreferences("aurora_voice",0).edit().putString("config",value.toString()).commit())throw new IllegalStateException("语音设置保存失败");ensure(c);if(wasEnabled&&!value.getBoolean("enabled")&&tts!=null)tts.stop();return state(c);
 });}
 static JSONObject speak(Context c,JSONObject body,boolean test)throws Exception{return AuroraBgm.onMain(c,()->{
  AuroraSettings.only(body,"text");String text=body.getString("text");if(text.trim().isEmpty()||text.length()>500)throw new IllegalArgumentException("朗读内容限1～500字");ensure(c);JSONObject cfg=config(c);if(!test&&!cfg.getBoolean("enabled"))return new JSONObject().put("accepted",false).put("reason","disabled");
  if(!ready)throw new UnsupportedOperationException(reason);int available=tts.setLanguage(Locale.forLanguageTag(cfg.getString("language")));if(available<0)throw new UnsupportedOperationException("当前引擎没有所选语言数据");
  String selected=cfg.getString("voice");if(!selected.isEmpty()){Voice chosen=null;for(Voice v:tts.getVoices())if(v.getName().equals(selected)&&!v.isNetworkConnectionRequired())chosen=v;if(chosen==null)throw new IllegalArgumentException("所选离线声音不可用，请重新选择");tts.setVoice(chosen);}
  tts.setSpeechRate((float)cfg.getDouble("rate"));tts.setPitch((float)cfg.getDouble("pitch"));Bundle args=new Bundle();args.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,(float)cfg.getDouble("volume"));utteranceId=UUID.randomUUID().toString();utterance="queued";utteranceError="";int status=tts.speak(text,TextToSpeech.QUEUE_FLUSH,args,utteranceId);if(status!=TextToSpeech.SUCCESS)throw new IllegalStateException("朗读请求失败");return new JSONObject().put("accepted",true);
 });}
 static JSONObject stop(Context c)throws Exception{return AuroraBgm.onMain(c,()->{if(tts!=null)tts.stop();utterance="stopped";return new JSONObject().put("stopped",true);});}
}
