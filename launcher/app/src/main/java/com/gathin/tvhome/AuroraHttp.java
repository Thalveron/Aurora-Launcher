package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/** Versioned theme/data API, sharing port 8899 with the existing password-protected console. */
final class AuroraHttp {
    static final String themeSession=AuroraRules.token();
    private static final AuroraRules.Confirmations confirmations=new AuroraRules.Confirmations();
    private static final Semaphore screenSlots=new Semaphore(2),eventSlots=new Semaphore(4);
    private static final AtomicLong sequence=new AtomicLong();
    private static final Map<String,long[]> failedLogins=new LinkedHashMap<>();
    private static SystemControls controls;
    private static final FrameHub frames=new FrameHub();
    static synchronized SystemControls controls(Context c){if(controls==null)controls=new SystemControls(c);return controls;}
    static void initialize(Context c) {
        new Thread(()->{try{TvAdb.shell(c,"echo AURORA_READY");}catch(Exception ignored){}},"aurora-probe").start();
    }
    static boolean handle(Context c,Socket socket,String[] request,Map<String,String> headers,InputStream input,OutputStream output)throws Exception {
        if(request.length!=3)return false;
        URI uri;try{uri=new URI(request[1]);}catch(Exception invalid){return false;}
        String path=uri.getPath();
        if(path==null||!(path.startsWith("/api/v1/")||path.startsWith("/themes/")||path.startsWith("/core/")||path.startsWith("/console/")))return false;
        String peer=socket.getInetAddress().getHostAddress();int status=500;boolean logged=false;WriteTracker streamOutput=new WriteTracker(output);
        try {
            if(!AuroraRules.lan(socket.getInetAddress())){json(output,403,null,"FORBIDDEN","仅允许局域网访问");status=403;return true;}
            if(!AuroraRules.originAllowed(headers.get("origin"),headers.get("host"))){json(output,403,null,"ORIGIN","请求来源不受信任");status=403;return true;}
            boolean loopback=socket.getInetAddress().isLoopbackAddress();
            if(path.equals("/api/v1/updates/health")&&request[0].equals("GET")){
                if(!loopback){json(output,403,null,"FORBIDDEN","仅允许本机健康检查");return true;}
                try{json(output,200,UpdateHealth.sample(c,headers.get("x-aurora-update-token")),null,null);}catch(SecurityException invalid){json(output,403,null,"FORBIDDEN","无效健康检查");}return true;
            }
            boolean session=loopback&&"127.0.0.1:8899".equals(headers.get("host"))&&cookie(headers.get("cookie"));
            boolean existingWeb=AuroraLogin.valid(c,headers.get("cookie"));
            boolean credentialAttempt=headers.containsKey("authorization")||(path.equals("/api/v1/auth/login")&&request[0].equals("POST"));
            if(credentialAttempt&&!session&&!existingWeb){synchronized(failedLogins){long[] failure=failedLogins.get(peer);if(failure!=null&&android.os.SystemClock.elapsedRealtime()-failure[0]<60000&&failure[1]>=8){json(output,429,null,"RATE_LIMIT","密码尝试过多，请一分钟后重试");return true;}}}
            boolean password=loopback&&UploadSecurity.authorized(c,headers.get("authorization"))||existingWeb;
            String logicalAsset=(path.startsWith("/console/")||path.startsWith("/core/"))?ConsoleAssets.logical(c,path.substring(1)):null;
            boolean loginAsset=logicalAsset!=null&&java.util.Arrays.asList("console/login.html","console/login.js","console/login.css","console/aurora.svg").contains(logicalAsset);
            boolean loginAttempt=path.equals("/api/v1/auth/login")&&request[0].equals("POST");
            boolean setupAttempt=path.equals("/api/v1/auth/setup")&&request[0].equals("POST");
            if(path.equals("/api/v1/auth/open-setup")&&request[0].equals("POST")){
                JSONObject choice=body(input,headers);
                if(!choice.optBoolean("confirmed")){json(output,409,null,"CONFIRM_REQUIRED","请确认局域网设备可直接管理桌面");return true;}
                if(UploadSecurity.initialized(c)||UploadSecurity.openAccess(c)){json(output,409,null,"ALREADY_INITIALIZED","访问方式已设置，请登录后修改");return true;}
                UploadSecurity.initializeOpen(c);WebAudit.record(c,peer,"首次选择无密码访问","仅限局域网",200);
                json(output,200,new JSONObject().put("openAccess",true).put("needsPath",ConsoleSettings.path(c).isEmpty()),null,null);return true;
            }
            if(path.equals("/api/v1/auth/status")&&request[0].equals("GET")){json(output,200,new JSONObject().put("initialized",UploadSecurity.initialized(c)).put("openAccess",UploadSecurity.openAccess(c)),null,null);return true;}
            if(setupAttempt||loginAttempt){
                JSONObject credentials=body(input,headers);
                if(setupAttempt){
                    if(UploadSecurity.initialized(c)||UploadSecurity.openAccess(c)){json(output,409,null,"ALREADY_INITIALIZED","访问方式已设置，请登录后修改");return true;}
                    UploadSecurity.initialize(c,credentials.optString("username"),credentials.optString("password"),credentials.optString("passwordConfirm"));
                }
                String basic="Basic "+java.util.Base64.getEncoder().encodeToString((credentials.optString("username")+":"+credentials.optString("password")).getBytes(StandardCharsets.UTF_8));
                password=UploadSecurity.authorized(c,basic);
                if(password){
                    synchronized(failedLogins){failedLogins.remove(peer);}
                    String token=AuroraLogin.create(c),payload=new JSONObject().put("ok",true).put("data",new JSONObject().put("needsPath",ConsoleSettings.path(c).isEmpty())).toString();
                    byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
                    output.write(("HTTP/1.1 200 OK\r\nSet-Cookie: aurora_web="+token+"; Path="+ConsolePath.base(c)+"; HttpOnly; SameSite=Strict; Max-Age=28800\r\nContent-Type: application/json\r\nCache-Control: no-store\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));output.write(bytes);
                    WebAudit.login(c,peer,true);if(setupAttempt)WebAudit.record(c,peer,"创建控制台账号","首次设置",200);return true;
                }
            }
            if(path.startsWith("/api/v1/updates")&&!existingWeb){json(output,401,null,"AUTH_REQUIRED","请登录主控制台");return true;}
            if(path.equals("/api/v1/security")&&!existingWeb){json(output,401,null,"AUTH_REQUIRED","请登录主控制台");return true;}
            if(path.startsWith("/api/v1/system/screenshots")&&!existingWeb){json(output,401,null,"AUTH_REQUIRED","请登录主控制台");return true;}
            boolean sensitive=path.equals("/api/v1/apps/manage")||path.equals("/api/v1/actions")||path.startsWith("/api/v1/system/")||path.startsWith("/api/v1/logs")||path.startsWith("/api/v1/network/")||!request[0].equals("GET")||path.startsWith("/console/")||path.startsWith("/api/v1/services");
            if(!password&&!session&&!loginAsset&&(!loopback||sensitive)){
                if(credentialAttempt)synchronized(failedLogins){
                    long now=android.os.SystemClock.elapsedRealtime();long[] failure=failedLogins.get(peer);
                    if(failure!=null&&now-failure[0]<60000&&failure[1]>=8){json(output,429,null,"RATE_LIMIT","登录尝试过多，请一分钟后重试");status=429;return true;}
                    if(failure==null||now-failure[0]>=60000){if(failedLogins.size()>64)failedLogins.clear();failure=new long[]{now,0};failedLogins.put(peer,failure);}failure[1]++;
                }
                if(path.startsWith("/console/")&&request[0].equals("GET")){ConsolePath.redirect(output,ConsolePath.base(c)+"console/login.html");status=302;return true;}unauthorized(output);status=401;if(credentialAttempt)WebAudit.login(c,peer,false);return true;
            }
            if(password||session){if(credentialAttempt)synchronized(failedLogins){failedLogins.remove(peer);}if(password)WebAudit.login(c,peer,true);}
            if(request[0].equals("GET")&&(path.equals("/console/")||path.equals("/console/index.html")||path.equals("/console/settings.html")||path.equals("/console/login.html")&&existingWeb)){ConsolePath.redirect(output,ConsolePath.base(c)+"console/app.html");status=302;return true;}
            String expectedTheme=headers.get("x-aurora-theme");
            boolean themeWrite=request[0].equals("POST")&&(path.startsWith("/api/v1/config/appearance")||path.startsWith("/api/v1/config/native")||path.equals("/api/v1/apps/layout")||path.equals("/api/v1/apps/cards")||path.startsWith("/api/v1/bgm/")||path.equals("/api/v1/actions")||path.equals("/api/v1/apps/manage"));
            if(themeWrite&&expectedTheme!=null&&!expectedTheme.equals(AuroraThemes.current(c))){json(output,409,null,"THEME_CHANGED","当前主题已切换，请刷新设置页面");return true;}
            logged=request[0].equals("POST")||!loopback&&(path.contains("screen")||path.endsWith("events"));
            if(path.startsWith("/api/v1/")&&AuroraRoutes.pending(AuroraRoutes.canonical(path.substring(8)))){json(output,501,null,"NOT_IMPLEMENTED","接口已预留，尚未实现");status=501;return true;}
            if(request[0].equals("GET")) {
                if(!path.startsWith("/api/v1/")){
                    String asset=path.substring(1);
                    if(path.startsWith("/console/")||path.startsWith("/core/")){
                        if(logicalAsset==null)throw new FileNotFoundException("页面资源不存在");
                        String published=ConsoleAssets.publicName(c,logicalAsset);
                        if(!published.equals(asset)){ConsolePath.redirect(output,ConsolePath.base(c)+published);status=302;return true;}
                        byte[] content=ConsoleAssets.read(c,logicalAsset);
                        if(logicalAsset.equals("console/login.html"))content=ConsoleLoginPage.render(content,UploadSecurity.initialized(c));
                        binary(output,200,mime(logicalAsset),content,true,false,published.equals(logicalAsset)?"no-cache":"private, max-age=31536000, immutable");
                    }else{
                        byte[] data=uri.getQuery()!=null&&uri.getQuery().equals("packaged=1")?AuroraThemes.packaged(c,asset):AuroraThemes.asset(c,asset);
                        binary(output,200,mime(asset),data,true,asset.startsWith("themes/"));
                    }status=200;return true;
                }
                String route=AuroraRoutes.canonical(path.substring(8));
                if(AuroraRoutes.pending(route)){json(output,501,null,"NOT_IMPLEMENTED","接口已预留，尚未实现");status=501;return true;}
                if(route.startsWith("system/screenshots/file/")){String id=route.substring("system/screenshots/file/".length());byte[] bytes=java.nio.file.Files.readAllBytes(AuroraScreenshots.file(c,id).toPath());binary(output,200,"image/png",bytes,false);WebAudit.record(c,peer,"download=1".equals(uri.getQuery())?"下载桌面截图":"查看桌面截图",id,200);status=200;return true;}
                if(route.startsWith("themes/css/")){json(output,200,new JSONObject().put("theme",route.substring(11)).put("css",AuroraCssThemes.css(c,route.substring(11))),null,null);status=200;return true;}
                if(route.equals("system/screenshot")){binary(output,200,"image/jpeg",frames.snapshot(c),false);status=200;return true;}
                if(route.equals("system/screen.mjpeg")){
                    if(!screenSlots.tryAcquire()){json(output,429,null,"STREAM_LIMIT","最多同时打开两个画面连接");status=429;return true;}
                    try{controls(c).select("screenshot");status=200;WebAudit.record(c,peer,"开始观看实时画面","",200);
                        mjpeg(c,socket,streamOutput,uri.getQuery(),headers.get("cookie"));}finally{screenSlots.release();WebAudit.record(c,peer,"结束观看实时画面","",200);}return true;
                }
                if(route.equals("events")){
                    if(!eventSlots.tryAcquire()){json(output,429,null,"STREAM_LIMIT","最多同时打开四个数据推送连接");status=429;return true;}
                    try{events(c,socket,input,streamOutput,headers,password||session);status=101;}finally{eventSlots.release();}return true;
                }
                if(route.startsWith("assets/thumbnail/")){binary(output,200,"image/jpeg",AuroraLibrary.thumbnail(c,route.substring(17)),false);status=200;return true;}
                if(route.startsWith("assets/file/")){AuroraLibrary.serve(c,route.substring(12),headers.get("range"),streamOutput);status=200;return true;}
                if(route.startsWith("icons/")){binary(output,200,"image/png",AuroraData.icon(c,route.substring(6)),false);status=200;return true;}
                if(route.equals("media/cover")){binary(output,200,"image/jpeg",AuroraData.cover(c),false);status=200;return true;}
                Object data;
            switch(route){
                case "config/native":data=ConsoleNativeSettings.read(c);break;
                case "logs":data=WebAudit.list(c);break;
                case "music/usb/volumes":data=ConsoleUsb.volumes();break;
                case "apps/layout":ConsoleNativeSettings.requireNative(c);data=WebSettings.apps(c);break;
                case "apps/cards":ConsoleNativeSettings.requireNative(c);data=WebSettings.cards(c);break;
                case "logs/export":binary(output,200,"application/x-ndjson; charset=utf-8",WebAudit.export(c),false);WebAudit.record(c,peer,"导出操作日志","完整日志",200);status=200;return true;
                case "updates":data=LauncherUpdater.state(c);break;
                case "system/default-home":data=DefaultHome.status(c);break;
                case "console/addresses":data=ConsoleAddresses.list(c);break;
                case "security":data=ConsoleSettings.get(c);break;
                case "themes/custom-css":data=new JSONObject().put("css",AuroraSettings.css(c));break;
                    case "config/appearance":data=AuroraSettings.get(c);break;
                    case "config/appearance/presets":data=AuroraPresets.get(c);break;
                    case "music/local/settings":data=AuroraMusicSettings.call(c,"get",null);break;
                    case "system/screenshots":data=AuroraScreenshots.list(c);WebAudit.record(c,peer,"查看截图库","",200);break;
                    case "inputs":data=AuroraInputs.list(c);break;
                    case "inputs/shortcut":data=InputShortcut.get(c);break;
                    case "assets":data=AuroraLibrary.list(c);break;
                    case "actions":data=AuroraActions.list();break;
                    case "voice":data=AuroraVoice.state(c);break;
                    case "backgrounds":JSONArray backgrounds=new JSONArray(),all=AuroraLibrary.list(c);for(int n=0;n<all.length();n++)if(all.getJSONObject(n).getString("kind").equals("background"))backgrounds.put(all.get(n));data=backgrounds;break;
                    case "apps/jobs":data=AuroraLibrary.jobs();break;
                    case "services/cities":data=AuroraServices.cities(c,android.net.Uri.parse(request[1]).getQueryParameter("name")==null?"":android.net.Uri.parse(request[1]).getQueryParameter("name"));break;
                    case "services":data=AuroraServices.status(c);break;
                    case "spectrum":data=AuroraSpectrum.read(c);break;
                    case "saver":data=new JSONObject().put("active",AuroraSaver.active);break;
                    case "bootstrap":data=bootstrap(c);break;
                    case "time":data=AuroraData.time(c);break;
                    case "weather":data=AuroraData.weather(c);break;
                    case "media":data=AuroraData.media(c);break;
                    case "bgm":data=AuroraBgm.state(c);break;
                    case "bgm/library":data=AuroraBgm.library(c);break;
                    case "media/queue":data=queue(c);break;
                    case "apps":data=AuroraData.apps(c);break;
                    case "apps/manage":data=AppManagement.list(c);break;
                    case "status":data=AuroraData.status(c);break;
                    case "network/traffic":data=NetworkTraffic.state(c);break;
                    case "network/traffic/history":{android.net.Uri q=android.net.Uri.parse(request[1]);String uid=q.getQueryParameter("uid"),hours=q.getQueryParameter("hours");data=NetworkTraffic.history(c,uid==null?-1:Integer.parseInt(uid),hours==null?24:Integer.parseInt(hours));break;}
                    case "system/idle-standby":data=IdleStandby.state(c);break;
                    case "system/debug-native-card":data=NowPlayingCard.diagnostics();break;
                    case "system/debug-theme":data=AuroraActivity.diagnostics();break;
                    case "system/capabilities":data=controls(c).capabilities().put("screenStream",frames.state());break;
                    case "themes":data=AuroraThemes.list(c);break;
                    case "config":data=AuroraThemes.publicConfig(c);break;
                    default:json(output,404,null,"NOT_FOUND","接口不存在");status=404;return true;
                }
                json(output,200,data,null,null);status=200;return true;
            }
            if(!request[0].equals("POST")){json(output,405,null,"METHOD","此接口仅支持GET或POST");status=405;return true;}
            if(path.equals("/api/v1/music/usb/upload")){
                if(headers.containsKey("transfer-encoding"))throw new IllegalArgumentException("上传需要明确文件长度");
                JSONObject uploaded=ConsoleUsb.upload(c,input,Long.parseLong(headers.getOrDefault("content-length","0")),headers.getOrDefault("x-usb-volume",""),java.net.URLDecoder.decode(headers.getOrDefault("x-file-name",""),"UTF-8"));
                WebAudit.record(c,peer,"上传 U 盘歌曲",uploaded.optString("volume")+" / "+uploaded.optString("name")+" · "+uploaded.optLong("bytes")+" B",200);
                json(output,200,uploaded,null,null);status=200;return true;
            }
            if(path.equals("/api/v1/updates/upload")){
                if(headers.containsKey("transfer-encoding"))throw new IllegalArgumentException("上传需要明确文件长度");
                JSONObject uploaded=LauncherUpdater.upload(c,input,Long.parseLong(headers.getOrDefault("content-length","0")));
                WebAudit.record(c,peer,"上传桌面更新包","v"+uploaded.optString("version")+" · "+uploaded.optLong("bytes")+" bytes",200);
                json(output,200,uploaded,null,null);status=200;return true;
            }
            if(AuroraRoutes.canonical(path.substring(8)).equals("assets/upload")||path.equals("/api/v1/backgrounds/upload")){
                if(headers.containsKey("transfer-encoding"))throw new IllegalArgumentException("上传需要文件长度");
                String name=java.net.URLDecoder.decode(headers.getOrDefault("x-file-name",""),"UTF-8");
                if(path.equals("/api/v1/backgrounds/upload")&&!name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp|mp4|webm|mkv)"))throw new IllegalArgumentException("请选择图片或视频背景");
                JSONObject uploaded=AuroraLibrary.upload(c,input,Long.parseLong(headers.getOrDefault("content-length","0")),name);
                json(output,200,uploaded,null,null);status=200;return true;
            }
            if(path.equals("/api/v1/bgm/upload")){
                if(headers.containsKey("transfer-encoding"))throw new IllegalArgumentException("上传需要明确文件长度");
                String name=java.net.URLDecoder.decode(headers.getOrDefault("x-file-name",""),"UTF-8");
                JSONObject uploaded;
                try{uploaded=AuroraBgm.upload(c,input,Integer.parseInt(headers.getOrDefault("content-length","0")),name);}
                catch(Exception failure){WebAudit.record(c,peer,"屏保音乐上传失败",name+" · "+String.valueOf(failure.getMessage()),failure instanceof IllegalArgumentException?400:503);throw failure;}
                WebAudit.record(c,peer,"屏保音乐上传成功",name+" · "+uploaded.optLong("bytes")+" bytes",200);
                json(output,200,uploaded,null,null);status=200;return true;
            }
            if(!headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("application/json"))throw new IllegalArgumentException("请使用JSON请求");
            JSONObject body=body(input,headers);String route=AuroraRoutes.canonical(path.substring(8));Object data=new JSONObject();
            if(AuroraRoutes.pending(route)){json(output,501,null,"NOT_IMPLEMENTED","接口已预留，尚未实现");status=501;return true;}
                if(route.startsWith("themes/css/")){AuroraSettings.only(body,"css");data=AuroraCssThemes.saveCss(c,route.substring(11),body.getString("css"));json(output,200,data,null,null);status=200;return true;}
            switch(route){
                case "config/native":data=ConsoleNativeSettings.save(c,body);WebAudit.record(c,peer,"修改极简桌面设置",String.valueOf(body.getJSONObject("changes").names()),200);break;
                case "config/native/reset-color":data=ConsoleNativeSettings.resetColor(c,body);break;
                case "apps/uninstall":AuroraSettings.only(body,"package","confirm");WebApps.uninstall(c,body.getString("package"),body.optBoolean("confirm"));data=AppManagement.list(c);break;
                case "apps/layout":ConsoleNativeSettings.requireNative(c);AuroraSettings.only(body,"apps");WebSettings.saveApps(c,body.getJSONArray("apps"));AppManagement.revision(c);data=WebSettings.apps(c);c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()).putExtra("kind","settings-layout"));break;
                case "apps/cards":ConsoleNativeSettings.requireNative(c);AuroraSettings.only(body,"package","shown");WebSettings.card(c,body.getString("package"),body.getBoolean("shown"));data=WebSettings.cards(c);c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()).putExtra("kind","settings-layout"));break;
                case "updates/config":data=LauncherUpdater.configure(c,body);break;
                case "system/default-home":data=DefaultHome.takeOver(c,body);break;
                case "system/default-home/restore":data=DefaultHome.restore(c,body);break;
                case "updates/check":AuroraSettings.only(body);data=LauncherUpdater.request(c,"check");break;
                case "updates/download":AuroraSettings.only(body);data=LauncherUpdater.request(c,"download");break;
                case "updates/cancel":AuroraSettings.only(body);data=LauncherUpdater.request(c,"cancel");break;
                case "updates/install":AuroraSettings.only(body);data=LauncherUpdater.request(c,"install");break;
                case "updates/install-upload":data=LauncherUpdater.requestUploaded(c,body);break;
                case "updates/rollback":AuroraSettings.only(body,"confirmed");if(!body.optBoolean("confirmed"))throw new IllegalArgumentException("请确认回退");data=LauncherUpdater.request(c,"rollback");break;
                case "updates/clean/confirm":AuroraSettings.only(body);data=new JSONObject().put("confirmationToken",confirmations.issue(peer+"|updates-clean|"+headers.get("cookie"),android.os.SystemClock.elapsedRealtime())).put("expiresInSeconds",60);break;
                case "updates/clean":AuroraSettings.only(body,"confirmationToken","confirmationText");if(!confirmations.consume(body.optString("confirmationToken"),peer+"|updates-clean|"+headers.get("cookie"),android.os.SystemClock.elapsedRealtime())){json(output,409,null,"CONFIRMATION_REQUIRED","确认已失效或不匹配，请重新确认");return true;}data=LauncherUpdater.request(c,"clean");break;
                case "security":data=ConsoleSettings.save(c,body);break;
                case "themes/custom":data=AuroraCssThemes.create(c,body);break;
                case "themes/custom/delete":AuroraCssThemes.remove(c,body);break;
                case "system/screenshots/capture":AuroraSettings.only(body,"resolution");String resolution=body.optString("resolution","4k");if(!resolution.equals("4k")&&!resolution.equals("native"))throw new IllegalArgumentException("截图分辨率无效");data=AuroraScreenshots.capture(c,resolution.equals("4k"));WebAudit.record(c,peer,"拍摄桌面截图",data.toString(),200);break;
                case "system/screenshots/delete":AuroraSettings.only(body,"id");AuroraScreenshots.delete(c,body.getString("id"));WebAudit.record(c,peer,"删除桌面截图",body.getString("id"),200);break;
                case "logs/clear":AuroraSettings.only(body,"confirm");WebAudit.clear(c,Boolean.TRUE.equals(body.opt("confirm")),peer);data=new JSONObject().put("cleared",true);break;
                case "voice":data=AuroraVoice.save(c,body);break;
                case "voice/speak":data=AuroraVoice.speak(c,body,false);break;
                case "voice/test":data=AuroraVoice.speak(c,body,true);break;
                case "voice/stop":data=AuroraVoice.stop(c);break;
                case "actions":data=AuroraActions.run(c,body,password||session);break;
                case "themes/switch":data=AuroraActions.switchTheme(c,body.getString("theme"));break;
                case "backgrounds/delete":AuroraLibrary.file(c,body.getString("id"),"background");AuroraLibrary.remove(c,body.getString("id"),body.optBoolean("confirm"));break;
                case "themes/custom-css":data=AuroraSettings.css(c,body.getString("css"));break;
                case "config/appearance":data=AuroraSettings.save(c,body);break;
                case "config/appearance/presets":data=AuroraPresets.save(c,body);break;
                case "config/appearance/preview":data=AuroraSettings.apply(c,body,false);break;
                case "music/local/settings":data=AuroraMusicSettings.call(c,"save",body);break;
                case "music/local/scan":data=AuroraMusicSettings.call(c,"scan",null);break;
                case "inputs/name":data=AuroraInputs.name(c,body);break;
                case "inputs/switch":AuroraInputs.open(c,body.getString("id"));break;
                case "inputs/shortcut":data=InputShortcut.save(c,body);WebAudit.record(c,peer,"设置输入源快捷方式",body.toString(),200);break;
                case "services":data=AuroraServices.save(c,body);break;
                case "services/test":data=AuroraServices.test(c);break;
                case "services/reset":data=AuroraServices.save(c,AuroraServices.defaults());break;
                case "saver/preview":data=AuroraSaver.preview(c);break;
                case "saver/enter":data=AuroraSaver.enter(c);break;
                case "saver/exit":AuroraSaver.exit(c);break;
                case "assets/delete":AuroraLibrary.remove(c,body.getString("id"),body.optBoolean("confirm"));break;
                case "apps/install":data=AuroraLibrary.install(c,body.getString("id"),peer);break;
                case "auth/logout":AuroraLogin.logout(headers.get("cookie"));break;
                case "system/connect":data=TvAdb.authorize(c);break;
                case "system/key":controls(c).select("key").key(AuroraRules.key(body.getString("key")));break;
                case "network/traffic/config":data=NetworkTraffic.configure(c,body);WebAudit.record(c,peer,"修改流量监控",body.toString(),200);break;
                case "network/traffic/export":data=NetworkTraffic.state(c);WebAudit.record(c,peer,"导出流量监控","当前采样与连接快照",200);break;
                case "network/traffic/clear":NetworkTraffic.clear(c,body);data=new JSONObject().put("clearing",true);WebAudit.record(c,peer,"清空流量监控历史","",200);break;
                case "system/idle-standby/preview":AuroraSettings.only(body);data=IdleStandby.preview(c);break;
                case "system/idle-standby":data=IdleStandby.save(c,body);WebAudit.record(c,peer,"设置无操作自动待机",body.toString(),200);break;
                case "system/standby/confirm":data=new JSONObject().put("confirmationToken",confirmations.issue(peer,android.os.SystemClock.elapsedRealtime())).put("expiresInSeconds",60);break;
                case "system/standby":
                    if(!confirmations.consume(body.optString("confirmationToken"),peer,android.os.SystemClock.elapsedRealtime())){
                        json(output,409,null,"CONFIRM_REQUIRED","请再次确认待机，确认凭据仅可使用一次且60秒内有效");status=409;return true;}
                    SystemControl backend=controls(c).select("standby");WebAudit.record(c,peer,"确认电视待机",backend.name(),200);
                    json(output,200,new JSONObject().put("accepted",true),null,null);output.flush();status=200;
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->new Thread(()->{try{backend.powerStandby();}catch(Exception e){WebAudit.record(c,peer,"待机失败",String.valueOf(e.getMessage()),500);}},"aurora-standby").start(),300);return true;
                case "system/wifi":case "system/bluetooth":case "system/display":
                    String setting=route.endsWith("wifi")?android.provider.Settings.ACTION_WIFI_SETTINGS:route.endsWith("bluetooth")?android.provider.Settings.ACTION_BLUETOOTH_SETTINGS:android.provider.Settings.ACTION_DISPLAY_SETTINGS;
                    c.startActivity(SystemSettings.intent(c,setting).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));break;
                case "apps/manage":data=AppManagement.change(c,body);WebAudit.record(c,peer,"修改应用设置",body.optString("package")+" · "+(body.has("enabled")?"启用状态":body.has("visible")?"显示状态":body.has("direction")?"排序":body.has("name")?"名称":"图标"),200);break;
                case "apps/launch":controls(c).select("launch").launch(body.getString("package"));break;
                case "themes/open":AuroraThemes.open(c);break;
                case "config":synchronized(AuroraThemes.class){
                    JSONObject config=AuroraThemes.config(c);
                    for(Iterator<String> keys=body.keys();keys.hasNext();){String key=keys.next();
                        if(key.equals("locale")){AuroraSettings.choice(body.get(key),"zh-CN","en-US");DesktopLanguage.set(c,body.getString(key));config.put(key,body.getString(key));}
                        else if(key.equals("consoleLocale")){AuroraSettings.choice(body.get(key),"zh-CN","en-US");config.put(key,body.getString(key));}
                        else if(key.equals("theme")){String name=AuroraThemes.id(body.getString(key));AuroraThemes.manifest(c,name);config.put(key,name);config.remove("forcePackaged");}
                        else if(key.equals("icon_fallback")){String value=body.getString(key);if(!value.equals("app")&&!value.equals("default"))throw new IllegalArgumentException("图标回退方式无效");config.put(key,value);}
                        else if(key.equals("system_backends")){
                            JSONObject choices=body.getJSONObject(key);
                            for(Iterator<String> capabilities=choices.keys();capabilities.hasNext();){String capability=capabilities.next();String backendChoice=choices.getString(capability);
                                if(!Arrays.asList("key","launch","standby","screenshot").contains(capability)
                                        ||!Arrays.asList("auto","AndroidAPI","AdbLoopbackBackend","PrivilegedBackend","AccessibilityBackend").contains(backendChoice))throw new IllegalArgumentException("系统后端选择无效");
                            }config.put(key,choices);
                        }
                        else throw new IllegalArgumentException("不允许修改此配置字段："+key);}
                    AuroraThemes.save(c,config);if(body.has("theme")){AuroraSettings.preview=null;AuroraBackgrounds.changed(c);}data=AuroraThemes.publicConfig(c);}break;
                default:
                    if(route.startsWith("media/"))data=AuroraData.transport(c,route.substring(6),body);
                    else if(route.startsWith("bgm/"))data=AuroraBgm.command(c,route.substring(4),body);
                    else {json(output,404,null,"NOT_FOUND","接口不存在");status=404;return true;}
            }
            json(output,200,data,null,null);status=200;
        }catch(FileNotFoundException e){status=404;if(!streamOutput.started)json(output,status,null,"NOT_FOUND",e.getMessage()==null?"文件不存在":e.getMessage());}
        catch(IllegalArgumentException|org.json.JSONException e){status=400;if(!streamOutput.started)json(output,status,null,"INVALID_ARGUMENT",String.valueOf(e.getMessage()));}
        catch(UnsupportedOperationException e){status=501;if(!streamOutput.started)json(output,status,null,"NOT_IMPLEMENTED",String.valueOf(e.getMessage()));}
        catch(Exception e){status=503;if(!streamOutput.started)json(output,status,null,"UNAVAILABLE",e.getMessage()==null?"能力暂不可用":e.getMessage());}
        finally{if(logged)WebAudit.record(c,peer,request[0]+" "+path,"",status);}
        return true;
    }
    private static final class WriteTracker extends FilterOutputStream {
        boolean started;WriteTracker(OutputStream out){super(out);}
        @Override public void write(int value)throws IOException{started=true;out.write(value);}
        @Override public void write(byte[] bytes,int offset,int length)throws IOException{started=true;out.write(bytes,offset,length);}
    }
    private static final java.util.concurrent.ScheduledExecutorService streamTimers=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"aurora-stream-watchdog");t.setDaemon(true);return t;});
    private static boolean cookie(String header){if(header==null)return false;for(String item:header.split(";"))if(item.trim().equals("aurora_session="+themeSession))return true;return false;}
    static JSONObject bootstrap(Context c)throws Exception{return new JSONObject().put("apiVersion",1).put("stage",2).put("console",ConsolePath.base(c)+"console/app.html").put("legacyConsole",ConsolePath.base(c)+"console/app.html").put("time",AuroraData.time(c)).put("weather",AuroraData.weather(c))
            .put("media",AuroraData.media(c)).put("apps",AuroraData.apps(c)).put("status",AuroraData.status(c)).put("config",AuroraThemes.publicConfig(c)).put("appearance",AuroraSettings.get(c)).put("themes",AuroraThemes.list(c));}
    private static JSONArray queue(Context c)throws Exception{
        android.media.session.MediaController controller=AuroraData.controller(c);JSONArray result=new JSONArray();
        if(controller==null||controller.getQueue()==null)return result;
        for(android.media.session.MediaSession.QueueItem item:controller.getQueue())result.put(new JSONObject().put("id",item.getQueueId()).put("title",item.getDescription().getTitle()).put("subtitle",item.getDescription().getSubtitle()));return result;
    }
    private static JSONObject body(InputStream input,Map<String,String> headers)throws Exception {
        if(headers.containsKey("transfer-encoding"))throw new IllegalArgumentException("不接受分块JSON请求");
        int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<0||length>65536)throw new IllegalArgumentException("请求过大");
        byte[] bytes=new byte[length];new DataInputStream(input).readFully(bytes);return new JSONObject(length==0?"{}":new String(bytes,StandardCharsets.UTF_8));
    }
    private static String mime(String path){if(path.endsWith(".ttf"))return "font/ttf";if(path.endsWith(".otf"))return "font/otf";if(path.endsWith(".txt"))return "text/plain";if(path.endsWith(".js"))return "application/javascript";if(path.endsWith(".css"))return "text/css";if(path.endsWith(".json"))return "application/json";if(path.endsWith(".png"))return "image/png";if(path.endsWith(".jpg"))return "image/jpeg";if(path.endsWith(".svg"))return "image/svg+xml";return "text/html";}
    private static void unauthorized(OutputStream out)throws Exception {
        byte[] body=new JSONObject().put("ok",false).put("error",new JSONObject().put("code","AUTH_REQUIRED").put("message","请先登录控制中心")).toString().getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
    }
    static void json(OutputStream out,int status,Object data,String code,String message)throws Exception {
        JSONObject result=new JSONObject().put("ok",status<400);
        if(status<400)result.put("data",data==null?JSONObject.NULL:data);
        else result.put("error",new JSONObject().put("code",code).put("message",message));
        binary(out,status,"application/json",result.toString().getBytes(StandardCharsets.UTF_8),false);
    }
    private static void binary(OutputStream out,int status,String type,byte[] data,boolean policy)throws IOException {binary(out,status,type,data,policy,false);}
    private static void binary(OutputStream out,int status,String type,byte[] data,boolean policy,boolean themeFrame)throws IOException {
        binary(out,status,type,data,policy,themeFrame,policy&&type.equals("text/html")?"no-cache":"no-store");
    }
    private static void binary(OutputStream out,int status,String type,byte[] data,boolean policy,boolean themeFrame,String cache)throws IOException {
        data=ConsolePath.body(type,data);
        String csp=policy?"Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; font-src 'self'; connect-src 'self' ws://127.0.0.1:8899; object-src 'none'; frame-ancestors "+(themeFrame?"'self'":"'none'")+"\r\n":"";
        out.write(("HTTP/1.1 "+status+" "+(status<400?"OK":"Error")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: "+cache+"\r\nX-Content-Type-Options: nosniff\r\n"+csp+"Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();
    }
    private static void mjpeg(Context c,Socket socket,OutputStream out,String query,String sessionCookie)throws Exception {
        double fps=1;if(query!=null)for(String item:query.split("&"))if(item.startsWith("fps="))fps=Double.parseDouble(item.substring(4));
        if(!Double.isFinite(fps)||fps<0.2||fps>2)throw new IllegalArgumentException("帧率范围0.2～2");
        java.util.concurrent.atomic.AtomicLong lastWrite=new java.util.concurrent.atomic.AtomicLong(android.os.SystemClock.elapsedRealtime());
        java.util.concurrent.ScheduledFuture<?> timeout=streamTimers.scheduleAtFixedRate(()->{
            if(android.os.SystemClock.elapsedRealtime()-lastWrite.get()>10000)try{socket.close();}catch(IOException ignored){}
        },2,2,java.util.concurrent.TimeUnit.SECONDS);
        frames.acquire(c,fps);try{
            out.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=aurora-frame\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));out.flush();
            long previous=-1;while(!socket.isClosed()){
                if(!socket.getInetAddress().isLoopbackAddress()&&!AuroraLogin.valid(c,sessionCookie))break;
                byte[] frame=frames.frame;long revision=frames.revision;
                if(frame!=null&&revision!=previous){out.write(("--aurora-frame\r\nContent-Type: image/jpeg\r\nContent-Length: "+frame.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(frame);out.write(new byte[]{13,10});out.flush();lastWrite.set(android.os.SystemClock.elapsedRealtime());previous=revision;}
                else if(frames.failure!=null)throw new IOException(frames.failure);
                Thread.sleep((long)(1000/fps));
            }
        }finally{timeout.cancel(false);frames.release(fps);}
    }
    private static void events(Context c,Socket socket,InputStream input,OutputStream out,Map<String,String> headers,boolean authorized)throws Exception {
        String key=headers.get("sec-websocket-key");
        if(!"websocket".equalsIgnoreCase(headers.get("upgrade"))||!"13".equals(headers.get("sec-websocket-version"))||key==null||java.util.Base64.getDecoder().decode(key).length!=16)throw new IllegalArgumentException("WebSocket握手无效");
        String accept=java.util.Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
        socket.setSoTimeout(1500);String previous="",lastAppearance="",lastMedia="",lastConfig="",lastApps="";long nextSnapshot=0;int ticks=0;JSONArray apps=AuroraData.apps(c);
        while(!socket.isClosed()){
            if(socket.getInetAddress().isLoopbackAddress()&&!AuroraActivity.visible)break;
            if(!socket.getInetAddress().isLoopbackAddress()&&!AuroraLogin.valid(c,headers.get("cookie")))break;
            String appearance=AuroraSettings.get(c).toString();if(!appearance.equals(lastAppearance)){ws(out,1,new JSONObject().put("type","snapshot").put("data",new JSONObject().put("appearance",new JSONObject(appearance))).toString().getBytes(StandardCharsets.UTF_8));lastAppearance=appearance;}
            if(android.os.SystemClock.elapsedRealtime()>=nextSnapshot){nextSnapshot=android.os.SystemClock.elapsedRealtime()+1000;
            if(ticks++%10==0)apps=AuroraData.apps(c);
            JSONObject snapshot=new JSONObject().put("time",AuroraData.time(c)).put("weather",AuroraData.weather(c)).put("media",AuroraData.media(c)).put("status",AuroraData.status(c)).put("apps",apps).put("config",AuroraThemes.publicConfig(c)).put("appearance",AuroraSettings.get(c)).put("saver",new JSONObject().put("active",AuroraSaver.active));
            String nextMedia=snapshot.getJSONObject("media").toString(),nextConfig=snapshot.getJSONObject("config").toString(),nextApps=apps.toString();
            if(!lastMedia.isEmpty()&&!lastMedia.equals(nextMedia))event(out,"media.changed",snapshot.getJSONObject("media"));
            if(!lastConfig.isEmpty()&&!lastConfig.equals(nextConfig))event(out,"config.changed",snapshot.getJSONObject("config"));
            if(!lastApps.isEmpty()&&!lastApps.equals(nextApps))event(out,"apps.changed",new JSONObject().put("apps",apps).put("added",appDelta(apps,new JSONArray(lastApps))).put("removed",appDelta(new JSONArray(lastApps),apps)));
            lastMedia=nextMedia;lastConfig=nextConfig;lastApps=nextApps;
            String data=snapshot.toString();if(!data.equals(previous)){ws(out,1,new JSONObject().put("type","snapshot").put("sequence",sequence.incrementAndGet()).put("data",snapshot).toString().getBytes(StandardCharsets.UTF_8));previous=data;}}
            if(input.available()>0){DataInputStream in=new DataInputStream(input);int first=in.readUnsignedByte(),second=in.readUnsignedByte();int length=second&127;if(length==126)length=in.readUnsignedShort();
                if((first&128)==0||(second&128)==0||length>32768||length==127)break;byte[] mask=new byte[4],payload=new byte[length];in.readFully(mask);in.readFully(payload);for(int i=0;i<length;i++)payload[i]^=mask[i%4];
                int opcode=first&15;if(opcode==8){ws(out,8,payload);break;}if(opcode==9)ws(out,10,payload);if(opcode==1){try{if(!authorized)throw new SecurityException("需要登录才能修改预览");JSONObject message=new JSONObject(new String(payload,StandardCharsets.UTF_8));if(!message.optString("type").equals("appearance-preview"))throw new IllegalArgumentException("消息类型无效");if(message.has("theme")&&!message.getString("theme").equals(AuroraThemes.current(c)))throw new IllegalStateException("主题已切换，请刷新设置");AuroraSettings.apply(c,message.getJSONObject("patch"),false);WebAudit.record(c,socket.getInetAddress().getHostAddress(),"WS appearance-preview","",200);}catch(Exception e){WebAudit.record(c,socket.getInetAddress().getHostAddress(),"WS appearance-preview","",authorized?400:401);ws(out,1,new JSONObject().put("type","error").put("message",String.valueOf(e.getMessage())).toString().getBytes(StandardCharsets.UTF_8));}}}
            Thread.sleep(50);
        }
    }
    private static JSONArray appDelta(JSONArray left,JSONArray right)throws Exception{Set<String> known=new HashSet<>();for(int n=0;n<right.length();n++)known.add(right.getJSONObject(n).getString("package"));JSONArray result=new JSONArray();for(int n=0;n<left.length();n++)if(!known.contains(left.getJSONObject(n).getString("package")))result.put(left.get(n));return result;}
    private static void event(OutputStream out,String type,Object data)throws Exception{ws(out,1,new JSONObject().put("type",type).put("sequence",sequence.incrementAndGet()).put("data",data).toString().getBytes(StandardCharsets.UTF_8));}
    private static void ws(OutputStream out,int opcode,byte[] bytes)throws IOException {
        if(opcode==1)bytes=ConsolePath.body("application/json",bytes);
        out.write(128|opcode);if(bytes.length<126)out.write(bytes.length);else if(bytes.length<=65535){out.write(126);out.write(bytes.length>>8);out.write(bytes.length);}
        else{out.write(127);out.write(ByteBuffer.allocate(8).putLong(bytes.length).array());}out.write(bytes);out.flush();
    }
    private static final class FrameHub {
        volatile byte[] frame;volatile String failure;volatile long revision;private int viewers;private Thread worker;
        private final List<Double> rates=new ArrayList<>();private volatile double captureFps=1;
        private final Object captureLock=new Object();private long capturedAt;
        byte[] snapshot(Context c)throws Exception{
            synchronized(captureLock){
                // Collapse concurrent refreshes; live streams and manual preview share the same capture.
                long now=android.os.SystemClock.elapsedRealtime();
                if(frame!=null&&now-capturedAt<200)return frame;
                byte[] next=controls(c).select("screenshot").screenshot();
                frame=next;capturedAt=android.os.SystemClock.elapsedRealtime();revision++;failure=null;
                return next;
            }
        }
        synchronized void acquire(Context c,double fps){viewers++;rates.add(fps);captureFps=Collections.max(rates);if(worker==null)start(c.getApplicationContext());}
        private void start(Context c){frame=null;failure=null;worker=new Thread(()->{
            try{while(watching()){long start=android.os.SystemClock.elapsedRealtime();try{snapshot(c);}catch(Exception e){failure=e.getMessage();}
                Thread.sleep(Math.max(50,(long)(1000/captureFps)-(android.os.SystemClock.elapsedRealtime()-start)));}}
            catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            finally{synchronized(this){worker=null;frame=null;if(viewers>0)start(c);}}
        },"aurora-shared-screen");worker.start();}
        synchronized JSONObject state()throws Exception{return new JSONObject().put("viewers",viewers).put("framesCaptured",revision).put("maxFps",2).put("captureFps",captureFps).put("workerRunning",worker!=null);}
        synchronized boolean watching(){return viewers>0;}
        synchronized void release(double fps){viewers=Math.max(0,viewers-1);rates.remove(Double.valueOf(fps));captureFps=rates.isEmpty()?1:Collections.max(rates);}
    }
}
