package com.gathin.tvhome;
import android.content.*;import android.net.Uri;import android.os.Bundle;import org.json.*;
/** Settings bridge to the companion music app. The package name is the protocol id; the label is read from the installed app. */
final class AuroraMusicSettings{
 static String appName(Context c){try{return String.valueOf(c.getPackageManager().getApplicationInfo("com.craftworks.music.tvcustom",0).loadLabel(c.getPackageManager()));}catch(Exception ignored){return "Aurora Music";}}
 static JSONObject call(Context c,String method,JSONObject value)throws Exception{Bundle args=new Bundle();if(value!=null)args.putString("json",value.toString());try{Bundle result=c.getContentResolver().call(Uri.parse("content://com.craftworks.music.tvcustom.localsettings"),method,null,args);if(result==null)throw new IllegalStateException();return new JSONObject(result.getString("json","{}")).put("available",true).put("appName",appName(c));}catch(IllegalArgumentException missing){if(method.equals("get")){String name=appName(c);return new JSONObject().put("available",false).put("appName",name).put("reason","请先安装支持 U 盘音乐的新版本 "+name);}throw missing;}}
}
