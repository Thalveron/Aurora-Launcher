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
  readableConnections=0;JSONArray result=new JSONArray();
  for(String protocol:new String[]{"tcp","tcp6","udp","udp6"})try{
   String[] lines=read("/proc/net/"+protocol).split("\n");readableConnections++;
   for(int i=1;i<lines.length&&result.length()<1500;i++)try{
    String[] p=lines[i].trim().split("\\s+");if(p.length<10)continue;
    String local=address(p[1]),remote=address(p[2]);String[] q=p[4].split(":");
    String state=p[3].equals("01")?"ESTABLISHED":p[3].equals("0A")?"LISTEN":p[3].equals("06")?"TIME_WAIT":p[3].equals("02")?"SYN_SENT":p[3].equals("08")?"CLOSE_WAIT":p[3].equals("07")?"CLOSED":p[3];
    result.put(new JSONObject().put("uid",Integer.parseInt(p[7])).put("protocol",protocol).put("local",local).put("localPort",Integer.parseInt(p[1].split(":")[1],16)).put("remote",remote).put("remotePort",Integer.parseInt(p[2].split(":")[1],16)).put("scope",scope(remote)).put("state",state).put("txQueueBytes",Long.parseLong(q[0],16)).put("rxQueueBytes",Long.parseLong(q[1],16)));
   }catch(Exception ignored){}
  }catch(Exception ignored){}
  return result;
 }
 private static boolean uidPacketsSupported;private static int readableConnections;private static final String fallbackBoot="clock-"+((System.currentTimeMillis()-android.os.SystemClock.elapsedRealtime())/1000);
 private static JSONObject row(NetworkCounterParser.Row r)throws Exception{return new JSONObject().put("iface",r.iface).put("uid",r.uid).put("set",r.set).put("tag",r.tag).put("rxBytes",r.rx).put("txBytes",r.tx).put("rxPackets",r.rp).put("txPackets",r.tp);}
 private static JSONArray managementRows()throws Exception{
  Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"network_management");
  Object manager=Class.forName("android.os.INetworkManagementService$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
  Object stats=Class.forName("android.os.INetworkManagementService").getMethod("getNetworkStatsDetail").invoke(manager);
  Class<?> entry=Class.forName("android.net.NetworkStats$Entry"),type=Class.forName("android.net.NetworkStats");Method get=type.getMethod("getValues",int.class,entry);int size=(Integer)type.getMethod("size").invoke(stats);JSONArray rows=new JSONArray();
  for(int i=0;i<size;i++){Object e=get.invoke(stats,i,null);JSONObject r=new JSONObject();for(String key:new String[]{"iface","uid","set","tag","rxBytes","txBytes","rxPackets","txPackets"})r.put(key,entry.getField(key).get(e));rows.put(r);}return rows;
 }
 private static JSONArray uidApiRows(int[] uids)throws Exception{uidPacketsSupported=true;
  Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"netstats");
  Object manager=Class.forName("android.net.INetworkStatsService$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
  Method get=Class.forName("android.net.INetworkStatsService").getMethod("getUidStats",int.class,int.class);JSONArray rows=new JSONArray();
  for(int uid:uids){long rx=((Number)get.invoke(manager,uid,0)).longValue(),tx=((Number)get.invoke(manager,uid,2)).longValue();if(rx<0||tx<0)continue;long rp=((Number)get.invoke(manager,uid,1)).longValue(),tp=((Number)get.invoke(manager,uid,3)).longValue();if(rp<0||tp<0)uidPacketsSupported=false;rows.put(row(new NetworkCounterParser.Row("all",uid,0,0,rx,tx,Math.max(0,rp),Math.max(0,tp))));}
  if(rows.length()==0)throw new IOException("UID统计接口未返回可用数据");return rows;
 }
 private static String reason(Throwable e){Throwable cause=e.getCause()==null?e:e.getCause();String value=cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());return value.length()>220?value.substring(0,220):value;}
 private static String snapshot(int[] requested){
  try{
   List<String> diagnostics=new ArrayList<>();String boot;try{boot=read("/proc/sys/kernel/random/boot_id").trim();}catch(Exception e){boot=fallbackBoot;}
   JSONArray links=connections();Set<Integer> ids=new TreeSet<>();for(int uid:requested)ids.add(uid);for(int i=0;i<links.length();i++)ids.add(links.getJSONObject(i).getInt("uid"));int[] uids=ids.stream().mapToInt(Integer::intValue).toArray();
   JSONArray interfaces=new JSONArray();String ifaceSource="proc/net/dev";
   try{for(NetworkCounterParser.Row r:NetworkCounterParser.interfaces(read("/proc/net/dev")))interfaces.put(new JSONObject().put("name",r.iface).put("rxBytes",r.rx).put("txBytes",r.tx).put("rxPackets",r.rp).put("txPackets",r.tp));}catch(Exception e){diagnostics.add(reason(e));}
   if(interfaces.length()==0){ifaceSource="sysfs";File[] dirs=new File("/sys/class/net").listFiles();if(dirs!=null)for(File dir:dirs)try{interfaces.put(new JSONObject().put("name",dir.getName()).put("rxBytes",Long.parseLong(read(dir+"/statistics/rx_bytes").trim())).put("txBytes",Long.parseLong(read(dir+"/statistics/tx_bytes").trim())));}catch(Exception ignored){}}
   if(interfaces.length()==0){ifaceSource="TrafficStats total";long rx=android.net.TrafficStats.getTotalRxBytes(),tx=android.net.TrafficStats.getTotalTxBytes();if(rx>=0&&tx>=0)interfaces.put(new JSONObject().put("name","all").put("rxBytes",rx).put("txBytes",tx));}
   JSONArray rows=null;String source="INetworkManagementService";
   try{rows=managementRows();}catch(Throwable e){diagnostics.add(source+": "+reason(e));}
   if(rows==null)try{source="xt_qtaguid";String text=read("/proc/net/xt_qtaguid/stats");if(!text.trim().startsWith("idx"))throw new IOException("内核统计格式不兼容");rows=new JSONArray();for(NetworkCounterParser.Row r:NetworkCounterParser.qtaguid(text))rows.put(row(r));}catch(Throwable e){rows=null;diagnostics.add(source+": "+reason(e));}
   if(rows==null)try{source="INetworkStatsService UID";rows=uidApiRows(uids);}catch(Throwable e){diagnostics.add(source+": "+reason(e));}
   if(rows==null){source="TrafficStats UID";JSONArray available=new JSONArray();for(int uid:uids)try{long rx=android.net.TrafficStats.getUidRxBytes(uid),tx=android.net.TrafficStats.getUidTxBytes(uid);if(rx>=0&&tx>=0)available.put(row(new NetworkCounterParser.Row("all",uid,0,0,rx,tx,0,0)));}catch(Throwable ignored){}if(available.length()>0)rows=available;}
   boolean uidAvailable=rows!=null;if(rows==null){rows=new JSONArray();source="total only";}
   JSONObject out=new JSONObject().put("timestamp",System.currentTimeMillis()).put("uptimeMs",android.os.SystemClock.elapsedRealtime()).put("bootId",boot).put("interfaces",interfaces).put("uidRows",rows).put("connections",links).put("source",source+" / "+ifaceSource).put("uidAvailable",uidAvailable).put("packetsAvailable",source.equals("INetworkManagementService")||source.equals("xt_qtaguid")||(source.equals("INetworkStatsService UID")&&uidPacketsSupported)).put("connectionsAvailable",readableConnections>0).put("uidComplete",source.equals("INetworkManagementService")||source.equals("xt_qtaguid")).put("diagnostics",new JSONArray(diagnostics));
   out.put("compatibilityNote",(!uidAvailable?"系统未开放逐应用统计，目前仅显示网卡总量；应用数据不可用，不代表零流量。":"")+(uidAvailable&&!source.equals("INetworkManagementService")&&!source.equals("xt_qtaguid")?"兼容接口中无法读取的UID显示为空，不代表零流量。":"")+(readableConnections==0?"系统禁止读取连接列表，无法显示连接IP。":"")+(ifaceSource.equals("TrafficStats total")?"网卡明细不可用，使用系统总量。":""));
   if(interfaces.length()==0&&!uidAvailable)out.put("error","系统拒绝所有网络统计接口；请检查本机调试连接与系统权限。"+String.join("; ",diagnostics));return out.toString();
  }catch(Throwable e){try{return new JSONObject().put("error",reason(e)).toString();}catch(Exception ignored){return "{\"error\":\"采样失败\"}";}}
 }

 public static void main(String[] args)throws Exception{
  if(args.length!=3||!"--serve".equals(args[0])){System.out.println(snapshot(new int[0]));return;}
  int port=Integer.parseInt(args[1]);String token=args[2];if(port<1024||!token.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
  long active=System.nanoTime();
  try(java.net.ServerSocket server=new java.net.ServerSocket(port,2,InetAddress.getByName("127.0.0.1"))){
   server.setSoTimeout(2000);
   while(System.nanoTime()-active<60000000000L){
    try(java.net.Socket socket=server.accept()){
     socket.setSoTimeout(1500);ByteArrayOutputStream line=new ByteArrayOutputStream();int ch;
     while(line.size()<32768&&(ch=socket.getInputStream().read())!=-1&&ch!=10)line.write(ch);
     String request=line.toString("UTF-8");boolean stop=request.equals(token+" STOP");
     if(!stop&&!request.equals(token+" SAMPLE")&&!request.startsWith(token+" SAMPLE "))continue;int[] uids=stop?new int[0]:NetworkCounterParser.uids(request.length()>token.length()+8?request.substring(token.length()+8):"");
     active=System.nanoTime();byte[] result=(stop?"{\"stopped\":true}":snapshot(uids)).getBytes("UTF-8");
     socket.getOutputStream().write(result);socket.getOutputStream().flush();if(stop)break;
    }catch(java.net.SocketTimeoutException ignored){}catch(Exception ignored){}
   }
  }
 }

}
