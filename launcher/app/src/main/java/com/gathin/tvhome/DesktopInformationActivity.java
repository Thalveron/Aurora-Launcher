package com.gathin.tvhome;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;import org.json.*;
import com.google.zxing.*;import com.google.zxing.common.BitMatrix;
/** Native address/QR and shared update controls, available from every theme. */
public final class DesktopInformationActivity extends Activity {
 private LinearLayout content;private TextView status;private final Handler main=new Handler(Looper.getMainLooper());private boolean updatePage;private String addresses="";private float layoutUnit=1f;
 private final Runnable refresh=new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;try{if(updatePage)refreshUpdate();else if(!ConsoleAddresses.list(DesktopInformationActivity.this).toString().equals(addresses))settings();}catch(Exception ignored){}main.postDelayed(this,2000);}};
 private String t(String text){return DesktopLanguage.t(this,text);}private int dp(int n){return Math.round(n*layoutUnit);}
 @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(DesktopViewport.wrap(DesktopLanguage.wrap(base)));}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);Point screen=new Point();getWindowManager().getDefaultDisplay().getSize(screen);layoutUnit=Math.min(screen.x/1280f,screen.y/720f);getWindow().setFlags(1024,1024);getWindow().getDecorView().setBackgroundColor(0xff101925);
  getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams window=getWindow().getAttributes();window.dimAmount=.28f;window.gravity=Gravity.END;getWindow().setAttributes(window);
  LinearLayout root=new LinearLayout(this);root.setPadding(dp(24),dp(20),dp(24),dp(20));root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff101925);setContentView(root);
  getWindow().setLayout(Math.round(screen.x*.56f),-1);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
  TextView name=new TextView(this);name.setText(t("桌面信息"));name.setTextColor(-1);name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(25));header.addView(name,new LinearLayout.LayoutParams(0,dp(54),1));
  Button close=new Button(this);close.setText("×");close.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(25));header.addView(close,new LinearLayout.LayoutParams(dp(54),dp(48)));close.setOnClickListener(v->finish());
  LinearLayout tabs=new LinearLayout(this);root.addView(tabs);for(String label:new String[]{"桌面设置","桌面更新"}){Button tab=new Button(this);tab.setText(t(label));tab.setAllCaps(false);tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(18));tabs.addView(tab,new LinearLayout.LayoutParams(0,dp(50),1));tab.setOnClickListener(v->{updatePage=label.equals("桌面更新");if(updatePage)updates();else settings();});}
  ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(12),0,dp(12));scroll.addView(content);
  try{title(root,"Aurora Launcher v"+LauncherUpdater.version(this),14);}catch(Exception ignored){}
  LauncherUpdater.start(this);if("updates".equals(getIntent().getStringExtra("page"))){updatePage=true;updates();}else settings();main.postDelayed(refresh,2000);tabs.getChildAt(0).requestFocus();
 }
 @Override protected void onResume(){super.onResume();LauncherUpdater.resumeInstaller(this);}
 @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
 private TextView title(LinearLayout parent,String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(size));v.setTextColor(Color.WHITE);v.setPadding(0,dp(10),0,dp(12));parent.addView(v);return v;}
 private Button button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.bottomMargin=dp(12);parent.addView(b,p);b.setOnClickListener(v->action.run());return b;}
 private void settings(){
  content.removeAllViews();title(content,t("桌面设置"),30);title(content,t("手机扫码打开完整管理地址。登录保护可在安全设置中开关。"),18);
  try{JSONArray list=ConsoleAddresses.list(this);addresses=list.toString();if(list.length()==0){title(content,t("未检测到局域网地址，请先连接网络。"),22);return;}
   for(int i=0;i<list.length();i++){String url=list.getString(i);TextView address=title(content,url,22);address.setTextIsSelectable(true);
    if(i==0){java.util.Map<EncodeHintType,Object> hints=new java.util.EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(EncodeHintType.MARGIN,3);BitMatrix matrix=new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,650,650,hints);int[] pixels=new int[650*650];for(int y=0;y<650;y++)for(int x=0;x<650;x++)pixels[y*650+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;ImageView qr=new ImageView(this);qr.setImageBitmap(Bitmap.createBitmap(pixels,650,650,Bitmap.Config.ARGB_8888));qr.setContentDescription(url);content.addView(qr,new LinearLayout.LayoutParams(dp(280),dp(280)));}
   }title(content,t("地址会跟随本机 IP、后台端口和访问路径变化；二维码不包含密码。"),17);
  }catch(Exception e){title(content,String.valueOf(e.getMessage()),20);}
 }
 private void updates(){
  content.removeAllViews();title(content,t("桌面更新"),30);status=title(content,t("正在读取更新状态…"),19);
  Switch check=new Switch(this);check.setText(t("自动检测新版"));Switch automatic=new Switch(this);automatic.setText(t("自动更新（空闲时安装）"));
  try{JSONObject s=LauncherUpdater.state(this);check.setChecked(s.optBoolean("autoCheck"));automatic.setChecked(s.optBoolean("automatic"));}catch(Exception ignored){}
  for(Switch toggle:new Switch[]{check,automatic}){toggle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(18));toggle.setMinHeight(dp(38));content.addView(toggle);}check.setOnCheckedChangeListener((v,on)->configure("autoCheck",on,check));automatic.setOnCheckedChangeListener((v,on)->configure("automatic",on,automatic));
  button(content,t("检查新版本"),()->request("check"));
  button(content,t("指定版本更新"),this::chooseVersion);
  button(content,t("手动更新（上传 APK）"),()->{updatePage=false;settings();Toast.makeText(this,t("请扫码进入网页的桌面更新，上传 APK 后确认安装。"),Toast.LENGTH_LONG).show();});
  button(content,t("回退上一个版本"),()->new AlertDialog.Builder(this).setTitle(t("回退上一个版本")).setMessage(t("将恢复更新前的安装包和设置。更新后修改的设置将被覆盖。")).setNegativeButton(t("取消"),null).setPositiveButton(t("确认回退"),(d,w)->request("rollback")).show());
  title(content,t("自动回退保护：安装失败、无法启动或启动健康检查失败时恢复上一版。任意功能性异常并不一定能自动识别。"),16);refreshUpdate();
 }
 private void chooseVersion(){
  EditText version=new EditText(this);version.setSingleLine(true);version.setHint("例如 0.1.3");version.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(20));
  new AlertDialog.Builder(this).setTitle(t("指定版本更新")).setView(version).setNegativeButton(t("取消"),null).setPositiveButton(t("下载并安装"),(d,w)->{try{LauncherUpdater.requestVersion(this,version.getText().toString().trim());refreshUpdate();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}).show();
 }
 private void configure(String key,boolean on,Switch control){try{LauncherUpdater.configure(this,new JSONObject().put(key,on));WebAudit.record(this,"127.0.0.1","电视端修改桌面更新设置",key+"="+on,200);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();control.setOnCheckedChangeListener(null);control.setChecked(!on);control.setOnCheckedChangeListener((v,value)->configure(key,value,control));}}
 private void request(String action){try{LauncherUpdater.request(this,action);WebAudit.record(this,"127.0.0.1","电视端桌面更新",action,200);refreshUpdate();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void refreshUpdate(){if(status==null)return;try{JSONObject s=LauncherUpdater.state(this),latest=s.optJSONObject("latest"),previous=s.optJSONObject("previous");status.setText("Aurora Launcher v"+s.getString("currentVersion")+"\n"+(latest==null?t("尚未检查新版"):t("最新版本")+" v"+latest.optString("version"))+"\n"+s.optString("message")+(s.optString("phase").equals("downloading")?" "+s.optInt("progress")+"%":"")+"\n"+(s.optBoolean("maintenanceAvailable")?t("自动安装与回退可用"):s.optString("maintenanceReason"))+"\n"+t("上一版本")+"："+(previous==null?t("暂无"):previous.optString("version")));}catch(Exception e){status.setText(String.valueOf(e.getMessage()));}}
}
