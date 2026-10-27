package com.gathin.tvhome;
import android.content.*;
final class LocationSettings {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("desktop_location",0);}
 static String city(Context c){try{return AuroraServices.config(c).getJSONObject("weather").getString("city");}catch(Exception e){return "上海";}}
 static String zone(Context c){return AuroraServices.zone(c);}
}
