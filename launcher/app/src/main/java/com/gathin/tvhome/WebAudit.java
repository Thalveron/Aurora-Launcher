package com.gathin.tvhome;
import android.content.Context;import org.json.*;import java.io.*;import java.util.*;
final class WebAudit{
 private static final Map<String,Long> views=new LinkedHashMap<>();
 static synchronized boolean view(String ip,String action){String key=ip+action;long now=System.currentTimeMillis();Long old=views.put(key,now);if(views.size()>512)views.clear();return old==null||now-old>60000;}
 private static final Map<String,Long> sessions=new LinkedHashMap<>();
 static synchronized void login(Context c,String ip,boolean ok){long now=System.currentTimeMillis();Long previous=sessions.get(ip);if(!ok||previous==null||now-previous>30*60*1000L)record(c,ip,ok?"登录成功":"登录失败","",ok?200:401);if(ok){if(sessions.size()>128)sessions.clear();sessions.put(ip,now);}}
 static synchronized void record(Context c,String ip,String action,String detail,int status){try{File f=new File(c.getFilesDir(),"web-audit.jsonl");if(f.length()>5*1024*1024){File old=new File(c.getFilesDir(),"web-audit.previous.jsonl");if(old.exists())old.delete();f.renameTo(old);}JSONObject row=new JSONObject().put("time",System.currentTimeMillis()).put("ip",ip).put("action",action).put("detail",detail.substring(0,Math.min(detail.length(),2000))).put("status",status);try(FileOutputStream out=new FileOutputStream(f,true)){out.write((row+"\n").getBytes("UTF-8"));}}catch(Exception ignored){}}
 static synchronized JSONArray list(Context c){ArrayDeque<JSONObject> rows=new ArrayDeque<>();for(String name:new String[]{"web-audit.previous.jsonl","web-audit.jsonl"})try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(new File(c.getFilesDir(),name)),"UTF-8"))){String line;while((line=r.readLine())!=null){try{rows.add(new JSONObject(line));if(rows.size()>500)rows.removeFirst();}catch(Exception ignored){}}}catch(IOException ignored){}JSONArray result=new JSONArray();Iterator<JSONObject> i=rows.descendingIterator();while(i.hasNext())result.put(i.next());return result;}
 static synchronized byte[] export(Context c)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(String name:new String[]{"web-audit.previous.jsonl","web-audit.jsonl"}){File f=new File(c.getFilesDir(),name);if(!f.isFile())continue;try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}return out.toByteArray();}
 static synchronized void clear(Context c,boolean confirmed,String ip)throws IOException{
  if(!confirmed)throw new IllegalArgumentException("请先确认清空日志");
  for(String name:new String[]{"web-audit.previous.jsonl","web-audit.jsonl"}){File f=new File(c.getFilesDir(),name);if(f.exists()&&!f.delete())throw new IOException("无法清空日志文件");}
  views.clear();sessions.clear();record(c,ip,"清空操作日志","用户确认清空历史日志",200);
 }
 static String safe(JSONObject body){JSONObject clean=new JSONObject();try{for(Iterator<String> it=body.keys();it.hasNext();){String k=it.next();Object v=body.get(k);if(k.toLowerCase(Locale.ROOT).matches(".*(password|token|authorization|secret).*"))v="[已隐藏]";else if(v instanceof JSONObject)v=new JSONObject(safe((JSONObject)v));else if(v instanceof JSONArray&&((JSONArray)v).length()>50)v="共"+((JSONArray)v).length()+"项";clean.put(k,v);}}catch(Exception ignored){}return clean.toString();}
}
