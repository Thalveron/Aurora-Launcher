package com.gathin.tvhome;
import android.content.*;import org.json.*;
/** Resolves locally. Explicit city-search metadata overrides compact offline boundary estimates. */
final class CityTimezone {
 private static volatile OfflineTimezone map;
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("aurora_city_zones",0);}
 private static OfflineTimezone map(Context c)throws Exception{if(map==null)synchronized(CityTimezone.class){if(map==null)try(java.io.InputStream in=c.getAssets().open("timezone/world-zones.dat")){map=OfflineTimezone.read(in);}}return map;}
 static void remember(Context c,double lat,double lon,String value){try{String zone=CityTime.zone(value);SharedPreferences p=prefs(c);SharedPreferences.Editor edit=p.edit();if(p.getAll().size()>512)edit.clear();edit.putString(CityTime.key(lat,lon),zone).apply();}catch(Exception ignored){}}
 static String resolve(Context c,JSONObject w)throws Exception{double lat=w.getDouble("lat"),lon=w.getDouble("lon");String key=CityTime.key(lat,lon),zone=prefs(c).getString(key,"");if(!zone.isEmpty())return CityTime.zone(zone);zone=CityTime.zone(map(c).lookup(lat,lon));remember(c,lat,lon,zone);return zone;}
 static boolean apply(Context c,JSONObject config)throws Exception{JSONObject w=config.getJSONObject("weather"),t=config.getJSONObject("time");String zone=resolve(c,w);boolean changed=!zone.equals(w.optString("timezone"))||!zone.equals(t.optString("timezone"));w.put("timezone",zone);t.put("timezone",zone);return changed;}
}
