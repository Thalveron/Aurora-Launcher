package com.gathin.tvhome;
import android.content.Context;
import org.json.*;
import java.util.*;
/** UTF-8 / UTF-16 / GB18030 LRC with timed lines, preserving enhanced word timing tags. */
final class AuroraLyrics {
    private static String cached="";private static JSONArray rows=new JSONArray();
    static synchronized JSONArray read(Context c,String id)throws Exception{
        if(id.isEmpty())return new JSONArray();if(id.equals(cached))return rows;
        byte[] bytes;try(java.io.InputStream in=new java.io.FileInputStream(AuroraLibrary.file(c,id,"lyrics"))){bytes=AuroraThemes.read(in,1024*1024);}
        String text;
        if(bytes.length>=2&&((bytes[0]&255)==255&&(bytes[1]&255)==254||(bytes[0]&255)==254&&(bytes[1]&255)==255))text=new String(bytes,"UTF-16");
        else try{text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(Exception invalid){text=new String(bytes,"GB18030");}
        List<JSONObject> list=new ArrayList<>();java.util.regex.Pattern stamp=java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");long offset=0;java.util.regex.Matcher off=java.util.regex.Pattern.compile("\\[offset:([+-]?\\d+)\\]").matcher(text);if(off.find())offset=Long.parseLong(off.group(1));
        for(String line:text.split("\\r?\\n")){java.util.regex.Matcher m=stamp.matcher(line);String content=line.replaceAll("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?\\]","").trim();while(m.find()){String f=m.group(3);long ms=(Long.parseLong(m.group(1))*60+Long.parseLong(m.group(2)))*1000+(f==null?0:Math.round(Double.parseDouble("0."+f)*1000))+offset;list.add(new JSONObject().put("ms",Math.max(0,ms)).put("text",content));}}
        list.sort((a,b)->Long.compare(a.optLong("ms"),b.optLong("ms")));JSONArray result=new JSONArray();for(JSONObject row:list)result.put(row);cached=id;rows=result;return result;
    }
}
