package com.gathin.tvhome;
import android.content.Context;import android.net.*;import java.net.*;import java.util.*;import org.json.*;
final class ConsoleAddresses {
 static JSONArray list(Context c)throws Exception{
  LinkedHashSet<String> ips=new LinkedHashSet<>();ConnectivityManager manager=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);LinkProperties links=manager.getLinkProperties(manager.getActiveNetwork());
  if(links!=null)for(LinkAddress link:links.getLinkAddresses())add(ips,link.getAddress());
  Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all!=null&&all.hasMoreElements()){NetworkInterface network=all.nextElement();if(!network.isUp()||network.isLoopback())continue;for(InetAddress ip:Collections.list(network.getInetAddresses()))add(ips,ip);}
  JSONArray result=new JSONArray();for(String ip:ips)result.put("http://"+ip+":"+ConsoleSettings.port(c)+ConsolePath.base(c));return result;
 }
 private static void add(Set<String> ips,InetAddress ip){if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress())ips.add(ip.getHostAddress());}
}
