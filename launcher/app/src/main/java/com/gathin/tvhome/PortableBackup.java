package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Portable data only; device grants, sessions and executable packages stay local. */
final class PortableBackup {
    private static final long LIMIT=2L*1024*1024*1024;
    private static final String MANIFEST="aurora-settings.json";
    private static final Set<String> PREFS=new TreeSet<>(Arrays.asList(
        "launcher_settings","desktop_appearance","desktop_language","desktop_location","background","ambient_music",
        "idle_standby","input_shortcut","input_devices","app_presentation","boot_input","wifi_shortcut",
        "aurora_library","aurora_bgm","aurora_bgm_shared_saver","aurora_bgm_tv_volume","aurora_voice","aurora_services","network_monitor"));
    private static boolean preference(String name){return PREFS.contains(name)||name.matches("(fullscreen_|aurora_bgm_theme_)[a-zA-Z0-9_-]{1,80}");}
    private static Set<String> names(Context c){
        Set<String> names=new TreeSet<>(PREFS);
        String[] files=new File(c.getDataDir(),"shared_prefs").list();
        if(files!=null)for(String file:files)if(file.endsWith(".xml")){
            String name=file.substring(0,file.length()-4);if(preference(name))names.add(name);
        }
        return names;
    }
    private static JSONObject capture(Context c)throws Exception{
        JSONObject all=new JSONObject();
        for(String name:names(c)){
            JSONObject group=new JSONObject();
            for(Map.Entry<String,?> e:c.getSharedPreferences(name,0).getAll().entrySet()){
                Object v=e.getValue();String type=v instanceof Set?"set":v.getClass().getSimpleName();
                group.put(e.getKey(),new JSONArray().put(type).put(v instanceof Set?new JSONArray((Set<?>)v):v));
            }
            all.put(name,group);
        }
        return all;
    }
    private static Set<String> keys(JSONObject object){
        Set<String> result=new LinkedHashSet<>();Iterator<String> iterator=object.keys();
        while(iterator.hasNext())result.add(iterator.next());return result;
    }
    private static void prefs(Context c,JSONObject all,boolean apply)throws Exception{
        Set<String> groups=names(c);groups.addAll(keys(all));
        for(String name:groups){
            if(!preference(name))throw new IOException("配置分组无效");
            SharedPreferences.Editor ed=c.getSharedPreferences(name,0).edit().clear();
            JSONObject values=all.has(name)?all.getJSONObject(name):new JSONObject();
            for(String key:keys(values)){
                JSONArray pair=values.getJSONArray(key);
                switch(pair.getString(0)){
                    case "Boolean":ed.putBoolean(key,pair.getBoolean(1));break;
                    case "Integer":ed.putInt(key,pair.getInt(1));break;
                    case "Long":ed.putLong(key,pair.getLong(1));break;
                    case "Float":ed.putFloat(key,(float)pair.getDouble(1));break;
                    case "String":ed.putString(key,pair.getString(1));break;
                    case "set":JSONArray a=pair.getJSONArray(1);Set<String> items=new HashSet<>();for(int i=0;i<a.length();i++)items.add(a.getString(i));ed.putStringSet(key,items);break;
                    default:throw new IOException("不支持的设置类型");
                }
            }
            if(apply&&!ed.commit())throw new IOException("设置写入失败");
        }
    }
    private static boolean allowed(String n){
        return n.matches("local/(order\\.txt|cards\\.json|library_apps\\.txt|library_home_removed\\.txt|input-remote-hint\\.image)")
            ||n.matches("local/(backgrounds|ambient-music)/[a-zA-Z0-9_-]+\\.(image|mp4|audio|jpg|lrc)(\\.jpg)?")
            ||n.matches("theme/(config\\.json|custom/themes\\.json|custom/css/[a-zA-Z0-9_-]+\\.css)")
            ||n.matches("theme/(library|bgm)/[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp|mp4|webm|mkv|ttf|otf|lrc|mp3|aac|wav|flac|m4a|ogg|opus|audio)");
    }
    private static File file(Context c,String n)throws Exception{
        if(!allowed(n))throw new IOException("无效的备份路径");
        File root=n.startsWith("local/")?c.getFilesDir():AuroraThemes.root(c);
        File result=new File(root.getCanonicalFile(),n.substring(n.indexOf('/')+1));
        if(!result.getCanonicalFile().equals(result.getAbsoluteFile()))throw new IOException("路径错误");
        return result;
    }
    private static List<String> paths(Context c)throws Exception{
        List<String> out=new ArrayList<>();
        for(String n:new String[]{"local/order.txt","local/cards.json","local/library_apps.txt","local/library_home_removed.txt","local/input-remote-hint.image","theme/config.json","theme/custom/themes.json"})
            if(file(c,n).isFile())out.add(n);
        for(String n:new String[]{"local/backgrounds","local/ambient-music","theme/library","theme/bgm","theme/custom/css"}){
            File root=n.startsWith("local/")?c.getFilesDir():AuroraThemes.root(c);
            String[] files=new File(root,n.substring(n.indexOf('/')+1)).list();
            if(files!=null)for(String child:files){String path=n+"/"+child;if(allowed(path)&&file(c,path).isFile())out.add(path);}
        }
        return out;
    }
    private static long copy(InputStream in,OutputStream out,long limit)throws IOException{
        long total=0;byte[] buf=new byte[65536];int n;
        while((n=in.read(buf))!=-1){total+=n;if(total>limit)throw new IOException("文件超过大小限制");out.write(buf,0,n);}return total;
    }
    static synchronized void download(Context c,OutputStream out)throws Exception{
        BundledDefaults.awaitReady();
        List<String> entries=paths(c);
        JSONObject preferences=capture(c);
        String day=new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date());
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=Aurora-settings-"+day+".zip\r\nCache-Control: no-store\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        OutputStream chunks=new OutputStream(){
            public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
            public void write(byte[] data,int offset,int length)throws IOException{
                if(length==0)return;
                out.write((Integer.toHexString(length)+"\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(data,offset,length);
                out.write(13);out.write(10);
            }
        };
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(chunks,65536))){
            zip.setLevel(0);
                JSONObject sums=new JSONObject();long total=0;
                for(String n:entries){
                    File f=file(c,n);zip.putNextEntry(new ZipEntry(n));
                    java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
                    try(InputStream in=new java.security.DigestInputStream(new FileInputStream(f),digest)){total+=copy(in,zip,LIMIT-total);}
                    zip.closeEntry();StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));sums.put(n,hex.toString());
                }
                byte[] metadata=new JSONObject().put("format","aurora-launcher-settings").put("schema",1).put("preferences",preferences).put("files",sums).toString().getBytes(StandardCharsets.UTF_8);
                if(metadata.length>16*1024*1024)throw new IOException("配置清单过大");
                zip.putNextEntry(new ZipEntry(MANIFEST));zip.write(metadata);zip.closeEntry();
            }
        out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }
    private static void erase(File dir){File[] files=dir.listFiles();if(files!=null)for(File child:files)erase(child);dir.delete();}
    static synchronized void restore(Context c,InputStream in,long size)throws Exception{
        BundledDefaults.awaitReady();
        if(size<=0||size>LIMIT+32L*1024*1024||size>c.getCacheDir().getUsableSpace())throw new IOException("文件大小或电视空间不足");
        File stage=Files.createTempDirectory(c.getCacheDir().toPath(),"aurora-restore-").toFile();
        Map<String,File> verified=new LinkedHashMap<>();
        try{
            File source=new File(stage,"upload.zip");
            try(OutputStream out=new FileOutputStream(source)){
                byte[] buf=new byte[65536];long remaining=size;while(remaining>0){int n=in.read(buf,0,(int)Math.min(buf.length,remaining));if(n<0)throw new EOFException();out.write(buf,0,n);remaining-=n;}
            }
            JSONObject preferences;
            try(ZipFile zip=new ZipFile(source)){
                ZipEntry meta=zip.getEntry(MANIFEST);if(meta==null||zip.size()>20000)throw new IOException("请选择极光桌面的配置备份");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream data=zip.getInputStream(meta)){copy(data,bytes,16L*1024*1024);}
                JSONObject info=new JSONObject(bytes.toString("UTF-8"));
                if(!"aurora-launcher-settings".equals(info.optString("format"))||info.optInt("schema")!=1)throw new IOException("不支持的备份格式");
                preferences=info.getJSONObject("preferences");JSONObject sums=info.getJSONObject("files");
                if(!keys(preferences).containsAll(PREFS)||zip.size()!=sums.length()+1)throw new IOException("备份清单不完整");
                prefs(c,preferences,false);
                long total=0;
                for(String n:keys(sums)){
                    file(c,n);ZipEntry entry=zip.getEntry(n);if(entry==null)throw new IOException("缺少备份文件");
                    File f=File.createTempFile("data-",null,stage);
                    try(InputStream data=zip.getInputStream(entry);OutputStream out=new FileOutputStream(f)){total+=copy(data,out,Math.min(LIMIT-total,stage.getUsableSpace()-16777216));}
                    if(!sums.getString(n).equals(UpdatePolicy.sha(f)))throw new IOException("备份文件校验失败");
                    verified.put(n,f);
                }
            }
            source.delete();JSONObject before=capture(c);Set<String> all=new LinkedHashSet<>(paths(c));all.addAll(verified.keySet());
            Map<String,File> previous=new LinkedHashMap<>();List<File> created=new ArrayList<>();
            try{
                for(String n:all){
                    File dest=file(c,n);File next=verified.get(n);
                    if(dest.isFile()&&next!=null&&UpdatePolicy.sha(dest).equals(UpdatePolicy.sha(next)))continue;
                    if(!dest.getParentFile().isDirectory()&&!dest.getParentFile().mkdirs())throw new IOException("无法创建设置目录");
                    File temp=null;
                    try{
                        if(next!=null){temp=File.createTempFile("restore-",null,dest.getParentFile());Files.copy(next.toPath(),temp.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
                        if(dest.exists()){File old=File.createTempFile("prior-",null,dest.getParentFile());Files.move(dest.toPath(),old.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);previous.put(n,old);}
                        if(temp!=null){Files.move(temp.toPath(),dest.toPath());created.add(dest);}
                    }finally{if(temp!=null)temp.delete();}
                }
                prefs(c,preferences,true);
            }catch(Exception failure){
                for(File f:created)f.delete();
                for(Map.Entry<String,File> e:previous.entrySet())Files.move(e.getValue().toPath(),file(c,e.getKey()).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                prefs(c,before,true);throw failure;
            }
            for(File f:previous.values())f.delete();
            c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()).putExtra("kind","settings-layout"));
        }finally{erase(stage);}
    }
}
