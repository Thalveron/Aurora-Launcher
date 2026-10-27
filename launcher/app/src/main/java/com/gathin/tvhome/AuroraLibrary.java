package com.gathin.tvhome;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** Managed uploads with bounded streaming and Range responses for long / high resolution video. */
final class AuroraLibrary {
    private static final Map<String,JSONObject> jobs=new LinkedHashMap<>();
    static File dir(Context c)throws Exception{File d=new File(AuroraThemes.root(c),"library");if(!d.isDirectory()&&!d.mkdirs())throw new IOException("无法创建素材目录");return d;}
    static File file(Context c,String id,String kind)throws Exception{
        if(id.startsWith("native-")){
            if(!id.matches("native-(image|video)-[a-zA-Z0-9-]+\\.(image|mp4)"))throw new IllegalArgumentException("素材编号无效");
            if(kind!=null&&!kind.equals("background"))throw new IllegalArgumentException("素材类型不符");
            File f=BackgroundStore.byId(c,id.substring(7));if(f==null||!f.isFile())throw new FileNotFoundException("素材不存在");return f;
        }
        if(!id.matches("[a-f0-9-]{36}\\.(jpg|jpeg|png|webp|mp4|webm|mkv|ttf|otf|lrc|apk)"))throw new IllegalArgumentException("素材编号无效");
        String actual=id.matches(".*\\.(ttf|otf)")?"font":id.endsWith(".lrc")?"lyrics":id.endsWith(".apk")?"apk":"background";
        if(kind!=null&&!kind.equals(actual))throw new IllegalArgumentException("素材类型不符");File f=AuroraRules.inside(dir(c),id);if(!f.isFile())throw new FileNotFoundException("素材不存在");return f;
    }
    static JSONArray list(Context c)throws Exception{
        JSONArray all=new JSONArray();File[] files=dir(c).listFiles();if(files==null)files=new File[0];
        android.content.SharedPreferences p=c.getSharedPreferences("aurora_library",0);Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files){if(f.getName().endsWith(".part"))continue;String id=f.getName();try{file(c,id,null);}catch(Exception ignored){continue;}all.put(new JSONObject().put("id",id).put("name",p.getString(id,id)).put("bytes",f.length()).put("kind",id.matches(".*\\.(ttf|otf)")?"font":id.endsWith(".lrc")?"lyrics":id.endsWith(".apk")?"apk":"background").put("video",id.matches(".*\\.(mp4|webm|mkv)")).put("url","/api/v1/assets/file/"+id).put("thumbnail","/api/v1/assets/thumbnail/"+id));}
        int index=0;for(File f:BackgroundStore.items(c)){
            String id="native-"+f.getName();boolean video=BackgroundStore.kind(f).equals("video");index++;
            all.put(new JSONObject().put("id",id).put("name",(video?"视频":"图片")+" · 原背景库 "+index).put("bytes",f.length()).put("kind","background").put("video",video).put("source","legacy").put("url","/api/v1/assets/file/"+id).put("thumbnail","/api/v1/assets/thumbnail/"+id));
        }
        return all;
    }
    static synchronized void inheritNativeBackground(Context c)throws Exception{
        android.content.SharedPreferences p=c.getSharedPreferences("aurora_library",0);
        if(p.getBoolean("native-background-imported",false))return;
        if(AuroraSettings.get(c).getJSONArray("desktopSlides").length()>0){p.edit().putBoolean("native-background-imported",true).commit();return;}
        String mode=BackgroundStore.mode(c);if(mode.equals("default"))return;
        File source=BackgroundStore.file(c,mode);if(!source.isFile())return;
        try(InputStream in=new FileInputStream(source)){
            JSONObject item=upload(c,in,source.length(),mode.equals("video")?"原桌面背景.mp4":"原桌面背景.jpg");
            AuroraSettings.save(c,new JSONObject().put("desktopSlides",new JSONArray().put(item.getString("id"))));
        }
        p.edit().putBoolean("native-background-imported",true).commit();
    }
    static JSONObject upload(Context c,InputStream in,long size,String name)throws Exception{
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);if(!ext.matches("jpg|jpeg|png|webp|mp4|webm|mkv|ttf|otf|lrc|apk"))throw new IllegalArgumentException("不支持此素材类型");
        long max=ext.matches("ttf|otf")?40L*1024*1024:ext.equals("lrc")?1024*1024:1024L*1024*1024;
        if(size<=0||size>max||dir(c).getUsableSpace()<size+128L*1024*1024)throw new IllegalArgumentException("文件过大或电视空间不足");
        if(list(c).length()>=150)throw new IllegalArgumentException("素材最多150项，请清理不用的素材");
        String id=UUID.randomUUID()+"."+ext;File temp=new File(dir(c),id+".part"),dest=new File(dir(c),id);boolean saved=false;
        try{try(FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];long left=size;while(left>0){int n=in.read(b,0,(int)Math.min(b.length,left));if(n<0)throw new EOFException("上传中断");out.write(b,0,n);left-=n;}out.getFD().sync();}
            if(ext.matches("jpg|jpeg|png|webp")){android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(temp.getPath(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("无法识别图片");}
            if(ext.matches("ttf|otf"))android.graphics.Typeface.createFromFile(temp);
            if(ext.matches("mp4|webm|mkv")){android.media.MediaExtractor m=new android.media.MediaExtractor();boolean video=false;try{m.setDataSource(temp.getPath());for(int i=0;i<m.getTrackCount();i++)if(m.getTrackFormat(i).getString("mime").startsWith("video/"))video=true;}finally{m.release();}if(!video)throw new IOException("无法识别视频轨道");}
            if(!temp.renameTo(dest))throw new IOException("保存素材失败");saved=true;c.getSharedPreferences("aurora_library",0).edit().putString(id,name.substring(0,Math.min(160,name.length()))).commit();return new JSONObject().put("id",id).put("name",name).put("bytes",size);
        }finally{if(!saved)temp.delete();}
    }
    private static byte[] digest(File file)throws Exception{java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}return hash.digest();}
    static synchronized byte[] thumbnail(Context c,String id)throws Exception{
        File f=file(c,id,"background"),cache=new File(f.getPath()+".jpg");
        if(!cache.isFile()&&c.getSharedPreferences("aurora_library",0).getString(id,"").startsWith("原桌面背景")){
            for(File original:BackgroundStore.items(c)){
                File preview=new File(original.getPath()+".jpg");
                if(original.length()==f.length()&&preview.isFile()&&Arrays.equals(digest(original),digest(f))){java.nio.file.Files.copy(preview.toPath(),cache.toPath());break;}
            }
        }
        if(!cache.isFile()){
            android.graphics.Bitmap image=null;boolean video=id.endsWith(".mp4")||id.endsWith(".webm")||id.endsWith(".mkv");
            try{
                if(video){android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();try{reader.setDataSource(f.getPath());image=reader.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,480,270);}finally{reader.release();}}
                else{android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.getPath(),options);options.inSampleSize=1;while(options.outWidth/options.inSampleSize>480||options.outHeight/options.inSampleSize>270)options.inSampleSize*=2;options.inJustDecodeBounds=false;image=android.graphics.BitmapFactory.decodeFile(f.getPath(),options);}
                if(image==null)throw new IOException("素材暂时无法生成预览");
                try(FileOutputStream out=new FileOutputStream(cache)){if(!image.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,out))throw new IOException("预览保存失败");}
            }finally{if(image!=null)image.recycle();}
        }
        try(InputStream in=new FileInputStream(cache)){return AuroraThemes.read(in,4*1024*1024);}
    }
    static void serve(Context c,String id,String range,OutputStream out)throws Exception{
        File f=file(c,id,null);long length=f.length(),start=0,end=length-1;boolean partial=range!=null;
        if(partial){if(!range.matches("bytes=\\d+-\\d*")){out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));return;}String[] s=range.substring(6).split("-",-1);start=Long.parseLong(s[0]);if(!s[1].isEmpty())end=Math.min(end,Long.parseLong(s[1]));if(start>end||start>=length){out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"+length+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));return;}}
        String type=id.endsWith(".image")?"image/jpeg":id.endsWith(".png")?"image/png":id.endsWith(".webp")?"image/webp":id.matches(".*\\.(jpg|jpeg)")?"image/jpeg":id.endsWith(".mp4")?"video/mp4":id.endsWith(".webm")?"video/webm":id.endsWith(".mkv")?"video/x-matroska":id.endsWith(".ttf")?"font/ttf":id.endsWith(".otf")?"font/otf":"text/plain; charset=utf-8";
        if(id.endsWith(".image")){android.graphics.BitmapFactory.Options image=new android.graphics.BitmapFactory.Options();image.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.getPath(),image);if(image.outMimeType!=null)type=image.outMimeType;}
        out.write(("HTTP/1.1 "+(partial?"206 Partial Content":"200 OK")+"\r\nContent-Type: "+type+"\r\nAccept-Ranges: bytes\r\nContent-Length: "+(end-start+1)+"\r\n"+(partial?"Content-Range: bytes "+start+"-"+end+"/"+length+"\r\n":"")+"Cache-Control: private, max-age=3600\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
        try(RandomAccessFile source=new RandomAccessFile(f,"r")){source.seek(start);byte[] b=new byte[65536];long left=end-start+1;while(left>0){int n=source.read(b,0,(int)Math.min(left,b.length));if(n<0)break;out.write(b,0,n);left-=n;}}out.flush();
    }
    static synchronized JSONObject install(Context c,String id,String peer)throws Exception{
        File apk=file(c,id,"apk");if(!TvAdb.status().optBoolean("connected"))throw new IOException("请先连接电视调试");
        for(JSONObject j:jobs.values())if(j.optString("state").equals("running"))throw new IOException("另一个安装任务正在运行");
        String key=UUID.randomUUID().toString();JSONObject job=new JSONObject().put("id",key).put("file",id).put("state","running");if(jobs.size()>20)jobs.clear();jobs.put(key,job);
        new Thread(()->{try{WebApps.install(c,apk);synchronized(AuroraLibrary.class){job.put("state","done");}WebAudit.record(c,peer,"后台安装APK",id,200);}catch(Exception e){synchronized(AuroraLibrary.class){try{job.put("state","failed").put("reason",String.valueOf(e.getMessage()));}catch(Exception ignored){}}WebAudit.record(c,peer,"后台安装APK失败",id,503);}},"aurora-install").start();return new JSONObject(job.toString());
    }
    static synchronized JSONArray jobs(){JSONArray a=new JSONArray();for(JSONObject j:jobs.values())a.put(j);return a;}
    static synchronized void remove(Context c,String id,boolean confirmed)throws Exception{
        if(!confirmed)throw new IllegalArgumentException("请先确认删除素材");File source=file(c,id,null);
        if(AuroraThemes.config(c).optJSONObject("themeProfiles")!=null&&AuroraThemes.config(c).getJSONObject("themeProfiles").toString().contains(id)||AuroraSettings.get(c).toString().contains(id)||AuroraBgm.state(c).toString().contains(id))throw new IllegalArgumentException("素材正在使用，请先取消背景、字体或歌词绑定");
        for(JSONObject job:jobs.values())if(job.optString("state").equals("running")&&job.optString("file").equals(id))throw new IllegalArgumentException("安装任务尚未结束");
        if(id.startsWith("native-")){
            android.content.SharedPreferences background=c.getSharedPreferences("background",0);String original=id.substring(7);
            if(!AuroraBackgrounds.active(c)&&(original.equals(background.getString("selected",""))||original.equals(background.getString("ambient_selected",""))))throw new IllegalArgumentException("素材仍在极简桌面或原屏保中使用，请先取消使用");
            BackgroundStore.delete(c,source);if(source.exists())throw new IOException("删除素材失败");return;
        }
        if(!source.delete())throw new IOException("删除素材失败");c.getSharedPreferences("aurora_library",0).edit().remove(id).commit();
    }
}
