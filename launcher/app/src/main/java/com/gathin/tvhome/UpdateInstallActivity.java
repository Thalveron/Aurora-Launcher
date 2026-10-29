package com.gathin.tvhome;
import android.app.Activity;import android.os.Bundle;import android.content.Intent;import android.net.Uri;import android.widget.*;import java.io.File;
/** Keeps the validated upload across the system's first-install permission screen. */
public final class UpdateInstallActivity extends Activity {
 private static final int PERMISSION=1,INSTALL=2;private boolean waiting,installing;private TextView status;
 @Override public void onCreate(Bundle state){super.onCreate(state);waiting=state!=null&&state.getBoolean("waiting");installing=state!=null&&state.getBoolean("installing");
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(40,32,40,32);panel.setBackgroundColor(0xff142130);status=new TextView(this);status.setTextColor(-1);status.setTextSize(20);status.setText("安装包已保留，等待系统安装确认");panel.addView(status);Button resume=new Button(this);resume.setText("继续安装");resume.setOnClickListener(v->advance());panel.addView(resume);Button close=new Button(this);close.setText("暂不安装");close.setOnClickListener(v->finish());panel.addView(close);setContentView(panel);if(state==null)advance();
 }
 @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("waiting",waiting);out.putBoolean("installing",installing);super.onSaveInstanceState(out);}
 @Override protected void onResume(){super.onResume();if(waiting&&getPackageManager().canRequestPackageInstalls()){waiting=false;advance();}}
 private void advance(){if(installing)return;try{
  File apk=LauncherUpdater.pendingApk(this);
  boolean vendor=false;
  if(!getPackageManager().canRequestPackageInstalls()){
   Intent permission=new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));
   try{if(permission.resolveActivity(getPackageManager())!=null){waiting=true;status.setText("请允许 Aurora Launcher 安装应用，返回后自动继续，无需重新上传");LauncherUpdater.installerStatus(this,"install_permission",status.getText().toString());startActivityForResult(permission,PERMISSION);return;}}catch(android.content.ActivityNotFoundException unavailable){/* Some vendor builds advertise a removed settings page. */}
   vendor=true;LauncherUpdater.installerStatus(this,"install_permission","此电视未提供逐应用安装授权页，交由电视安装器检查授权并确认安装");
  }
  waiting=false;installing=true;Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",apk);LauncherUpdater.installerStatus(this,"user_confirmation","请在电视上选择安装或取消，原设置保持不变");startActivityForResult(new Intent(vendor?Intent.ACTION_VIEW:Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").putExtra(Intent.EXTRA_RETURN_RESULT,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),INSTALL);
 }catch(Exception error){waiting=false;installing=false;status.setText("未能打开安装确认："+error.getMessage()+"。安装包已保留，可点击继续安装。");LauncherUpdater.installerStatus(this,"install_permission",status.getText().toString());}}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PERMISSION){waiting=false;if(getPackageManager().canRequestPackageInstalls())advance();}else if(request==INSTALL){installing=false;LauncherUpdater.installerStatus(this,"install_permission","系统安装已返回；若取消，可点击继续安装，无需重新上传");finish();}}
}
