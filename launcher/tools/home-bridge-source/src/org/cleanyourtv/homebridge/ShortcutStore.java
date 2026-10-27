package org.cleanyourtv.homebridge;
import android.content.*;import android.content.pm.*;import org.json.*;
final class ShortcutStore{
 static JSONArray records(Context c){try{return new JSONArray(c.getSharedPreferences("shortcuts",0).getString("items","[]"));}catch(Exception e){return new JSONArray();}}
 static void save(Context c,ShortcutInfo s)throws Exception{String key=java.util.UUID.nameUUIDFromBytes((s.getPackage()+"|"+s.getId()).getBytes("UTF-8")).toString().replace("-","");JSONArray list=records(c),next=new JSONArray();for(int i=0;i<list.length();i++)if(!key.equals(list.getJSONObject(i).getString("key")))next.put(list.get(i));if(next.length()>=100)throw new IllegalArgumentException("最多100项");next.put(new JSONObject().put("key",key).put("package",s.getPackage()).put("id",s.getId()).put("label",String.valueOf(s.getShortLabel())));c.getSharedPreferences("shortcuts",0).edit().putString("items",next.toString()).apply();}
}
