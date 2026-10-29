package com.gathin.tvhome;
import java.time.*;import java.util.*;import java.net.URL;
final class CityTime {
 static String name(String value){value=value.trim();if(value.isEmpty()||value.length()>80)throw new IllegalArgumentException("请输入城市名称");for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw new IllegalArgumentException("城市名称不能包含控制字符");return value;}
 static String zone(String value){try{return ZoneId.of(value).getId();}catch(DateTimeException unsupported){String alias=value.equals("Europe/Kyiv")?"Europe/Kiev":value.equals("America/Nuuk")?"America/Godthab":value.equals("Pacific/Kanton")?"Pacific/Enderbury":value.equals("Asia/Kolkata")?"Asia/Calcutta":value.equals("Asia/Yangon")?"Asia/Rangoon":value.equals("Asia/Kathmandu")?"Asia/Katmandu":value;return ZoneId.of(alias).getId();}}
 static String key(double lat,double lon){if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat< -90||lat>90||lon< -180||lon>180)throw new IllegalArgumentException("城市经纬度无效");return String.format(Locale.ROOT,"%.6f,%.6f",lat==0?0:lat,lon==0?0:lon);}
 static LocalDate date(long epoch,String zone){return Instant.ofEpochMilli(epoch).atZone(ZoneId.of(zone)).toLocalDate();}
 static String weatherTemplate(String url,String provider){if(!provider.equals("openmeteo"))return url;try{String h=new URL(url).getHost();if(!(h.equals("open-meteo.com")||h.endsWith(".open-meteo.com")))return url;}catch(Exception e){return url;}if(url.matches(".*[?&]timezone=[^&]*.*"))return url.replaceAll("([?&])timezone=[^&]*","$1timezone={timezone}");return url+(url.contains("?")?"&":"?")+"timezone={timezone}";}
}
