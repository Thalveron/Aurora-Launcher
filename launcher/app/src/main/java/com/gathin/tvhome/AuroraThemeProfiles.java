package com.gathin.tvhome;
import android.content.Context;import org.json.*;
/** Independent snapshots; the old shared appearance remains as the migration seed. */
final class AuroraThemeProfiles {
 static boolean shared(String k){return k.startsWith("saver")||k.startsWith("greeting");}
 private static JSONObject copy(JSONObject v)throws Exception{return v==null?new JSONObject():new JSONObject(v.toString());}
 private static JSONObject ensure(Context c,JSONObject config,String id)throws Exception{
  JSONObject profiles=config.optJSONObject("themeProfiles");if(profiles==null){profiles=new JSONObject();config.put("themeProfiles",profiles);}
  JSONObject entry=profiles.optJSONObject(id);if(entry!=null)return entry;
  JSONObject manifest=AuroraThemes.manifest(c,id),template=profiles.optJSONObject(manifest.optString("base",id));
  JSONObject appearance=copy(template==null?config.optJSONObject("appearance"):template.optJSONObject("appearance"));
  JSONObject layout=manifest.optJSONObject("defaultLayout");if(layout!=null&&(template==null||appearance.optJSONObject("layout")==null))appearance.put("layout",copy(layout));
  JSONArray presets=template==null?config.optJSONArray("presets"):template.optJSONArray("presets");
  entry=new JSONObject().put("appearance",appearance).put("presets",presets==null?new JSONArray():new JSONArray(presets.toString()));profiles.put(id,entry);return entry;
 }
 static JSONObject read(Context c,String id,String field)throws Exception{synchronized(AuroraThemes.class){JSONObject config=AuroraThemes.config(c);boolean missing=config.optJSONObject("themeProfiles")==null||!config.getJSONObject("themeProfiles").has(id);JSONObject entry=ensure(c,config,id);boolean changed=missing;JSONObject common=config.optJSONObject("sharedSaver");if(common==null){common=new JSONObject();JSONObject source=ensure(c,config,AuroraThemes.current(c)).optJSONObject("appearance");if(source!=null)for(java.util.Iterator<String> i=source.keys();i.hasNext();){String k=i.next();if(shared(k))common.put(k,source.get(k));}config.put("sharedSaver",common);changed=true;}if(changed)AuroraThemes.save(c,config);if(!field.equals("appearance"))return new JSONObject().put("presets",new JSONArray(entry.getJSONArray("presets").toString()));JSONObject result=copy(entry.optJSONObject(field));for(java.util.Iterator<String> i=common.keys();i.hasNext();){String k=i.next();result.put(k,common.get(k));}return result;}}
 static void write(Context c,String id,String field,Object value)throws Exception{synchronized(AuroraThemes.class){if(!AuroraThemes.current(c).equals(id))throw new IllegalStateException("主题已切换，请刷新后设置");JSONObject config=AuroraThemes.config(c);if(field.equals("appearance")){JSONObject personal=copy((JSONObject)value),common=config.optJSONObject("sharedSaver");if(common==null)common=new JSONObject();for(java.util.Iterator<String> i=((JSONObject)value).keys();i.hasNext();){String k=i.next();if(shared(k)){common.put(k,((JSONObject)value).get(k));personal.remove(k);}}config.put("sharedSaver",common);value=personal;}ensure(c,config,id).put(field,value);AuroraThemes.save(c,config);}}
}
