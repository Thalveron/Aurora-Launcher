package com.gathin.tvhome;
import android.content.*;import android.content.pm.*;import android.provider.Settings;import org.json.*;

/** HOME takeover is explicit, verified, reversible and never targets settings packages. */
final class DefaultHome {
 private static Intent home(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
 private static String current(Context c){ResolveInfo r=c.getPackageManager().resolveActivity(home(),PackageManager.MATCH_DEFAULT_ONLY);return r==null||r.activityInfo==null?"":r.activityInfo.packageName;}
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("aurora_default_home",0);}
 private static boolean disabled(Context c,String pkg){try{int value=c.getPackageManager().getApplicationEnabledSetting(pkg);return value==2||value==3||value==4;}catch(Exception e){return false;}}
 private static boolean safe(Context c,String pkg){
  java.util.Set<String> homes=new java.util.HashSet<>(),protectedPackages=new java.util.HashSet<>();for(ResolveInfo r:c.getPackageManager().queryIntentActivities(home(),PackageManager.MATCH_DISABLED_COMPONENTS))if(r.activityInfo!=null)homes.add(r.activityInfo.packageName);for(String action:new String[]{Settings.ACTION_SETTINGS,Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_DISPLAY_SETTINGS,Settings.ACTION_BLUETOOTH_SETTINGS}){ResolveInfo r=c.getPackageManager().resolveActivity(new Intent(action),0);if(r!=null&&r.activityInfo!=null)protectedPackages.add(r.activityInfo.packageName);}return DefaultHomeRules.mayDisable(pkg,c.getPackageName(),homes,protectedPackages);
 }
 static JSONObject status(Context c)throws Exception{
  String previous=prefs(c).getString("previous","");boolean connected=TvAdb.status().optBoolean("connected");
  return new JSONObject().put("isDefault",c.getPackageName().equals(current(c))).put("currentPackage",current(c)).put("previousPackage",previous).put("originalDisabled",!previous.isEmpty()&&disabled(c,previous)).put("canManage",connected).put("canRestore",connected&&!previous.isEmpty()&&disabled(c,previous)).put("reason",connected?"可设置默认主页；原桌面只有通过设置保护检查后才可停用":"未连接本机调试，可打开系统默认桌面选择；不能保证停用原系统桌面");
 }
 static void openSystemChooser(Context c){
  Intent intent=new Intent(c,HomeSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(intent);
 }
 static synchronized JSONObject takeOver(Context c,JSONObject body)throws Exception{
  AuroraSettings.only(body,"confirmed","disableOriginal");if(!Boolean.TRUE.equals(body.opt("confirmed")))throw new IllegalArgumentException("请确认更改默认桌面");
  if(body.has("disableOriginal")&&!(body.get("disableOriginal") instanceof Boolean))throw new IllegalArgumentException("disableOriginal 必须为布尔值");
  if(!TvAdb.status().optBoolean("connected")){WebAudit.record(c,"电视/网页","打开默认桌面选择","无本机调试",200);openSystemChooser(c);return status(c).put("message","请在电视系统页面选择 Aurora Launcher；此设备尚不能停用原桌面");}
  String previous=current(c);if(previous.equals(c.getPackageName())||previous.equals("android"))previous=prefs(c).getString("previous","");
  if(safe(c,previous)&&!prefs(c).edit().putString("previous",previous).commit())throw new IllegalStateException("无法保存恢复信息");
  String component=new ComponentName(c,MainActivity.class).flattenToShortString();String output=TvAdb.shell(c,"cmd package set-home-activity --user "+(c.getApplicationInfo().uid/100000)+" "+component);
  if(!c.getPackageName().equals(current(c)))throw new IllegalStateException("系统未接受默认桌面设置："+output);
  String message="已设为默认桌面";
  if(body.optBoolean("disableOriginal",true)&&safe(c,previous)){
   String result=TvAdb.shell(c,"pm disable-user --user "+(c.getApplicationInfo().uid/100000)+" "+previous);
   if(disabled(c,previous))message+="，原桌面已停用（可恢复）";else message+="；系统拒绝停用原桌面："+result;
  }else if(body.optBoolean("disableOriginal",true))message+="；未发现可安全停用的独立原桌面，保留系统组件";
  WebAudit.record(c,"电视/网页","设置默认桌面",message,200);return status(c).put("message",message);
 }
 static synchronized JSONObject restore(Context c,JSONObject body)throws Exception{
  AuroraSettings.only(body,"confirmed");if(!Boolean.TRUE.equals(body.opt("confirmed")))throw new IllegalArgumentException("请确认恢复原桌面");
  if(!TvAdb.status().optBoolean("connected"))throw new IllegalStateException("请先连接本机调试");String pkg=prefs(c).getString("previous","");if(!safe(c,pkg))throw new IllegalArgumentException("没有可恢复的原桌面记录");
  String result=TvAdb.shell(c,"pm enable --user "+(c.getApplicationInfo().uid/100000)+" "+pkg);if(disabled(c,pkg))throw new IllegalStateException("恢复失败："+result);
  WebAudit.record(c,"电视/网页","恢复原桌面",pkg,200);return status(c).put("message","原桌面已恢复启用，默认主页仍保留 Aurora Launcher；可在系统设置中重新选择");
 }
}
