package com.gathin.tvhome;
import android.content.*;import android.net.Uri;import android.os.Bundle;import org.json.*;
final class AuroraMusicSettings{
 static JSONObject call(Context c,String method,JSONObject value)throws Exception{Bundle args=new Bundle();if(value!=null)args.putString("json",value.toString());try{Bundle result=c.getContentResolver().call(Uri.parse("content://com.craftworks.music.tvcustom.localsettings"),method,null,args);if(result==null)throw new IllegalStateException();return new JSONObject(result.getString("json","{}")).put("available",true);}catch(IllegalArgumentException missing){if(method.equals("get"))return new JSONObject().put("available",false).put("reason","请先安装支持U盘音乐的新版本Chora");throw missing;}}
}
