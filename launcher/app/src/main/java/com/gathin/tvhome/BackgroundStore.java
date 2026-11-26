package com.gathin.tvhome;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import java.io.File;
import java.io.IOException;

final class BackgroundStore {
    static File directory(Context c) { File d = new File(c.getFilesDir(), "backgrounds"); d.mkdirs(); return d; }
    static synchronized java.util.List<File> items(Context c) {
        File d = directory(c);
        for (String k : new String[]{"image", "video"}) {
            File old = new File(c.getFilesDir(), "personal-background." + (k.equals("video") ? "mp4" : "image"));
            File target = new File(d, k + "-legacy." + (k.equals("video") ? "mp4" : "image"));
            if (old.isFile() && old.renameTo(target) && mode(c).equals(k)) c.getSharedPreferences("background",0).edit().putString("selected",target.getName()).commit();
        }
        File[] all = d.listFiles((dir, name) -> name.matches("(image|video)-[a-zA-Z0-9-]+\\.(mp4|image)"));
        java.util.List<File> result = new java.util.ArrayList<>();
        if (all != null) java.util.Collections.addAll(result, all);
        android.content.SharedPreferences prefs=c.getSharedPreferences("background",0);
        String desktop=mode(c).equals("default")?"":prefs.getString("selected","");
        String ambientMode=prefs.getString("ambient_mode","follow");
        String saver=ambientMode.equals("follow")?desktop:ambientMode.equals("default")?"":prefs.getString("ambient_selected","");
        final String desktopId=desktop,saverId=saver;
        result.sort((a,b) -> {int x=rank(a.getName(),desktopId,saverId),y=rank(b.getName(),desktopId,saverId);if(x!=y)return Integer.compare(x,y);int time=Long.compare(b.lastModified(),a.lastModified());return time!=0?time:a.getName().compareTo(b.getName());}); return result;
    }
    private static int rank(String id,String desktop,String saver){return id.equals(desktop)?0:id.equals(saver)?1:2;}
    static String kind(File f) { return f.getName().startsWith("video-")||f.getName().matches(".*\\.(mp4|webm|mkv)") ? "video" : "image"; }
    static String mode(Context c, boolean ambient) {
        String mode=ambient?c.getSharedPreferences("background",0).getString("ambient_mode","follow"):"follow";
        return "follow".equals(mode)?mode(c):mode;
    }
    static File file(Context c,String mode,boolean ambient) {
        if(ambient&&!"follow".equals(c.getSharedPreferences("background",0).getString("ambient_mode","follow"))) {
            File selected=byId(c,c.getSharedPreferences("background",0).getString("ambient_selected",""));
            return selected==null?new File(directory(c),"missing"):selected;
        }
        return file(c,mode);
    }
    static void selectAmbient(Context c,File file) {
        c.getSharedPreferences("background",0).edit().putString("ambient_mode",kind(file)).putString("ambient_selected",file.getName()).putLong("revision",System.currentTimeMillis()).commit();
    }
    static File byId(Context c, String id) { for (File f : items(c)) if (f.getName().equals(id)) return f; return null; }
    static File file(Context c, String kind) {
        java.util.List<File> all = items(c);
        String selected = c.getSharedPreferences("background",0).getString("selected", "");
        for (File f : all) if (kind(f).equals(kind) && f.getName().equals(selected)) return f;
        for (File f : all) if (kind(f).equals(kind)) return f;
        return new File(directory(c), "missing");
    }
    static void selectFile(Context c, File f) {
        c.getSharedPreferences("background",0).edit().putString("selected",f.getName()).commit(); select(c,kind(f));
    }
    static void delete(Context c, File f) {
        try{org.json.JSONObject slides=AmbientSlideshow.state(c);org.json.JSONArray ids=slides.getJSONArray("ids"),keep=new org.json.JSONArray();for(int i=0;i<ids.length();i++)if(!ids.getString(i).equals(f.getName())&&byId(c,ids.getString(i))!=null)keep.put(ids.getString(i));c.getSharedPreferences("background",0).edit().putString("slides",keep.toString()).putBoolean("slides_enabled",slides.getBoolean("enabled")&&keep.length()>0).putLong("revision",System.currentTimeMillis()).commit();}catch(Exception ignored){}
        if(f.getName().equals(c.getSharedPreferences("background",0).getString("ambient_selected","")))c.getSharedPreferences("background",0).edit().putString("ambient_mode","follow").remove("ambient_selected").putLong("revision",System.currentTimeMillis()).commit();
        if (f.getName().equals(c.getSharedPreferences("background",0).getString("selected",""))) select(c,"default");
        f.delete(); new File(f.getPath()+".jpg").delete();
    }
    static File thumbnail(Context c, File f) {
        File t = new File(f.getPath()+".jpg"); if (t.isFile()) return t;
        android.graphics.Bitmap bitmap = null;
        try {
            if (kind(f).equals("video")) {
                MediaMetadataRetriever m = new MediaMetadataRetriever();
                try { m.setDataSource(f.getPath()); bitmap = m.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270); } finally { m.release(); }
            } else {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds=true; BitmapFactory.decodeFile(f.getPath(),o);
                o.inSampleSize=1; while(o.outWidth/o.inSampleSize>480 || o.outHeight/o.inSampleSize>270) o.inSampleSize*=2;
                o.inJustDecodeBounds=false; bitmap=BitmapFactory.decodeFile(f.getPath(),o);
            }
            if(bitmap!=null) {
                float scale=Math.min(1f, Math.min(480f/bitmap.getWidth(),270f/bitmap.getHeight()));
                android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true);
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(t)) { small.compress(android.graphics.Bitmap.CompressFormat.JPEG,80,out); }
                if(small!=bitmap)small.recycle();
            }
        } catch(Exception | OutOfMemoryError ignored) {} finally { if(bitmap!=null)bitmap.recycle(); }
        return t;
    }
    static String mode(Context c) { return c.getSharedPreferences("background", 0).getString("mode", "default"); }
    static void select(Context c, String kind) {
        c.getSharedPreferences("background", 0).edit().putString("mode", kind)
                .putLong("revision", System.currentTimeMillis()).commit();
        BridgePreview.sync(c);
    }
    static synchronized void save(Context c, File temp, String kind) throws Exception {
        save(c,temp,kind,false);
    }
    static synchronized void save(Context c, File temp, String kind, boolean ambient) throws Exception {
        if ("image".equals(kind)) {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temp.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0 || (long)o.outWidth * o.outHeight > 100_000_000L)
                throw new IOException("图片格式不支持，或图片超过 1 亿像素");
        } else if ("video".equals(kind)) {
            MediaMetadataRetriever m = new MediaMetadataRetriever();
            MediaExtractor e = new MediaExtractor();
            try {
                m.setDataSource(temp.getPath());
                long duration = Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                int w = Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int h = Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                if (duration <= 0 || duration > 30_000) throw new IOException("请上传 30 秒以内的视频");
                if (w <= 0 || h <= 0) throw new IOException("无法读取视频分辨率");
                e.setDataSource(temp.getPath()); boolean supported = false;
                for (int i = 0; i < e.getTrackCount(); i++) {
                    String mime = e.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        if (!"video/avc".equals(mime) && !"video/hevc".equals(mime)) throw new IOException("请使用 H.264 或 H.265 编码的 MP4 视频");
                        supported = true;
                    }
                }
                if (!supported) throw new IOException("文件没有可用的视频轨道");
            } finally { try { m.release(); } finally { e.release(); } }
        } else throw new IOException("不支持的背景类型");
        java.util.List<File> all = items(c); long bytes = temp.length();
        for(File existing:all) bytes += existing.length();
        if(all.size() >= 30 || bytes > 500L*1024*1024) throw new IOException("背景库已满（最多 30 项、500 MB），请先删除不用的背景");
        File target = new File(directory(c), kind + "-" + java.util.UUID.randomUUID() + (kind.equals("video") ? ".mp4" : ".image"));
        if (!temp.renameTo(target)) throw new IOException("保存失败，请重试");
        thumbnail(c,target); // Upload only adds to the library; selection always requires an explicit user action.
    }
}
