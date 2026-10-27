package com.gathin.tvhome;
import android.content.*;import android.content.res.Configuration;import android.graphics.Point;import android.view.*;
final class DesktopViewport {
 static boolean changed(Context c){
  try{Point size=new Point();((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(size);return c.getResources().getConfiguration().densityDpi!=ViewportScale.densityDpi(size.x,size.y);}catch(Exception ignored){return false;}
 }
 static Context wrap(Context base){
  try{
   Point size=new Point();WindowManager windows=(WindowManager)base.getSystemService(Context.WINDOW_SERVICE);windows.getDefaultDisplay().getSize(size);
   Configuration cfg=new Configuration(base.getResources().getConfiguration());cfg.densityDpi=ViewportScale.densityDpi(size.x,size.y);cfg.fontScale=1f;
   float density=cfg.densityDpi/160f;cfg.screenWidthDp=Math.round(size.x/density);cfg.screenHeightDp=Math.round(size.y/density);cfg.smallestScreenWidthDp=Math.min(cfg.screenWidthDp,cfg.screenHeightDp);
   return base.createConfigurationContext(cfg);
  }catch(Exception unavailable){return base;}
 }
}
