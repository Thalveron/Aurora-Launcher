package com.gathin.tvhome;
import android.app.*;import android.os.*;import android.content.*;import android.net.Uri;
/** Only the Android installer decides whether installation proceeds. */
public final class UpdateInstallActivity extends Activity {
 private boolean launched;
 @Override public void onCreate(Bundle state){super.onCreate(state);launched=state!=null&&state.getBoolean("launched");getWindow().setBackgroundDrawableResource(android.R.color.transparent);
  if(!launched){launched=true;Intent install=new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(Uri.parse(getIntent().getStringExtra("uri"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
   try{startActivityForResult(install,91);}catch(android.content.ActivityNotFoundException unavailable){try{install.setAction(Intent.ACTION_VIEW).setDataAndType(install.getData(),"application/vnd.android.package-archive");startActivityForResult(install,91);}catch(Exception failure){android.widget.Toast.makeText(this,"系统没有可用的APK安装界面",android.widget.Toast.LENGTH_LONG).show();LauncherUpdater.cancelInstall(this,getIntent().getStringExtra("job"));finish();}}catch(Exception failure){android.widget.Toast.makeText(this,"无法打开安装界面："+failure.getMessage(),android.widget.Toast.LENGTH_LONG).show();LauncherUpdater.cancelInstall(this,getIntent().getStringExtra("job"));finish();}
  }
 }
 @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("launched",launched);super.onSaveInstanceState(state);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==91){if(result!=RESULT_OK)LauncherUpdater.cancelInstall(this,getIntent().getStringExtra("job"));finish();}}
}
