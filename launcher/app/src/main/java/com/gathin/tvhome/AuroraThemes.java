package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class AuroraThemes {
    static final String FOUNDATION="foundation";
    static File root(Context c)throws IOException {
        File external=c.getExternalFilesDir(null);if(external==null)throw new IOException("应用外部目录不可用");
        File root=new File(external,"aurora");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法创建主题目录");return root;
    }
    static synchronized JSONObject config(Context c)throws Exception {
        AtomicFile file=new AtomicFile(new File(root(c),"config.json"));
        try(InputStream in=file.openRead()){JSONObject value=new JSONObject(new String(read(in,1024*1024),StandardCharsets.UTF_8));value.put("locale",DesktopLanguage.code(c));if(!value.has("consoleLocale"))value.put("consoleLocale","zh-CN");return value;}
        catch(FileNotFoundException missing){return new JSONObject().put("locale",DesktopLanguage.code(c)).put("consoleLocale","zh-CN").put("theme","legacy").put("lastGood","legacy").put("icon_fallback","app");}
        catch(Exception corrupt){return new JSONObject().put("locale",DesktopLanguage.code(c)).put("consoleLocale","zh-CN").put("theme","legacy").put("lastGood","legacy").put("icon_fallback","app").put("recovered",true);}
    }
    static synchronized void save(Context c,JSONObject config)throws Exception {
        AtomicFile file=new AtomicFile(new File(root(c),"config.json"));FileOutputStream out=null;
        try{out=file.startWrite();out.write(config.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception error){if(out!=null)file.failWrite(out);throw error;}
    }
    static String id(String id) {
        if(!id.matches("[a-z][a-z0-9_-]{0,39}"))throw new IllegalArgumentException("主题编号无效");return id;
    }
    static JSONObject manifest(Context c,String name)throws Exception {
        if(name.startsWith("user_"))return AuroraCssThemes.manifest(c,name);
        id(name);String path="themes/"+name+"/theme.json";
        JSONObject manifest=new JSONObject(new String(name.equals("legacy")?packaged(c,path):asset(c,path),StandardCharsets.UTF_8));
        if(!name.equals(manifest.optString("id"))||manifest.optInt("apiVersion")!=1)throw new IOException("主题接口版本不兼容");return manifest;
    }
    static JSONArray list(Context c)throws Exception {
        JSONArray themes=new JSONArray();
        themes.put(manifest(c,"legacy").put("available",true).put("stage","已内置"));
        themes.put(manifest(c,"cards").put("available",true).put("stage","简约卡片 · 封面、歌词、快捷功能与系统状态"));
        JSONArray users=AuroraCssThemes.entries(c);for(int i=0;i<users.length();i++)themes.put(manifest(c,users.getJSONObject(i).getString("id")).put("available",true).put("stage","自定义 CSS 主题"));
        return themes;
    }
    static JSONObject publicConfig(Context c)throws Exception{JSONObject config=config(c);config.remove("themeProfiles");config.put("theme",current(c));config.put("appearance",AuroraSettings.stored(c));return config;}
    static void open(Context c) {
        Class<?> target=current(c).equals("legacy")?MainActivity.class:AuroraActivity.class;
        c.startActivity(new android.content.Intent(c,target).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    static byte[] asset(Context c,String path)throws Exception {
        if(path.startsWith("themes/user_"))return AuroraCssThemes.asset(c,path);
        if(!path.startsWith("themes/")&&!path.startsWith("core/")&&!path.startsWith("console/"))throw new FileNotFoundException();
        File external=AuroraRules.inside(root(c),path);
        // Pre-upgrade hotfix files must not shadow the console/API shipped in the new APK.
        // Keep the files and accept subsequent hot updates; user theme/CSS/media remain untouched.
        if(external.isFile()&&(path.startsWith("console/")||path.startsWith("core/")||path.equals("themes/saver/index.html"))){
            long installed=c.getPackageManager().getPackageInfo(c.getPackageName(),0).lastUpdateTime;
            if(external.lastModified()<installed)try(InputStream in=c.getAssets().open("aurora/"+path)){return read(in,4*1024*1024);}catch(FileNotFoundException absent){}
        }
        if(external.isFile())try(InputStream in=new FileInputStream(external)){return read(in,4*1024*1024);}
        try(InputStream in=c.getAssets().open("aurora/"+path)){return read(in,4*1024*1024);}
    }
    static byte[] packaged(Context c,String path)throws Exception {
        if(path.startsWith("themes/user_"))return AuroraCssThemes.asset(c,path);
        try(InputStream in=c.getAssets().open("aurora/"+path)){return read(in,4*1024*1024);}
    }
    static String current(Context c) {
        try{String id=config(c).optString("theme","legacy");manifest(c,id);return id;}
        catch(Exception invalid){return "legacy";}
    }
    static byte[] read(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("文件或请求过大");out.write(buffer,0,n);}return out.toByteArray();
    }
}
