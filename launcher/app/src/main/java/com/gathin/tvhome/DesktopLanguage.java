package com.gathin.tvhome;
import android.app.*;import android.content.*;import android.content.res.Configuration;import android.os.LocaleList;import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;
final class DesktopLanguage {
 private static JSONObject words;
 static String code(Context c){return c.getSharedPreferences("desktop_language",0).getString("locale","zh-CN");}
 static boolean english(Context c){return code(c).equals("en-US");}
 static Context wrap(Context c){Configuration cfg=new Configuration(c.getResources().getConfiguration());cfg.setLocales(new LocaleList(Locale.forLanguageTag(code(c))));cfg.setLayoutDirection(Locale.forLanguageTag(code(c)));return c.createConfigurationContext(cfg);}
 static void set(Context c,String value){if(!value.equals("zh-CN")&&!value.equals("en-US"))throw new IllegalArgumentException("Invalid desktop language");if(value.equals(code(c)))return;c.getSharedPreferences("desktop_language",0).edit().putString("locale",value).commit();c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()).putExtra("kind","settings-language"));}
 static String t(Context c,String value){if(!english(c))return value;try{if(words==null)synchronized(DesktopLanguage.class){if(words==null)try(java.io.InputStream in=c.getAssets().open("aurora/core/strings-en.json")){words=new JSONObject(new String(AuroraThemes.read(in,1024*1024),StandardCharsets.UTF_8));}}return words.optString(value,value);}catch(Exception e){return value;}}
 static void choose(Activity a){new AlertDialog.Builder(a).setTitle("语言 / Language").setSingleChoiceItems(new String[]{"简体中文","English"},english(a)?1:0,(dialog,index)->{set(a,index==1?"en-US":"zh-CN");dialog.dismiss();a.recreate();}).setNegativeButton(android.R.string.cancel,null).show();}
 static String city(Context c){String city=LocationSettings.city(c);if(english(c)&&city.equals("耒阳"))return "Leiyang";if(english(c)&&city.equals("上海"))return "Shanghai";return city;}
 static String lunar(Context c){java.time.LocalDate date=CityTime.date(AuroraServices.now(),LocationSettings.zone(c));if(!english(c))return LunarDate.on(date);android.icu.util.ChineseCalendar l=LunarDate.calendar(date);String festival=ChineseFestivals.on(date);for(String part:festival.split(" · "))festival=festival.replace(part,t(c,part));return "Lunar "+(l.get(android.icu.util.Calendar.MONTH)+1)+"/"+l.get(android.icu.util.Calendar.DAY_OF_MONTH)+(l.get(android.icu.util.Calendar.IS_LEAP_MONTH)==1?" (leap)":"")+(festival.isEmpty()?"":" · "+festival);}
}
