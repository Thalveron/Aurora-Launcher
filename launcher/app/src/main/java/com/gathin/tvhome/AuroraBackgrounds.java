package com.gathin.tvhome;
import android.content.*;import java.io.*;import java.util.*;import org.json.*;
/** One background selection and playlist for both native and WebView themes. */
final class AuroraBackgrounds {
 static boolean active(Context c){return c.getSharedPreferences("background",0).getBoolean("unified_library",false);}
 static List<File> files(Context c,boolean saver){List<File> files=new ArrayList<>();try{JSONObject a=AuroraSettings.stored(c);JSONArray ids=a.getJSONArray(saver?"saverSlides":"desktopSlides");for(int i=0;i<ids.length();i++)try{files.add(AuroraLibrary.file(c,ids.getString(i),"background"));}catch(Exception ignored){}}catch(Exception ignored){}return files;}
 static int seconds(Context c,boolean saver){try{return AuroraSettings.stored(c).getInt(saver?"saverSlideSeconds":"slideSeconds");}catch(Exception e){return 20;}}
 static void changed(Context c){c.getSharedPreferences("background",0).edit().putBoolean("unified_library",true).putLong("revision",System.currentTimeMillis()).commit();if(AuroraActivity.visible)AuroraActivity.nativeSaver(AuroraSaver.active);c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()));}
}
