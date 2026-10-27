package com.gathin.tvhome;
import android.content.Context;import android.graphics.*;import java.io.*;import java.util.*;import org.json.*;
/** Authenticated, explicitly captured screenshots in private app storage. */
final class AuroraScreenshots {
 static File dir(Context c){File f=new File(c.getFilesDir(),"screenshots");if(!f.exists())f.mkdirs();return f;}
 static File file(Context c,String id){if(id==null||!id.matches("[a-f0-9]{32}\\.png"))throw new IllegalArgumentException("截图不存在");File f=new File(dir(c),id);if(!f.isFile())throw new IllegalArgumentException("截图不存在");return f;}
 static JSONObject item(Context c,File f)throws Exception{JSONObject m=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(dir(c),f.getName()+".json").toPath()),"UTF-8"));return m.put("id",f.getName()).put("url",ConsolePath.base(c)+"api/v1/system/screenshots/file/"+f.getName()).put("bytes",f.length());}
 static synchronized JSONArray list(Context c)throws Exception{File[] files=dir(c).listFiles((d,n)->n.endsWith(".png"));if(files==null)files=new File[0];Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));JSONArray out=new JSONArray();for(File f:files)try{out.put(item(c,f));}catch(Exception ignored){}return out;}
 static synchronized JSONObject capture(Context c,boolean fourK)throws Exception{
  File[] files=dir(c).listFiles();long size=0;int count=0;if(files!=null)for(File f:files){size+=f.length();if(f.getName().endsWith(".png"))count++;}
  if(count>=50||size>512L*1024*1024)throw new IOException("截图库已满，请手动删除旧截图");
  byte[] png=TvAdb.screenPng(c);Bitmap original=BitmapFactory.decodeByteArray(png,0,png.length);if(original==null)throw new IOException("截图读取失败");
  Bitmap result=original;File target=new File(dir(c),UUID.randomUUID().toString().replace("-","")+".png");
  try{
   int sw=original.getWidth(),sh=original.getHeight();boolean scaled=fourK&&(sw!=3840||sh!=2160);
   if(scaled){result=Bitmap.createBitmap(3840,2160,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.drawColor(Color.BLACK);float scale=Math.min(3840f/sw,2160f/sh);float w=sw*scale,h=sh*scale;canvas.drawBitmap(original,null,new RectF((3840-w)/2,(2160-h)/2,(3840+w)/2,(2160+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));}
   try(FileOutputStream out=new FileOutputStream(target)){if(!result.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("截图保存失败");}
   JSONObject meta=new JSONObject().put("createdAt",System.currentTimeMillis()).put("width",result.getWidth()).put("height",result.getHeight()).put("sourceWidth",sw).put("sourceHeight",sh).put("upscaled",scaled);
   try(FileOutputStream out=new FileOutputStream(new File(dir(c),target.getName()+".json"))){out.write(meta.toString().getBytes("UTF-8"));}
   return item(c,target);
  }catch(Exception e){target.delete();new File(dir(c),target.getName()+".json").delete();throw e;}
  finally{if(result!=original)result.recycle();original.recycle();}
 }
 static synchronized void delete(Context c,String id){File f=file(c,id);if(!f.delete())throw new IllegalStateException("删除失败");new File(dir(c),id+".json").delete();}
}