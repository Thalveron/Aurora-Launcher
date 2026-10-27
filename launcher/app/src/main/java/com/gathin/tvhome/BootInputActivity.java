package com.gathin.tvhome;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
/** Boot-only handoff; normal Home actions still open MainActivity. */
public final class BootInputActivity extends Activity {
 static boolean handled(Context c) {
  int boot=Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);
  return boot>=0 && c.createDeviceProtectedStorageContext().getSharedPreferences("boot_input",0).getInt("handled",-2)==boot;
 }
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  if(handled(this)){finish();return;}
  int boot=Settings.Global.getInt(getContentResolver(),"boot_count",-1);
  createDeviceProtectedStorageContext().getSharedPreferences("boot_input",0).edit().putInt("handled",boot).commit();
  UploadService.ensureStarted(this);
  new Thread(()->{
   try { WebInputs.open(this,"EN_TCL_HDMI1"); android.util.Log.i("TvHome","Boot HDMI 1 selected"); }
   catch(Exception failure){android.util.Log.w("TvHome","Boot HDMI 1 failed",failure);startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
   runOnUiThread(this::finish);
  },"boot-hdmi").start();
 }
}
