package com.gathin.tvhome;
import java.util.*;import java.util.regex.*;
/** Cached LRC timing; only the current background track's local lyrics are used. */
final class AmbientLyricLine {
 private String cached="";private final TreeMap<Long,String> lines=new TreeMap<>();
 String at(String text,long position){if(!text.equals(cached)){cached=text;lines.clear();long offset=0;Matcher o=Pattern.compile("\\[offset:([+-]?\\d+)\\]",Pattern.CASE_INSENSITIVE).matcher(text);if(o.find())try{offset=Long.parseLong(o.group(1));}catch(Exception ignored){}
 for(String raw:text.split("\\r?\\n")){Matcher m=Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]").matcher(raw);List<Long> times=new ArrayList<>();int end=0;while(m.find()){String f=m.group(3);long ms=f==null?0:Long.parseLong(f)*(f.length()==1?100:f.length()==2?10:1);times.add(Long.parseLong(m.group(1))*60000+Long.parseLong(m.group(2))*1000+ms+offset);end=m.end();}String line=raw.substring(end).replaceAll("<\\d{1,3}:\\d{2}(?:\\.\\d{1,3})?>","").trim();for(long t:times)lines.put(t,line);}}
 Map.Entry<Long,String> line=lines.floorEntry(position);return line==null?"":line.getValue();}
}