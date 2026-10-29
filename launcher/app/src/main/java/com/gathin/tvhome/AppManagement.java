package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.provider.Settings;
import android.util.Base64;
import org.json.*;
import java.util.*;

/** Presentation overrides are launcher-only. Package names and APK signatures never change. */
final class AppManagement {
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("app_presentation",0);}
    static String iconUrl(Context c,String pkg){return "/api/v1/icons/"+pkg+"?v="+prefs(c).getLong("revision",0);}
    static String label(Context c,String pkg,String fallback){return prefs(c).getString("name:"+pkg,fallback);}
    static byte[] icon(Context c,String pkg){String data=prefs(c).getString("icon:"+pkg,"");try{return data.isEmpty()?null:Base64.decode(data,Base64.DEFAULT);}catch(Exception invalid){return null;}}
    static Drawable drawable(Context c,String pkg){byte[] data=icon(c,pkg);if(data==null)return null;Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length);return b==null?null:new BitmapDrawable(c.getResources(),b);}
    static void revision(Context c)throws Exception{if(!c.getSharedPreferences(Config.SETTINGS_NAME,0).edit().putLong("web_layout_revision",System.currentTimeMillis()).commit())throw new java.io.IOException("显示设置保存失败");}
    static String protection(Context c,PackageInfo p){
        String pkg=p.packageName;
        if(pkg.equals(c.getPackageName())||pkg.equals(LauncherUpdateAgent.GUARD)||pkg.equals("com.alibaba.dingtalk.focus"))return "桌面及维护组件受到保护";
        if(p.applicationInfo.uid<10000||(p.applicationInfo.flags&ApplicationInfo.FLAG_PERSISTENT)!=0)return "系统核心或共享系统进程受到保护";
        if(AppManagementRules.protectedPackage(pkg))return "系统设置、连接或输入组件受到保护";
        String ime=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        if(ime!=null&&ime.startsWith(pkg+"/"))return "当前输入法不能停用";
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for(ResolveInfo r:c.getPackageManager().queryIntentActivities(home,0))if(r.activityInfo!=null&&pkg.equals(r.activityInfo.packageName))return "主页组件不能在应用管理中停用";
        return "";
    }
    static JSONArray ordered(Context c)throws Exception{
        if(AuroraThemes.current(c).equals("legacy"))return WebSettings.apps(c);
        JSONArray result=AuroraData.apps(c),order=AuroraSettings.get(c).getJSONObject("appGrid").getJSONArray("order"),hidden=AuroraSettings.get(c).getJSONObject("appGrid").getJSONArray("hidden");
        Set<String> off=new HashSet<>();for(int i=0;i<hidden.length();i++)off.add(hidden.getString(i));
        List<JSONObject> items=new ArrayList<>();for(int i=0;i<result.length();i++){JSONObject item=result.getJSONObject(i);String pkg=item.getString("package");int rank=10000+i;for(int j=0;j<order.length();j++)if(pkg.equals(order.getString(j)))rank=j;item.put("visible",!off.contains(pkg)).put("rank",rank);items.add(item);}items.sort(Comparator.comparingInt(v->v.optInt("rank")));return new JSONArray(items);
    }
    static JSONObject list(Context c)throws Exception{
        Map<String,JSONObject> layout=new HashMap<>();JSONArray ordered=ordered(c);for(int i=0;i<ordered.length();i++)layout.put(ordered.getJSONObject(i).getString("package"),ordered.getJSONObject(i));
        List<JSONObject> apps=new ArrayList<>();boolean adb=TvAdb.status().optBoolean("connected");PackageManager pm=c.getPackageManager();
        for(PackageInfo p:pm.getInstalledPackages(0)){
            String pkg=p.packageName,original=String.valueOf(p.applicationInfo.loadLabel(pm)),reason=protection(c,p);JSONObject tile=layout.get(pkg);boolean system=(p.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
            apps.add(new JSONObject().put("package",pkg).put("name",label(c,pkg,original)).put("originalName",original).put("version",p.versionName==null?"":p.versionName)
                .put("icon",iconUrl(c,pkg)).put("customIcon",prefs(c).contains("icon:"+pkg)).put("launchable",tile!=null&&p.applicationInfo.enabled)
                .put("visible",tile!=null&&tile.optBoolean("visible",true)).put("rank",tile==null?Integer.MAX_VALUE:tile.optInt("rank",10000))
                .put("system",system).put("enabled",p.applicationInfo.enabled).put("removable",!WebApps.protectedApp(c,p))
                .put("canToggle",system&&adb&&(!p.applicationInfo.enabled||reason.isEmpty())).put("toggleReason",!reason.isEmpty()&&p.applicationInfo.enabled?reason:!adb?"请先连接本机调试":""));
        }
        apps.sort((a,b)->{int x=Boolean.compare(b.optBoolean("removable"),a.optBoolean("removable"));if(x==0)x=Integer.compare(a.optInt("rank"),b.optInt("rank"));return x!=0?x:a.optString("name").compareToIgnoreCase(b.optString("name"));});
        return new JSONObject().put("apps",new JSONArray(apps)).put("theme",AuroraThemes.current(c)).put("mock",false);
    }
    static synchronized JSONObject change(Context c,JSONObject body)throws Exception{
        AuroraSettings.only(body,"package","name","icon","resetIcon","visible","direction","enabled","confirm");String pkg=WebApps.valid(c,body.getString("package"));
        for(String key:new String[]{"visible","enabled","resetIcon","confirm"})if(body.has(key)&&!(body.get(key) instanceof Boolean))throw new IllegalArgumentException("开关参数必须是布尔值");if(body.has("direction")&&!(body.get("direction") instanceof Integer))throw new IllegalArgumentException("排序方向必须是整数");
        int operations=0;for(String k:new String[]{"name","icon","resetIcon","visible","direction","enabled"})if(body.has(k))operations++;
        if(operations!=1)throw new IllegalArgumentException("每次只修改一个应用属性");
        android.content.SharedPreferences.Editor edit=prefs(c).edit();
        if(body.has("name")){if(!(body.get("name") instanceof String))throw new IllegalArgumentException("名称必须是文字");String name=AppManagementRules.name(body.getString("name"));if(name.isEmpty())edit.remove("name:"+pkg);else edit.putString("name:"+pkg,name);}
        if(body.has("icon")){
            byte[] bytes=Base64.decode(body.getString("icon"),Base64.DEFAULT);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            if(bytes.length>40000||bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>256||bounds.outHeight>256)throw new IllegalArgumentException("请选择有效的256像素以内图标");
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)throw new IllegalArgumentException("无法读取图标");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();if(out.size()>40000)throw new IllegalArgumentException("图标文件过大");edit.putString("icon:"+pkg,Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
        }
        if(body.has("resetIcon")){if(!body.getBoolean("resetIcon"))throw new IllegalArgumentException("恢复图标参数无效");edit.remove("icon:"+pkg);}
        if(body.has("enabled")){
            if(!body.optBoolean("confirm"))throw new IllegalArgumentException("请先确认停用或启用");PackageInfo p=c.getPackageManager().getPackageInfo(pkg,0);boolean enabled=body.getBoolean("enabled");String reason=protection(c,p);
            if((p.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0||(!enabled&&!reason.isEmpty()))throw new IllegalArgumentException(reason.isEmpty()?"仅系统应用支持此操作":reason);
            if(!TvAdb.status().optBoolean("connected"))throw new IllegalStateException("请先连接本机调试");
            TvAdb.shell(c,"pm "+(enabled?"enable":"disable-user")+" --user 0 "+pkg);
            if(c.getPackageManager().getApplicationInfo(pkg,0).enabled!=enabled)throw new java.io.IOException("系统未完成操作，请检查权限");
        }
        if(body.has("visible")||body.has("direction")){
            JSONArray ordered=ordered(c);List<JSONObject> list=new ArrayList<>();int at=-1;for(int i=0;i<ordered.length();i++){JSONObject v=ordered.getJSONObject(i);list.add(v);if(pkg.equals(v.getString("package")))at=i;}if(at<0)throw new IllegalArgumentException("此应用没有可显示的桌面入口");
            if(body.has("visible"))list.get(at).put("visible",body.getBoolean("visible"));
            if(body.has("direction")){int d=body.getInt("direction");if(d!=1&&d!=-1)throw new IllegalArgumentException("排序方向无效");int next=at+d;if(next>=0&&next<list.size())Collections.swap(list,at,next);}
            if(AuroraThemes.current(c).equals("legacy"))WebSettings.saveApps(c,new JSONArray(list));
            else{JSONObject grid=AuroraSettings.get(c).getJSONObject("appGrid");JSONArray order=new JSONArray(),hidden=new JSONArray();for(JSONObject v:list){order.put(v.getString("package"));if(!v.optBoolean("visible",true))hidden.put(v.getString("package"));}grid.put("order",order).put("hidden",hidden);AuroraSettings.save(c,new JSONObject().put("appGrid",grid));}
        }
        if(!edit.putLong("revision",System.currentTimeMillis()).commit())throw new java.io.IOException("应用设置保存失败");revision(c);c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()).putExtra("kind",body.has("visible")||body.has("direction")?"settings-layout":"apps-presentation"));return list(c);
    }
}
