package com.gathin.tvhome;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** One content version for the console bundle, including references between its assets. */
final class ConsoleAssets {
    private static final Map<String,byte[]> contents=new TreeMap<>();
    private static final Map<String,String> publicNames=new LinkedHashMap<>();
    private static String stamp="";
    private static long checked;
    private static void discover(Context c,String path,List<String> names)throws Exception{
        String[] entries=c.getAssets().list("aurora/"+path);
        if(entries==null||entries.length==0){if(path.matches(".*\\.(html|js|css|svg|json|ttf|otf|txt)$"))names.add(path);return;}
        for(String name:entries)discover(c,path+"/"+name,names);
    }
    private static synchronized void refresh(Context c)throws Exception{
        long now=android.os.SystemClock.elapsedRealtime();if(!contents.isEmpty()&&now-checked<1000)return;
        checked=now;List<String> names=new ArrayList<>();discover(c,"console",names);discover(c,"core",names);Collections.sort(names);
        StringBuilder signature=new StringBuilder().append(c.getPackageManager().getPackageInfo(c.getPackageName(),0).lastUpdateTime);
        for(String name:names){File file=new File(AuroraThemes.root(c),name);signature.append('|').append(name).append(':').append(file.lastModified()).append(':').append(file.length());}
        if(signature.toString().equals(stamp))return;
        Map<String,byte[]> next=new TreeMap<>();MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(String name:names){byte[] bytes=AuroraThemes.asset(c,name);next.put(name,bytes);digest.update(name.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);digest.update(bytes);}
        StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",value&255));String hash=hex.substring(0,12);
        Map<String,String> urls=new LinkedHashMap<>();for(String name:names)if(name.endsWith(".js")||name.endsWith(".css")){int dot=name.lastIndexOf('.');urls.put(name,name.substring(0,dot)+"."+hash+name.substring(dot));}
        // Replacements use the original source once, so no generated URL can be rewritten twice.
        for(String name:names){byte[] bytes=next.get(name);if(name.matches(".*\\.(html|js|css)$")){String text=new String(bytes,StandardCharsets.UTF_8);for(Map.Entry<String,String> entry:urls.entrySet())text=text.replace("/"+entry.getKey(),"/"+entry.getValue());next.put(name,text.getBytes(StandardCharsets.UTF_8));}}
        contents.clear();contents.putAll(next);publicNames.clear();publicNames.putAll(urls);stamp=signature.toString();
    }
    static synchronized String logical(Context c,String path)throws Exception{
        refresh(c);if(contents.containsKey(path))return path;
        for(Map.Entry<String,String> entry:publicNames.entrySet())if(path.equals(entry.getValue()))return entry.getKey();
        return null;
    }
    static synchronized String publicName(Context c,String logical)throws Exception{refresh(c);return publicNames.getOrDefault(logical,logical);}
    static synchronized byte[] read(Context c,String logical)throws Exception{refresh(c);byte[] bytes=contents.get(logical);if(bytes==null)throw new java.io.FileNotFoundException("页面资源不存在");return bytes;}
}
