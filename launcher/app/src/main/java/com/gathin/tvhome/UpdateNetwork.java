package com.gathin.tvhome;
import java.net.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import org.json.*;
final class UpdateNetwork {
 interface Progress{void bytes(long value,long total);}
 private static final ExecutorService probes=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"aurora-update-network");t.setDaemon(true);return t;});
 static HttpURLConnection open(String url,String range)throws Exception{
  for(int n=0;n<6;n++){
   if(!UpdatePolicy.transport(url))throw new IOException("更新地址或重定向不受信任");
   HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(4000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","Aurora-Launcher-Updater");c.setRequestProperty("Accept-Encoding","identity");if(range!=null)c.setRequestProperty("Range",range);
   int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("重定向缺少地址");url=new URL(new URL(url),location).toString();continue;}
   if(code!=200&&code!=206){c.disconnect();throw new IOException("HTTP "+code);}return c;
  }throw new IOException("更新地址重定向过多");
 }
 static JSONObject manifest()throws Exception{
  java.util.List<Future<JSONObject>> jobs=new ArrayList<>();
  for(String url:UpdatePolicy.manifestSources())jobs.add(probes.submit(()->{
   HttpURLConnection c=open(url,null);try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(b.size()+n>131072)throw new IOException("版本信息过大");b.write(buf,0,n);}JSONObject all=new JSONObject(b.toString("UTF-8"));if(all.getInt("schema")!=1)throw new IOException("不支持的更新协议");JSONObject latest=release(all.getJSONObject("latest"));org.json.JSONArray history=new org.json.JSONArray();org.json.JSONArray source=all.optJSONArray("history");if(source!=null)for(int i=0;i<Math.min(50,source.length());i++)history.put(release(source.getJSONObject(i)));return new JSONObject().put("latest",latest).put("history",history).put("source",url);}finally{c.disconnect();}
  }));
  JSONObject best=null;Exception error=null;
  for(Future<JSONObject> job:jobs)try{JSONObject result=job.get(30,TimeUnit.SECONDS);if(best==null||result.getJSONObject("latest").getLong("versionCode")>best.getJSONObject("latest").getLong("versionCode"))best=result;}catch(Exception e){error=e;}finally{job.cancel(true);}
  if(best==null)throw new IOException("更新服务暂不可用，请稍后检查（尚未发布版本信息或网络不可达）",error);
  return best;
 }
 static JSONObject release(JSONObject input)throws Exception{
  String version=input.getString("version"),url=input.getString("url"),sha=input.getString("sha256");UpdatePolicy.version(version);UpdatePolicy.validateArtifact(url);UpdatePolicy.digest(sha);
  long code=integer(input,"versionCode"),bytes=integer(input,"bytes");int sdk=input.has("minSdk")?(int)integer(input,"minSdk"):26;
  if(code<1||code>Integer.MAX_VALUE||bytes<1024||bytes>512L*1024*1024||sdk<1||sdk>100)throw new IllegalArgumentException("版本信息字段无效");
  return new JSONObject().put("version",version).put("versionCode",code).put("url",url).put("sha256",sha.toLowerCase(Locale.ROOT)).put("bytes",bytes).put("minSdk",sdk).put("notes",input.optString("notes",""));
 }
 private static long integer(JSONObject object,String key)throws Exception{Object value=object.get(key);if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||((Number)value).doubleValue()!=((Number)value).longValue())throw new IllegalArgumentException("版本信息需要整数："+key);return ((Number)value).longValue();}
 static JSONArray rank(String original)throws Exception{
  String[] urls=UpdatePolicy.downloadSources(original);List<Future<JSONObject>> jobs=new ArrayList<>();
  for(int i=0;i<urls.length;i++){final String url=urls[i],name=i==0?"GitHub 原地址":"GitHub 加速";jobs.add(probes.submit(()->{
   long start=System.nanoTime();HttpURLConnection c=null;JSONObject row=new JSONObject().put("name",name).put("url",url);
   try{c=open(url,"bytes=0-65535");try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int total=0,n;while(total<65536&&(n=in.read(b,0,Math.min(b.length,65536-total)))!=-1){for(int k=0;k<n&&total+k<2;k++)if(b[k]!=(total+k==0?'P':'K'))throw new IOException("线路没有返回 APK");total+=n;}if(total<1024)throw new IOException("线路返回文件不完整");return row.put("ok",true).put("ms",Math.max(1,(System.nanoTime()-start)/1_000_000)).put("sampleBytes",total);}}
   catch(Exception e){return row.put("ok",false).put("reason",String.valueOf(e.getMessage()));}finally{if(c!=null)c.disconnect();}
  }));}
  List<JSONObject> rows=new ArrayList<>();for(int i=0;i<jobs.size();i++)try{rows.add(jobs.get(i).get(25,TimeUnit.SECONDS));}catch(Exception e){rows.add(new JSONObject().put("url",urls[i]).put("ok",false).put("reason","测速超时"));}finally{jobs.get(i).cancel(true);}
  rows.sort((a,b)->Long.compare(a.optBoolean("ok")?a.optLong("ms"):Long.MAX_VALUE,b.optBoolean("ok")?b.optLong("ms"):Long.MAX_VALUE));return new JSONArray(rows);
 }
 static String download(JSONObject release,File target,JSONArray ranked,Progress progress)throws Exception{
  Exception error=null;
  for(int i=0;i<ranked.length();i++){JSONObject route=ranked.getJSONObject(i);HttpURLConnection c=null;File part=new File(target.getPath()+".part");
   try{c=open(route.getString("url"),null);long expected=release.getLong("bytes"),done=0;
    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){done+=n;if(done>expected)throw new IOException("下载大小超过声明值");out.write(b,0,n);progress.bytes(done,expected);}}
    if(done!=expected||!UpdatePolicy.sha(part).equals(release.getString("sha256")))throw new IOException("更新包大小或 SHA-256 校验失败");
    if(target.exists()&&!target.delete())throw new IOException("无法替换下载文件");if(!part.renameTo(target))throw new IOException("下载文件保存失败");return route.optString("name");
   }catch(Exception e){error=e;part.delete();}finally{if(c!=null)c.disconnect();}
  }throw new IOException("全部更新线路下载失败",error);
 }
}
