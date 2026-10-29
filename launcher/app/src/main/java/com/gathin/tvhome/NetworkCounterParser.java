package com.gathin.tvhome;
import java.util.*;
/** Small kernel text formats shared by privileged and compatibility samplers. */
final class NetworkCounterParser {
 static final class Row{final String iface;final int uid,set,tag;final long rx,tx,rp,tp;Row(String i,int u,int s,int t,long r,long x,long p,long q){iface=i;uid=u;set=s;tag=t;rx=r;tx=x;rp=p;tp=q;}}
 static List<Row> qtaguid(String text){List<Row> rows=new ArrayList<>();for(String line:text.split("\n"))try{
  String[] p=line.trim().split("\\s+");if(p.length<9||!p[0].matches("[0-9]+"))continue;
  long rx=Long.parseLong(p[5]),tx=Long.parseLong(p[7]),rp=Long.parseLong(p[6]),tp=Long.parseLong(p[8]);if(rx<0||tx<0||rp<0||tp<0)continue;
  rows.add(new Row(p[1],Integer.parseInt(p[3]),Integer.parseInt(p[4]),p[2].matches("(?:0x)?0+")?0:1,rx,tx,rp,tp));
 }catch(Exception ignored){}return rows;}
 static List<Row> interfaces(String text){List<Row> rows=new ArrayList<>();for(String line:text.split("\n"))try{
  if(!line.contains(":"))continue;String[] p=line.trim().split(":",2),n=p[1].trim().split("\\s+");if(n.length<16)continue;
  long rx=Long.parseLong(n[0]),tx=Long.parseLong(n[8]);if(rx<0||tx<0)continue;rows.add(new Row(p[0],-1,0,0,rx,tx,Long.parseLong(n[1]),Long.parseLong(n[9])));
 }catch(Exception ignored){}return rows;}
 static int[] uids(String value){if(value==null||value.isEmpty())return new int[0];if(value.length()>24000||!value.matches("[0-9,]+"))throw new IllegalArgumentException("UID清单无效");TreeSet<Integer> set=new TreeSet<>();for(String s:value.split(","))if(!s.isEmpty()){int uid=Integer.parseInt(s);if(uid>=0)set.add(uid);if(set.size()>2000)throw new IllegalArgumentException("UID清单过长");}return set.stream().mapToInt(Integer::intValue).toArray();}
}
