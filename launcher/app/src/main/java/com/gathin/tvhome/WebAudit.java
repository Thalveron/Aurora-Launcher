package com.gathin.tvhome;
import android.content.Context;import org.json.*;import java.io.*;import java.util.*;
final class WebAudit{
 private static final Map<String,Long> views=new LinkedHashMap<>();
 static synchronized boolean view(String ip,String action){String key=ip+action;long now=System.currentTimeMillis();Long old=views.put(key,now);if(views.size()>512)views.clear();return old==null||now-old>60000;}
 private static final Map<String,Long> sessions=new LinkedHashMap<>();
 static synchronized void login(Context c,String ip,boolean ok){long now=System.currentTimeMillis();Long previous=sessions.get(ip);if(!ok||previous==null||now-previous>30*60*1000L)record(c,ip,ok?"登录成功":"登录失败","",ok?200:401);if(ok){if(sessions.size()>128)sessions.clear();sessions.put(ip,now);}}
 private static long prunedAt;
 private static File archives(Context c){return new File(c.getFilesDir(),"web-audit-archive");}
 private static List<File> files(Context c){List<File> result=new ArrayList<>();result.add(new File(c.getFilesDir(),"web-audit.previous.jsonl"));File[] archived=archives(c).listFiles((d,n)->n.matches("segment-[0-9]+-[a-f0-9-]+\\.jsonl"));if(archived!=null){Arrays.sort(archived,Comparator.comparing(File::getName));Collections.addAll(result,archived);}result.add(new File(c.getFilesDir(),"web-audit.jsonl"));return result;}
 static synchronized JSONObject settings(Context c)throws Exception{return new JSONObject().put("retentionDays",c.getSharedPreferences("web_audit",0).getInt("retentionDays",0));}
 static synchronized JSONObject configure(Context c,JSONObject body)throws Exception{AuroraSettings.only(body,"retentionDays");Object value=body.get("retentionDays");if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue())throw new IllegalArgumentException("保存期限需为整数");int days=AuditRetention.days(((Number)value).intValue());if(!c.getSharedPreferences("web_audit",0).edit().putInt("retentionDays",days).commit())throw new IOException("日志保存期限写入失败");prunedAt=0;prune(c);return settings(c);}
 private static synchronized void prune(Context c)throws IOException{
  long now=System.currentTimeMillis();if(now-prunedAt<3600000)return;int days=c.getSharedPreferences("web_audit",0).getInt("retentionDays",0);if(days==0){prunedAt=now;return;}long cutoff=AuditRetention.cutoff(days,now);
  for(File f:files(c)){if(!f.isFile())continue;File next=new File(f.getPath()+".next");boolean changed=false;int kept=0;
   try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));FileOutputStream out=new FileOutputStream(next)){
    String line;while((line=in.readLine())!=null){boolean expired=false;try{JSONObject row=new JSONObject(line);expired=row.has("time")&&row.getLong("time")<cutoff;}catch(Exception invalid){}if(expired){changed=true;continue;}out.write((line+"\n").getBytes("UTF-8"));kept++;}out.getFD().sync();
   }catch(IOException failure){next.delete();throw failure;}
   if(changed){if(kept==0){if(!f.delete())throw new IOException("过期日志清理失败");next.delete();}else if(!next.renameTo(f))throw new IOException("过期日志保存失败");}else next.delete();
  }prunedAt=now;
 }
 static synchronized void record(Context c,String ip,String action,String detail,int status){try{prune(c);File f=new File(c.getFilesDir(),"web-audit.jsonl");if(f.length()>5*1024*1024){File dir=archives(c);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("日志目录创建失败");File old=new File(dir,"segment-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".jsonl");if(!f.renameTo(old))throw new IOException("日志归档失败");}JSONObject row=new JSONObject().put("time",System.currentTimeMillis()).put("ip",ip).put("action",action).put("detail",detail.substring(0,Math.min(detail.length(),2000))).put("status",status);try(FileOutputStream out=new FileOutputStream(f,true)){out.write((row+"\n").getBytes("UTF-8"));}}catch(Exception ignored){} }
 static synchronized JSONArray list(Context c)throws IOException{prune(c);ArrayDeque<JSONObject> rows=new ArrayDeque<>();for(File f:files(c))try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"))){String line;while((line=r.readLine())!=null){try{rows.add(new JSONObject(line));if(rows.size()>500)rows.removeFirst();}catch(Exception ignored){}}}catch(FileNotFoundException missing){if(f.exists())throw new IOException("无法读取操作日志",missing);}JSONArray result=new JSONArray();Iterator<JSONObject> i=rows.descendingIterator();while(i.hasNext())result.put(i.next());return result;}
 static synchronized void exportTo(Context c,OutputStream out)throws IOException{prune(c);for(File f:files(c)){if(!f.isFile())continue;try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}}
 static synchronized void clear(Context c,boolean confirmed,String ip)throws IOException{
  if(!confirmed)throw new IllegalArgumentException("请先确认清空日志");
  for(File f:files(c))if(f.exists()&&!f.delete())throw new IOException("无法清空日志文件");
  views.clear();sessions.clear();record(c,ip,"清空操作日志","用户确认清空历史日志",200);
 }
 static String safe(JSONObject body){JSONObject clean=new JSONObject();try{for(Iterator<String> it=body.keys();it.hasNext();){String k=it.next();Object v=body.get(k);if(k.toLowerCase(Locale.ROOT).matches(".*(password|token|authorization|secret).*"))v="[已隐藏]";else if(v instanceof JSONObject)v=new JSONObject(safe((JSONObject)v));else if(v instanceof JSONArray&&((JSONArray)v).length()>50)v="共"+((JSONArray)v).length()+"项";clean.put(k,v);}}catch(Exception ignored){}return clean.toString();}
}
