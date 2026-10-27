package com.gathin.tvhome;
import android.content.Context;import java.io.*;import java.util.zip.*;import java.nio.file.Files;
/** Settings snapshot before package replacement. Full reset additionally snapshots managed media. */
final class UpdateBackup {
 static File create(Context c,boolean full)throws Exception{
  File out=new File(c.getCacheDir(),"launcher-update-backup.zip");long[] bytes={0};long limit=full?2L*1024*1024*1024:256L*1024*1024;
  try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))){
   for(String dir:new String[]{"shared_prefs","files","databases"})add(zip,new File(c.getDataDir(),dir),"private/"+dir,full,false,bytes,limit,c.getCacheDir());
   File external=c.getExternalFilesDir(null);if(external!=null)add(zip,external,"external",full,true,bytes,limit,c.getCacheDir());
  }catch(Exception e){out.delete();throw e;}return out;
 }
 private static void add(ZipOutputStream zip,File file,String path,boolean full,boolean external,long[] bytes,long limit,File cache)throws Exception{
  if(!file.exists()||Files.isSymbolicLink(file.toPath())||path.startsWith("private/files/updater/")||path.equals("private/files/updater")||path.startsWith("external/web-apk"))return;
  if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("无法读取设置快照");for(File child:children)add(zip,child,path+"/"+child.getName(),full,external,bytes,limit,cache);return;}
  if(external&&!full&&!(path.endsWith(".json")||path.endsWith(".css")||path.endsWith(".xml")||path.endsWith(".lrc")))return;
  bytes[0]+=file.length();if(bytes[0]>limit||cache.getUsableSpace()<file.length()+32L*1024*1024)throw new IOException("恢复快照所需空间不足，未执行更新或清空");
  zip.putNextEntry(new ZipEntry(path));try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();
 }
}
