package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.media.AudioManager;
import org.json.*;
import java.util.*;

/** Explicit settings schema; neither raw preference stores nor credentials leave the TV. */
final class WebSettings {
    private static JSONObject field(String id,String title,String section,String type,Object value,int min,int max,String...options)throws JSONException{
        JSONObject f=new JSONObject().put("id",id).put("title",title).put("section",section).put("type",type).put("value",value).put("min",min).put("max",max);
        if(options.length>0)f.put("options",new JSONArray(options));return f;
    }
    static JSONArray schema(Context c)throws JSONException{
        Config cfg=Config.load(c);android.content.SharedPreferences l=c.getSharedPreferences(Config.SETTINGS_NAME,0),a=AmbientMusic.prefs(c),p=DesktopAppearance.prefs(c);
        JSONArray f=new JSONArray();
        f.put(field("launcher:desktop_info_mode","桌面信息显示","layout","select",l.getInt("desktop_info_mode",0),0,1,"完整信息","极简信息：时间、日期、今天天气"));
        f.put(field("launcher:columns","每排应用数量","layout","number",cfg.columns,4,7));
        f.put(field("launcher:animations","桌面动画","layout","boolean",cfg.animations,0,0));
        f.put(field("launcher:advanced_effects","增强动画效果","layout","boolean",cfg.advancedEffects,0,0));
        f.put(field("launcher:app_ambient","焦点跟随应用颜色的氛围光","layout","boolean",l.getBoolean("app_ambient",true),0,0));
        String[] keys={"fill_dock","reduced_motion","dock_title","dock_row","reorder_button","module_cards","app_library","show_stats","show_home_settings_shortcut"};
        String[] names={"自动填满第一排","减少动态效果","显示选中应用名称","第一排独立卡片栏","显示排序按钮","应用模块卡片","显示全部应用入口","显示性能统计","显示默认主页入口"};
        boolean[] values={cfg.fillDock,cfg.reducedMotion,cfg.dockTitle,cfg.dockRow,cfg.reorderButton,cfg.moduleCards,cfg.appLibrary,cfg.showStats,cfg.showHomeSettingsShortcut};
        for(int i=0;i<keys.length;i++)f.put(field("config:"+keys[i],names[i],"layout","boolean",values[i],0,0));
        f.put(field("config:rows_below_dock","第一排下方可见行数","layout","number",cfg.rowsBelowDock,0,3));
        f.put(field("config:greeting_name","问候语称呼","layout","text",cfg.greetingName,0,60));
        f.put(field("appearance:text","全局字体与播放卡片颜色","appearance","color",hex(DesktopAppearance.text(c)),0,0));
        f.put(field("appearance:spectrum","频谱颜色","compat","color",hex(p.getInt("spectrum",0xFF69E7FF)),0,0));
        f.put(field("appearance:opacity","频谱不透明程度","compat","number",p.getInt("opacity",50),0,100));
        f.put(field("appearance:brightness","频谱亮度","compat","number",p.getInt("brightness",70),0,100));
        f.put(field("config:background_color","默认纯色背景","appearance","color",hex(cfg.backgroundColor),0,0));
        f.put(field("config:sky_intensity","默认背景光效强度","appearance","number",cfg.skyIntensity,0,100));
        f.put(field("appearance:card_style","播放卡片频谱样式","music","select",p.getInt("card_style",0),0,4,"渐变柱状","流动波线","镜面频谱","光点","环形"));
        f.put(field("appearance:card_color","播放卡片频谱渐变起始色","music","color",hex(p.getInt("card_color",p.getInt("spectrum",0xFF69E7FF))),0,0));
        f.put(field("appearance:card_color2","播放卡片频谱渐变结束色","music","color",hex(p.getInt("card_color2",0xFF6172FF)),0,0));
        f.put(field("appearance:card_opacity","播放卡片频谱不透明程度（0为隐藏）","music","number",p.getInt("card_opacity",p.getInt("opacity",65)),0,100));
        f.put(field("appearance:card_size","播放卡片频谱尺寸百分比","music","number",p.getInt("card_size",100),30,200));
        f.put(field("appearance:card_angle","播放卡片频谱渐变角度","music","number",p.getInt("card_angle",315),0,360));
        f.put(field("launcher:desktop_card_visible","显示桌面播放卡片","music","boolean",l.getBoolean("desktop_card_visible",l.getBoolean("desktop_lyrics",true)),0,0));
        f.put(field("launcher:desktop_lyrics_visible","显示桌面歌词","music","boolean",l.getBoolean("desktop_lyrics_visible",l.getBoolean("desktop_lyrics",true)),0,0));
        f.put(field("launcher:music_card_position","播放卡片位置","music","select",l.getInt("music_card_position",0),0,2,"靠左","居中","靠右"));
        f.put(field("launcher:lyrics_color","歌词未唱部分颜色","music","color",hex(l.getInt("lyrics_color",0xFF79E8FF)),0,0));
        f.put(field("launcher:lyrics_highlight","歌词逐字高亮颜色","music","color",hex(l.getInt("lyrics_highlight",0xFFFFD166)),0,0));
        f.put(field("ambient:enabled","启用背景音乐","ambient","boolean",a.getBoolean("enabled",false),0,0));
        f.put(field("ambient:continuous","在桌面持续播放","ambient","boolean",a.getBoolean("continuous",false),0,0));
        f.put(field("ambient:minutes","无操作多少分钟后播放","ambient","minutes",a.getInt("minutes",5),3,10));
        f.put(field("ambient:volume_index","背景音乐实际音量","ambient","number",a.getInt("volume_index",3),0,((AudioManager)c.getSystemService(Context.AUDIO_SERVICE)).getStreamMaxVolume(AudioManager.STREAM_MUSIC)));
        f.put(field("ambient:info_mode","音乐氛围信息","ambient","select",a.getInt("info_mode",0),0,2,"完整信息","极简信息：时间、日期、今天天气","全部隐藏"));
        f.put(field("ambient:spectrum_visible","显示屏保频谱","ambient","boolean",a.getBoolean("spectrum_visible",true),0,0));
        f.put(field("ambient:lyrics_visible","显示屏保单行歌词（频谱上方）","ambient","boolean",a.getBoolean("lyrics_visible",true),0,0));
        f.put(field("ambient:lyrics","当前屏保音乐歌词（LRC，按歌曲保存）","ambient","lrc",a.getString("lyrics_"+a.getString("selected",""),""),0,100000));
        f.put(field("ambient:crossfade","无缝交叉淡化循环","ambient","boolean",a.getBoolean("crossfade",true),0,0));
        f.put(field("home:enabled","Home键返回极简桌面","system","boolean",HomeCompatibility.enabled(c),0,0));
        f.put(field("config:settings_key_panel","遥控设置键打开快捷面板","system","boolean",cfg.settingsKeyPanel,0,0));
        f.put(field("config:settings_key_keycodes","遥控设置键代码（逗号分隔）","advanced","codes",join(cfg.settingsKeycodes),0,512));
        String[] advancedKeys={"vpn_app","live_tv_package","live_tv_action","source_panel_service","source_panel_action","input_action"};
        String[] advancedNames={"VPN应用包名","电视直播应用包名","电视直播操作","输入源面板服务","输入源面板操作","输入源操作"};
        String[] advancedValues={cfg.vpnApp,cfg.liveTvPackage,cfg.liveTvAction,cfg.sourcePanelService,cfg.sourcePanelAction,cfg.inputAction};
        for(int i=0;i<advancedKeys.length;i++)f.put(field("config:"+advancedKeys[i],advancedNames[i],"advanced","text",advancedValues[i],0,256));
        f.put(field("config:shamsi_date","额外显示波斯历日期","advanced","boolean",cfg.shamsiDate,0,0));
        f.put(field("ambient:greeting","氛围屏幕问候语（留空隐藏）","ambient","text",a.getString("greeting",""),0,80));
        f.put(field("ambient:greeting_x","问候语水平位置（%）","ambient","number",a.getInt("greeting_x",50),0,100));
        f.put(field("ambient:greeting_y","问候语垂直位置（%）","ambient","number",a.getInt("greeting_y",35),0,100));
        f.put(field("location:city","天气城市名称","system","text",LocationSettings.city(c),0,40));
        f.put(field("location:latitude","天气纬度（-90～90）","system","text",LocationSettings.prefs(c).getString("latitude","31.2304"),0,20));
        f.put(field("location:longitude","天气经度（-180～180）","system","text",LocationSettings.prefs(c).getString("longitude","121.4737"),0,20));
        f.put(field("location:timezone","时区（例如 Asia/Shanghai）","system","text",LocationSettings.zone(c),0,80));
        f.put(field("location:hour24","24小时制","system","boolean",LocationSettings.prefs(c).getBoolean("hour24",true),0,0));
        return f;
    }
    private static String hex(int c){return String.format(Locale.ROOT,"#%06X",c&0xffffff);}
    private static String join(int[] a){StringJoiner s=new StringJoiner(",");for(int n:a)s.add(String.valueOf(n));return s.toString();}
    static JSONObject state(Context c)throws Exception{
        android.content.SharedPreferences b=c.getSharedPreferences("background",0);
        return new JSONObject().put("fields",schema(c)).put("uploadPath",BackgroundUploadServer.path(c)).put("themeId",AuroraThemes.current(c)).put("desktopBackground",b.getString("selected","")).put("desktopMode",BackgroundStore.mode(c))
            .put("ambientBackground",b.getString("ambient_selected","")).put("ambientMode",b.getString("ambient_mode","follow")).put("slideshow",AmbientSlideshow.state(c)).put("version","2.6.1 · 网页控制中心");
    }
    static synchronized boolean apply(Context c,JSONObject changes)throws Exception{
        if(changes.length()==0||changes.length()>80)throw new IllegalArgumentException("请选择要修改的设置");
        Map<String,JSONObject> allowed=new HashMap<>();JSONArray fields=schema(c);for(int i=0;i<fields.length();i++){JSONObject f=fields.getJSONObject(i);allowed.put(f.getString("id"),f);}
        Map<String,Object> checked=new LinkedHashMap<>();boolean rebuild=false;
        for(Iterator<String> it=changes.keys();it.hasNext();){String id=it.next();JSONObject f=allowed.get(id);if(f==null)throw new IllegalArgumentException("不支持的设置项");Object v=changes.get(id);String type=f.getString("type");
            if(type.equals("boolean")){if(!(v instanceof Boolean))throw new IllegalArgumentException("开关值无效");}
            else if(type.equals("lrc")){if(!(v instanceof String)||((String)v).length()>100000||((String)v).indexOf(0)>=0)throw new IllegalArgumentException("LRC歌词内容无效或过长");}
            else if(type.equals("color")){if(!(v instanceof String)||!((String)v).matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("颜色格式无效");v=android.graphics.Color.parseColor((String)v);}
            else if(type.equals("text")||type.equals("codes")){if(!(v instanceof String)||((String)v).length()>f.getInt("max")||((String)v).matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException("文本过长或包含无效字符");if(type.equals("codes")){JSONArray codes=new JSONArray();if(!((String)v).trim().isEmpty())for(String n:((String)v).split(",")){int code=Integer.parseInt(n.trim());if(code<0||code>1000||codes.length()>=32)throw new IllegalArgumentException("按键代码无效");codes.put(code);}v=codes;}}
            else {if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).intValue())throw new IllegalArgumentException("请输入整数");int n=((Number)v).intValue();if(n<f.getInt("min")||n>f.getInt("max")||(type.equals("minutes")&&n!=3&&n!=5&&n!=8&&n!=10))throw new IllegalArgumentException("数值超出范围");v=n;}
            if(id.equals("location:latitude")||id.equals("location:longitude")){double n=Double.parseDouble(v.toString());double max=id.endsWith("latitude")?90:180;if(!Double.isFinite(n)||Math.abs(n)>max)throw new IllegalArgumentException("经纬度超出范围");}
            if(id.equals("location:timezone")&&!java.util.Arrays.asList(java.util.TimeZone.getAvailableIDs()).contains(v.toString()))throw new IllegalArgumentException("时区名称无效，例如 Asia/Shanghai");
            checked.put(id,v);if(f.getString("section").equals("layout")||id.startsWith("config:")||id.startsWith("location:"))rebuild=true;
        }
        if(Boolean.FALSE.equals(checked.get("launcher:animations")))checked.put("launcher:advanced_effects",false);
        android.content.SharedPreferences l=c.getSharedPreferences(Config.SETTINGS_NAME,0);JSONObject config=new JSONObject(l.getString("web_config","{}"));
        Map<String,android.content.SharedPreferences.Editor> editors=new LinkedHashMap<>();
        for(Map.Entry<String,Object> e:checked.entrySet()){String[] id=e.getKey().split(":",2);Object v=e.getValue();if(e.getKey().equals("ambient:lyrics"))id[1]="lyrics_"+AmbientMusic.prefs(c).getString("selected","");
            if(id[0].equals("config")){config.put(id[1],v instanceof Integer&&allowed.get(e.getKey()).getString("type").equals("color")?hex((Integer)v):v);continue;}
            if(id[0].equals("home"))continue;
            String store=id[0].equals("launcher")?Config.SETTINGS_NAME:id[0].equals("appearance")?"desktop_appearance":id[0].equals("location")?"desktop_location":"ambient_music";
            android.content.SharedPreferences.Editor ed=editors.get(store);if(ed==null){ed=c.getSharedPreferences(store,0).edit();editors.put(store,ed);}if(v instanceof Boolean)ed.putBoolean(id[1],(Boolean)v);else if(v instanceof Integer)ed.putInt(id[1],(Integer)v);else ed.putString(id[1],v.toString());
        }
        android.content.SharedPreferences.Editor le=editors.get(Config.SETTINGS_NAME);if(le==null){le=l.edit();editors.put(Config.SETTINGS_NAME,le);}le.putString("web_config",config.toString());
        if(rebuild)le.putLong("web_layout_revision",System.currentTimeMillis());
        for(android.content.SharedPreferences.Editor ed:editors.values())if(!ed.commit())throw new java.io.IOException("设置保存失败，请重试");
        if(checked.containsKey("home:enabled"))HomeCompatibility.setEnabled(c,(Boolean)checked.get("home:enabled"));
        boolean locationChanged=false;for(String id:checked.keySet())if(id.startsWith("location:"))locationChanged=true;
        if(locationChanged){JSONObject service=AuroraServices.config(c),w=service.getJSONObject("weather"),t=service.getJSONObject("time");for(Map.Entry<String,Object> e:checked.entrySet()){
            switch(e.getKey()){case "location:city":w.put("city",e.getValue());break;case "location:latitude":w.put("lat",Double.parseDouble(e.getValue().toString()));break;case "location:longitude":w.put("lon",Double.parseDouble(e.getValue().toString()));break;case "location:timezone":t.put("timezone",e.getValue());break;}}
            AuroraServices.save(c,service);
        }
        return rebuild;
    }
    static JSONArray apps(Context c)throws Exception{
        Config cfg=Config.load(c);PackageManager pm=c.getPackageManager();Map<String,ResolveInfo> found=new LinkedHashMap<>();Set<String> tv=new HashSet<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))if(r.activityInfo!=null&&r.activityInfo.exported){String pkg=r.activityInfo.packageName;if(category.equals(Intent.CATEGORY_LEANBACK_LAUNCHER))tv.add(pkg);if(!pkg.equals(c.getPackageName())&&!pkg.equals("com.alibaba.dingtalk.focus"))found.putIfAbsent(pkg,r);}
        Set<String> added=LibraryApps.load(c),removed=LibraryApps.loadRemoved(c);Map<String,Integer> ranks=Order.ranks(Order.load(Order.file(c)),cfg.dockApps);
        List<JSONObject> list=new ArrayList<>();for(Map.Entry<String,ResolveInfo> e:found.entrySet()){String pkg=e.getKey(),key=new ComponentName(pkg,e.getValue().activityInfo.name).flattenToShortString();boolean visible=LibraryApps.visible(tv.contains(pkg),cfg.hiddenApps.contains(pkg),added.contains(pkg),removed.contains(pkg));list.add(new JSONObject().put("package",pkg).put("key",key).put("label",Cards.cleanLabel(String.valueOf(e.getValue().loadLabel(pm)),pkg)).put("visible",visible).put("pinned",cfg.dockApps.contains(pkg)).put("rank",ranks.getOrDefault(key,10000+Math.max(0,cfg.dockApps.indexOf(pkg)))));}
        list.sort((a,b)->{int x=Integer.compare(a.optInt("rank"),b.optInt("rank"));return x!=0?x:a.optString("label").compareToIgnoreCase(b.optString("label"));});
        if(cfg.dockRow){int visible=0;for(JSONObject item:list)item.put("pinned",item.getBoolean("visible")&&visible++<cfg.columns);}
        return new JSONArray(list);
    }
    static JSONArray cards(Context c)throws Exception{
        JSONArray result=new JSONArray();Config cfg=Config.load(c);
        for(Cards.Card card:Cards.discover(c,new HashSet<>(cfg.hiddenApps),Cards.load(Cards.storeFile(c))))result.put(new JSONObject().put("package",card.pkg).put("label",card.appLabel).put("caption",card.caption==null?"应用提供的桌面信息卡片":card.caption).put("shown",card.state==Cards.State.SHOWN));return result;
    }
    static boolean resetColor(Context c,String id)throws Exception{
        boolean allowed=false;JSONArray schema=schema(c);for(int i=0;i<schema.length();i++){JSONObject f=schema.getJSONObject(i);if(f.getString("id").equals(id)&&f.getString("type").equals("color"))allowed=true;}
        if(!allowed)throw new IllegalArgumentException("此设置不支持颜色重置");String[] key=id.split(":",2);
        if(key[0].equals("config")){android.content.SharedPreferences p=c.getSharedPreferences(Config.SETTINGS_NAME,0);JSONObject config=new JSONObject(p.getString("web_config","{}"));config.remove(key[1]);p.edit().putString("web_config",config.toString()).putLong("web_layout_revision",System.currentTimeMillis()).commit();return true;}
        c.getSharedPreferences(key[0].equals("appearance")?"desktop_appearance":Config.SETTINGS_NAME,0).edit().remove(key[1]).commit();return false;
    }
    static void card(Context c,String pkg,boolean show)throws Exception{
        Config cfg=Config.load(c);for(Cards.Card card:Cards.discover(c,new HashSet<>(cfg.hiddenApps),Cards.load(Cards.storeFile(c))))if(card.pkg.equals(pkg)){if(!Cards.decide(Cards.storeFile(c),card,show))throw new java.io.IOException("卡片设置未能保存");c.getSharedPreferences(Config.SETTINGS_NAME,0).edit().putLong("web_layout_revision",System.currentTimeMillis()).commit();return;}throw new IllegalArgumentException("此应用没有可用模块卡片");
    }
    static synchronized void saveApps(Context c,JSONArray requested)throws Exception{
        JSONArray current=apps(c);Map<String,JSONObject> allowed=new HashMap<>();for(int i=0;i<current.length();i++)allowed.put(current.getJSONObject(i).getString("package"),current.getJSONObject(i));
        if(requested.length()!=allowed.size())throw new IllegalArgumentException("应用列表已变化，请刷新后重试");Set<String> seen=new HashSet<>();List<String> order=new ArrayList<>(),dock=new ArrayList<>(),otherOrder=new ArrayList<>();
        for(int i=0;i<requested.length();i++){JSONObject item=requested.getJSONObject(i);String pkg=item.getString("package");if(!allowed.containsKey(pkg)||!seen.add(pkg)||!(item.get("visible") instanceof Boolean)||!(item.get("pinned") instanceof Boolean))throw new IllegalArgumentException("应用设置无效");if(item.getBoolean("pinned")){dock.add(pkg);order.add(allowed.get(pkg).getString("key"));}else otherOrder.add(allowed.get(pkg).getString("key"));}order.addAll(otherOrder);
        android.content.SharedPreferences p=c.getSharedPreferences(Config.SETTINGS_NAME,0);JSONObject config=new JSONObject(p.getString("web_config","{}"));config.put("dock_apps",new JSONArray(dock));
        Set<String> hidden=new HashSet<>(Config.load(c).hiddenApps);for(String pkg:seen)hidden.remove(pkg);config.put("hidden_apps",new JSONArray(hidden));
        for(int i=0;i<requested.length();i++){JSONObject item=requested.getJSONObject(i);if(!LibraryApps.save(c,item.getString("package"),item.getBoolean("visible")))throw new java.io.IOException("应用显示设置未能保存");}
        if(!Order.save(Order.file(c),new Order(Order.dockKey(dock),order)))throw new java.io.IOException("排序未能保存");
        if(!p.edit().putString("web_config",config.toString()).putLong("web_layout_revision",System.currentTimeMillis()).commit())throw new java.io.IOException("应用设置保存失败");
    }
}
