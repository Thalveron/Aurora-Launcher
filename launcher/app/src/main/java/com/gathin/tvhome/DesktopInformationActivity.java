package com.gathin.tvhome;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;import org.json.*;
import com.google.zxing.*;import com.google.zxing.common.BitMatrix;
/** Native address/QR and shared update controls, available from every theme. */
public final class DesktopInformationActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(DesktopLanguage.wrap(DesktopViewport.wrap(base)));}

 private LinearLayout content;private TextView status;private final Handler main=new Handler(Looper.getMainLooper());private boolean updatePage;private String addresses="";
 private final Runnable refresh=new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;try{if(updatePage)refreshUpdate();else if(!ConsoleAddresses.list(DesktopInformationActivity.this).toString().equals(addresses))settings();}catch(Exception ignored){}main.postDelayed(this,2000);}};
 private String t(String text){return DesktopLanguage.t(this,text);}private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setFlags(1024,1024);getWindow().getDecorView().setBackgroundColor(0xff101925);
  LinearLayout root=new LinearLayout(this);root.setPadding(dp(28),dp(24),dp(28),dp(24));root.setOrientation(LinearLayout.HORIZONTAL);setContentView(root);
  LinearLayout sidebar=new LinearLayout(this);sidebar.setOrientation(LinearLayout.VERTICAL);root.addView(sidebar,new LinearLayout.LayoutParams(dp(235),-1));
  title(sidebar,t("桌面信息"),28);button(sidebar,t("桌面设置"),()->{updatePage=false;settings();});button(sidebar,t("桌面更新"),()->{updatePage=true;updates();});button(sidebar,t("返回"),this::finish);
  ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1);right.leftMargin=dp(30);root.addView(scroll,right);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(14),0,dp(14),dp(24));scroll.addView(content);
  try{title(sidebar,"Aurora Launcher\nv"+LauncherUpdater.version(this),16);}catch(Exception ignored){}
  LauncherUpdater.start(this);if("updates".equals(getIntent().getStringExtra("page"))){updatePage=true;updates();}else settings();main.postDelayed(refresh,2000);sidebar.getChildAt(1).requestFocus();
 }
 @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
 private TextView title(LinearLayout parent,String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setPadding(0,dp(10),0,dp(12));parent.addView(v);return v;}
 private Button button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(20);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.bottomMargin=dp(12);parent.addView(b,p);b.setOnClickListener(v->action.run());return b;}
 private void settings(){
  content.removeAllViews();title(content,t("桌面设置"),30);title(content,t("手机扫码，在浏览器打开完整管理地址。首次使用请自行设置账号和密码。"),18);
  try{JSONArray list=ConsoleAddresses.list(this);addresses=list.toString();if(list.length()==0){title(content,t("未检测到局域网地址，请先连接网络。"),22);return;}
   for(int i=0;i<list.length();i++){String url=list.getString(i);TextView address=title(content,url,22);address.setTextIsSelectable(true);
    if(i==0){java.util.Map<EncodeHintType,Object> hints=new java.util.EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(EncodeHintType.MARGIN,3);BitMatrix matrix=new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,650,650,hints);int[] pixels=new int[650*650];for(int y=0;y<650;y++)for(int x=0;x<650;x++)pixels[y*650+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;ImageView qr=new ImageView(this);qr.setImageBitmap(Bitmap.createBitmap(pixels,650,650,Bitmap.Config.ARGB_8888));qr.setContentDescription(url);content.addView(qr,new LinearLayout.LayoutParams(dp(350),dp(350)));}
   }title(content,t("地址会跟随本机 IP、后台端口和访问路径变化；二维码不包含密码。"),17);
  }catch(Exception e){title(content,String.valueOf(e.getMessage()),20);}
 }
 private void updates(){
  content.removeAllViews();title(content,t("桌面更新"),30);status=title(content,t("正在读取更新状态…"),19);
  Switch check=new Switch(this);check.setText(t("自动检测新版"));Switch automatic=new Switch(this);automatic.setText(t("自动更新（空闲时安装）"));
  try{JSONObject s=LauncherUpdater.state(this);check.setChecked(s.optBoolean("autoCheck"));automatic.setChecked(s.optBoolean("automatic"));}catch(Exception ignored){}
  content.addView(check);content.addView(automatic);check.setOnCheckedChangeListener((v,on)->configure("autoCheck",on,check));automatic.setOnCheckedChangeListener((v,on)->configure("automatic",on,automatic));
  button(content,t("检查新版"),()->request("check"));button(content,t("手动更新"),()->new AlertDialog.Builder(this).setTitle(t("手动更新")).setMessage(t("将下载并校验新版，保留现有设置。若系统要求确认，请按安装提示操作。")).setNegativeButton(t("取消"),null).setPositiveButton(t("继续"),(d,w)->request("install")).show());
  button(content,t("回退上一个版本"),()->new AlertDialog.Builder(this).setTitle(t("回退上一个版本")).setMessage(t("将恢复更新前的安装包和设置。更新后修改的设置将被覆盖。")).setNegativeButton(t("取消"),null).setPositiveButton(t("确认回退"),(d,w)->request("rollback")).show());
  title(content,t("自动回退保护：安装失败、无法启动或启动健康检查失败时恢复上一版。任意功能性异常并不一定能自动识别。"),16);refreshUpdate();
 }
 private void configure(String key,boolean on,Switch control){try{LauncherUpdater.configure(this,new JSONObject().put(key,on));WebAudit.record(this,"127.0.0.1","电视端修改桌面更新设置",key+"="+on,200);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();control.setOnCheckedChangeListener(null);control.setChecked(!on);control.setOnCheckedChangeListener((v,value)->configure(key,value,control));}}
 private void request(String action){try{LauncherUpdater.request(this,action);WebAudit.record(this,"127.0.0.1","电视端桌面更新",action,200);refreshUpdate();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void refreshUpdate(){if(status==null)return;try{JSONObject s=LauncherUpdater.state(this),latest=s.optJSONObject("latest"),previous=s.optJSONObject("previous");status.setText("Aurora Launcher v"+s.getString("currentVersion")+"\n"+(latest==null?t("尚未检查新版"):t("最新版本")+" v"+latest.optString("version"))+"\n"+s.optString("message")+(s.optString("phase").equals("downloading")?" "+s.optInt("progress")+"%":"")+"\n"+(s.optBoolean("maintenanceAvailable")?t("自动安装与回退可用"):s.optString("maintenanceReason"))+"\n"+t("上一版本")+"："+(previous==null?t("暂无"):previous.optString("version")));}catch(Exception e){status.setText(String.valueOf(e.getMessage()));}}
}
