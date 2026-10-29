package com.gathin.tvhome;
import android.content.Context;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** One canonical LAN namespace; validation runs before routing and authentication. */
final class ConsolePath {
 private static final ThreadLocal<String> scope=new ThreadLocal<>();
 static String base(Context c){return ConsoleSettings.base(c);}
 static void clear(){scope.remove();}
 static String captureScope(){return scope.get();}
 static void restoreScope(String value){if(value==null)scope.remove();else scope.set(value);}
 static String text(String value){String base=scope.get();if(base==null)return value;java.util.regex.Matcher m=java.util.regex.Pattern.compile("([\\\"'(])/(api/v1|console|core|themes)/").matcher(value);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(m.group(1)+base+m.group(2)+"/"));m.appendTail(out);return out.toString();}
 static Object jsonPaths(Object value)throws org.json.JSONException{
  String base=scope.get();
  if(value instanceof org.json.JSONObject){org.json.JSONObject object=(org.json.JSONObject)value;for(java.util.Iterator<String> keys=object.keys();keys.hasNext();){String key=keys.next();object.put(key,jsonPaths(object.get(key)));}return object;}
  if(value instanceof org.json.JSONArray){org.json.JSONArray array=(org.json.JSONArray)value;for(int i=0;i<array.length();i++)array.put(i,jsonPaths(array.get(i)));return array;}
  if(value instanceof String){String string=(String)value;if(base!=null&&(string.startsWith("/api/v1/")||string.startsWith("/console/")||string.startsWith("/core/")||string.startsWith("/themes/")))return base+string.substring(1);return text(string);}
  return value;
 }
 static byte[] body(String type,byte[] data){
  if(type.contains("json")&&scope.get()!=null)try{return jsonPaths(new org.json.JSONTokener(new String(data,StandardCharsets.UTF_8)).nextValue()).toString().getBytes(StandardCharsets.UTF_8);}catch(org.json.JSONException invalid){return data;}
  if(type.contains("javascript")||type.contains("text/"))return text(new String(data,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);return data;
 }
 static void redirect(OutputStream out,String path)throws IOException{out.write(("HTTP/1.1 302 Found\r\nLocation: "+path+"\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();}
 static boolean prepare(Context c,Socket socket,String[] request,Map<String,String> headers,OutputStream out)throws IOException{
  scope.remove();headers.remove("x-aurora-canonical");
  if(request.length!=3)return reject(out,400);
  String raw=request[1];URI uri;
  try{uri=new URI(raw);}catch(Exception e){return reject(out,400);}
  String path=uri.getRawPath();
  if(uri.isAbsolute()||uri.getRawAuthority()!=null||uri.getRawFragment()!=null||path==null||!path.startsWith("/")||path.contains("//")||path.contains("\\")||path.contains("%")||path.indexOf(0)>=0)return reject(out,400);
  for(String part:path.split("/"))if(part.equals(".")||part.equals(".."))return reject(out,400);
  String b=base(c),query=uri.getRawQuery()==null?"":"?"+uri.getRawQuery();
  boolean local=socket.getInetAddress().isLoopbackAddress();
  if(!AuroraRules.lan(socket.getInetAddress()))return reject(out,403);
  if((local&&path.equals("/"))||path.equals(b.substring(0,b.length()-1))||path.equals(b)){redirect(out,b+"console/");return true;}
  if(path.startsWith(b)){
   scope.set(b);headers.put("x-aurora-canonical","1");
   String tail=path.substring(b.length());
   if(tail.equals("console")){redirect(out,b+"console/");return true;}
   if(tail.startsWith("console/")||tail.startsWith("api/v1/")||tail.startsWith("core/")||tail.startsWith("themes/")){request[1]="/"+tail+query;return false;}
   if(tail.startsWith("desktop/")){
    if(request[0].equals("GET")&&(tail.equals("desktop/")||tail.equals("desktop/index.html")||tail.equals("desktop/app.js")||tail.equals("desktop/style.css"))){
     String section=android.net.Uri.parse(raw).getQueryParameter("section"),page="legacy";
     if("appearance".equals(section))page="style";
     else if("ambient".equals(section))page="saver";
     else if("backgrounds".equals(section))page="background";
     else if("apps".equals(section))page="library";
     else if("system".equals(section))page="services";
     else if("devices".equals(section))page="inputs";
     else if(java.util.Arrays.asList("layout","music","usb","logs","advanced","security").contains(section))page=section;
     redirect(out,b+"console/app.html#"+page);return true;
    }
    request[1]="/api/v1/legacy";return false;
   }
   return reject(out,404);
  }
  if(!local)return reject(out,404);
  return false;
 }
 private static boolean reject(OutputStream out,int code)throws IOException{byte[] b=("{\"ok\":false,\"error\":{\"code\":\"PATH_REJECTED\",\"message\":\"Invalid or unavailable path\"}}").getBytes(StandardCharsets.UTF_8);out.write(("HTTP/1.1 "+code+" Error\r\nContent-Type: application/json\r\nContent-Length: "+b.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(b);return true;}
}
