package com.gathin.tvhome;
import android.content.Context;import java.io.*;
/** Replace only shipped console/core files on an APK change; user themes/media remain separate. */
final class ManagedWebAssets {
 static void sync(Context c)throws Exception{
  long version=UpdatePackages.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0));
  if(c.getSharedPreferences("managed_web_assets",0).getLong("version",-1)==version)return;
  File base=new File(c.getExternalFilesDir(null),"aurora");
  copy(c,"aurora/console",new File(base,"console"));copy(c,"aurora/core",new File(base,"core"));File cards=new File(base,"themes/cards");cards.mkdirs();copy(c,"aurora/themes/cards/theme.js",new File(cards,"theme.js"));
  if(!c.getSharedPreferences("managed_web_assets",0).edit().putLong("version",version).commit())throw new IOException("网页版本标记保存失败");
 }
 private static void copy(Context c,String source,File target)throws Exception{
  String[] entries=c.getAssets().list(source);if(entries!=null&&entries.length>0){if(!target.isDirectory()&&!target.mkdirs())throw new IOException("无法创建网页资源目录");for(String entry:entries)copy(c,source+"/"+entry,new File(target,entry));return;}
  File temp=new File(target.getPath()+".next");try(InputStream in=c.getAssets().open(source);OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  if(target.exists()&&!target.delete())throw new IOException("无法替换网页资源");if(!temp.renameTo(target))throw new IOException("网页资源保存失败");
 }
}
