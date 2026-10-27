package com.gathin.tvhome;
import org.json.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
/** Read-only privileged sampler, invoked through the existing authorized loopback ADB. */
public final class NetworkTrafficProbe {
 private static String read(String path)throws Exception{try(BufferedReader r=new BufferedReader(new FileReader(path))){StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line).append('\n');return b.toString();}}
 private static String address(String value)throws Exception{
  String[] p=value.split(":");String hex=p[0];byte[] b=new byte[hex.length()/2];
  for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(hex.substring((i/4*4+3-i%4)*2,(i/4*4+4-i%4)*2),16);
  return InetAddress.getByAddress(b).getHostAddress();
 }
 private static String scope(String ip){try{InetAddress a=InetAddress.getByName(ip);return a.isLoopbackAddress()?"loopback":a.isAnyLocalAddress()?"any":a.isLinkLocalAddress()||a.isSiteLocalAddress()?"lan":a.isMulticastAddress()?"multicast":ip.startsWith("fc")||ip.startsWith("fd")?"lan":"public";}catch(Exception e){return "unknown";}}
 private static JSONArray connections(){
  JSONArray result=new JSONArray();
  for(String protocol:new String[]{"tcp","tcp6","udp","udp6"})try{
   String[] lines=read("/proc/net/"+protocol).split("\n");
   for(int i=1;i<lines.length&&result.length()<1500;i++)try{
    String[] p=lines[i].trim().split("\\s+");if(p.length<10)continue;
    String local=address(p[1]),remote=address(p[2]);String[] q=p[4].split(":");
    String state=p[3].equals("01")?"ESTABLISHED":p[3].equals("0A")?"LISTEN":p[3].equals("06")?"TIME_WAIT":p[3].equals("02")?"SYN_SENT":p[3].equals("08")?"CLOSE_WAIT":p[3].equals("07")?"CLOSED":p[3];
    result.put(new JSONObject().put("uid",Integer.parseInt(p[7])).put("protocol",protocol).put("local",local).put("localPort",Integer.parseInt(p[1].split(":")[1],16)).put("remote",remote).put("remotePort",Integer.parseInt(p[2].split(":")[1],16)).put("scope",scope(remote)).put("state",state).put("txQueueBytes",Long.parseLong(q[0],16)).put("rxQueueBytes",Long.parseLong(q[1],16)));
   }catch(Exception ignored){}
  }catch(Exception ignored){}
  return result;
 }
 private static String snapshot(){
  try{
   JSONObject out=new JSONObject().put("timestamp",System.currentTimeMillis()).put("uptimeMs",android.os.SystemClock.elapsedRealtime()).put("bootId",read("/proc/sys/kernel/random/boot_id").trim());
   JSONArray interfaces=new JSONArray();
   for(String line:read("/proc/net/dev").split("\n"))if(line.contains(":")){
    String[] a=line.trim().split(":",2);String[] n=a[1].trim().split("\\s+");
    interfaces.put(new JSONObject().put("name",a[0].trim()).put("rxBytes",Long.parseLong(n[0])).put("rxPackets",Long.parseLong(n[1])).put("txBytes",Long.parseLong(n[8])).put("txPackets",Long.parseLong(n[9])).put("rxErrors",Long.parseLong(n[2])).put("txErrors",Long.parseLong(n[10])));
   }
   out.put("interfaces",interfaces);
   Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"network_management");
   Object manager=Class.forName("android.os.INetworkManagementService$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
   Object stats=manager.getClass().getMethod("getNetworkStatsDetail").invoke(manager);
   Class<?> entry=Class.forName("android.net.NetworkStats$Entry");Method get=stats.getClass().getMethod("getValues",int.class,entry);
   int size=(Integer)stats.getClass().getMethod("size").invoke(stats);JSONArray rows=new JSONArray();
   for(int i=0;i<size;i++){Object e=get.invoke(stats,i,null);JSONObject row=new JSONObject();for(String key:new String[]{"iface","uid","set","tag","rxBytes","txBytes","rxPackets","txPackets"})row.put(key,entry.getField(key).get(e));rows.put(row);}
   out.put("uidRows",rows).put("connections",connections()).put("source","INetworkManagementService");
   return out.toString();
  }catch(Throwable e){try{return new JSONObject().put("error",String.valueOf(e.getCause()==null?e:e.getCause())).toString();}catch(Exception ignored){}return "{\"error\":\"采样失败\"}";}
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=3||!"--serve".equals(args[0])){System.out.println(snapshot());return;}
  int port=Integer.parseInt(args[1]);String token=args[2];if(port<1024||!token.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
  long active=System.nanoTime();
  try(java.net.ServerSocket server=new java.net.ServerSocket(port,2,InetAddress.getByName("127.0.0.1"))){
   server.setSoTimeout(2000);
   while(System.nanoTime()-active<60000000000L){
    try(java.net.Socket socket=server.accept()){
     socket.setSoTimeout(1500);ByteArrayOutputStream line=new ByteArrayOutputStream();int ch;
     while(line.size()<90&&(ch=socket.getInputStream().read())!=-1&&ch!=10)line.write(ch);
     String request=line.toString("UTF-8");boolean stop=request.equals(token+" STOP");
     if(!stop&&!request.equals(token+" SAMPLE"))continue;
     active=System.nanoTime();byte[] result=(stop?"{\"stopped\":true}":snapshot()).getBytes("UTF-8");
     socket.getOutputStream().write(result);socket.getOutputStream().flush();if(stop)break;
    }catch(java.net.SocketTimeoutException ignored){}catch(Exception ignored){}
   }
  }
 }

}
