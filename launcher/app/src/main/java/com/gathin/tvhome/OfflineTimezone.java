package com.gathin.tvhome;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Java adaptation of @photostructure/tz-lookup 11.7.0 (CC0); see assets/timezone/NOTICE.txt. */
final class OfflineTimezone {
 private final String grid;private final String[] zones;
 private OfflineTimezone(String grid,String[] zones){this.grid=grid;this.zones=zones;}
 static OfflineTimezone read(InputStream input)throws IOException{BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));String grid=r.readLine();List<String> zones=new ArrayList<>();String line;while((line=r.readLine())!=null){if(!line.isEmpty())zones.add(line);if(zones.size()>600)throw new IOException("时区索引无效");}if(grid==null||grid.length()<2304||grid.length()>131072||zones.size()<100)throw new IOException("时区数据无效");return new OfflineTimezone(grid,zones.toArray(new String[0]));}
 String lookup(double lat,double lon){CityTime.key(lat,lon);if(lat>=90)return "Etc/GMT";int previous=-1;double x=48*(180+lon)/360.00000000000006,y=24*(90-lat)/180.00000000000003;int ix=(int)x,iy=(int)y,index=96*iy+2*ix,value=decode(index);int depth=0;
  while(value+zones.length<3136){if(++depth>32)throw new IllegalStateException("时区索引循环");previous=previous+value+1;y=(2*(y-iy))%2;iy=(int)y;x=(2*(x-ix))%2;ix=(int)x;index=8*previous+4*iy+2*ix+2304;value=decode(index);}int zone=value+zones.length-3136;if(zone<0||zone>=zones.length)throw new IllegalStateException("时区索引越界");return zones[zone];
 }
 private int decode(int index){if(index<0||index+1>=grid.length())throw new IllegalStateException("时区数据越界");return 56*grid.charAt(index)+grid.charAt(index+1)-1995;}
}
