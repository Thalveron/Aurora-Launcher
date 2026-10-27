package com.gathin.tvhome;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import org.json.*;
/** Resolve only an exact publisher tag and its attached checksum; never arbitrary update URLs. */
final class SpecifiedRelease {
 static String normalize(String version){String value=version==null?"":version.trim();if(value.startsWith("v"))value=value.substring(1);UpdatePolicy.version(value);return value;}
 static JSONObject fetch(String input)throws Exception{
  String version=normalize(input),tag="v"+version;
  try{JSONObject metadata=UpdateNetwork.manifest();JSONObject latest=metadata.getJSONObject("latest");if(version.equals(latest.getString("version")))return latest;JSONArray history=metadata.optJSONArray("history");if(history!=null)for(int i=0;i<history.length();i++)if(version.equals(history.getJSONObject(i).getString("version")))return history.getJSONObject(i);}catch(Exception unavailable){/* Exact public release is the fallback. */}

  JSONObject release=new JSONObject(text("https://api.github.com/repos/"+UpdatePolicy.REPO+"/releases/tags/"+tag,1024*1024));
  if(release.optBoolean("draft")||!tag.equals(release.getString("tag_name")))throw new IOException("指定版本未公开发布");
  JSONArray assets=release.getJSONArray("assets");JSONObject apk=null,sum=null;
  String name="Aurora-Launcher-v"+version+".apk";
  for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);if(name.equals(asset.optString("name")))apk=asset;if("SHA256SUMS.txt".equals(asset.optString("name")))sum=asset;}
  if(apk==null)throw new IOException("该版本没有标准安装包");String url=apk.getString("browser_download_url");UpdatePolicy.validateArtifact(url);
  String hash=apk.optString("digest","");if(hash.startsWith("sha256:"))hash=hash.substring(7);else hash="";
  if(!hash.matches("[a-fA-F0-9]{64}")){
   if(sum==null)throw new IOException("指定版本缺少校验文件");
   String expected="https://github.com/"+UpdatePolicy.REPO+"/releases/download/"+tag+"/SHA256SUMS.txt";
   if(!expected.equals(sum.getString("browser_download_url")))throw new IOException("校验文件地址不匹配");
   for(String line:text(expected,65536).split("\\r?\\n")){String[] parts=line.trim().split("\\s+",2);if(parts.length==2&&parts[1].replaceFirst("^\\*","").equals(name)){hash=parts[0];break;}}
  }
  UpdatePolicy.digest(hash);long bytes=apk.getLong("size");if(bytes<1024||bytes>512L*1024*1024)throw new IOException("安装包大小无效");
  return new JSONObject().put("version",version).put("versionCode",-1).put("url",url).put("sha256",hash.toLowerCase(java.util.Locale.ROOT)).put("bytes",bytes).put("minSdk",26);
 }
 private static String text(String url,int maximum)throws Exception{
  HttpURLConnection connection=UpdateNetwork.open(url,null);try(InputStream in=connection.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>maximum)throw new IOException("版本信息过大");out.write(buffer,0,n);}return out.toString(StandardCharsets.UTF_8.name());}finally{connection.disconnect();}
 }
}
