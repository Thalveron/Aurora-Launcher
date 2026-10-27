package com.gathin.tvhome;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Password-protected LAN receiver. Uploads can only target managed media folders. */
final class BackgroundUploadServer implements AutoCloseable {
    interface Listener { void uploaded(String kind); }
    private final Context context;
    private final Listener listener;
    private final ServerSocket server;
    private ServerSocket internal;
    private final ExecutorService worker = new ThreadPoolExecutor(6, 6, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16));
    // Long-lived event/video streams must never occupy the ordinary HTTP workers.
    private final ExecutorService streamWorker = new ThreadPoolExecutor(0, 8, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

    private final Map<String,long[]> attempts = new LinkedHashMap<>();
    static String address(Context c) throws SocketException {
        List<String> urls = new ArrayList<>();
        Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
        while (networks != null && networks.hasMoreElements()) {
            NetworkInterface n = networks.nextElement();
            if (!n.isUp() || n.isLoopback()) continue;
            Enumeration<InetAddress> ips = n.getInetAddresses();
            while (ips.hasMoreElements()) { InetAddress ip = ips.nextElement(); if (ip instanceof Inet4Address && !ip.isLoopbackAddress() && !ip.isLinkLocalAddress()) urls.add("http://"+ip.getHostAddress()+":"+ConsoleSettings.port(c)+ConsolePath.base(c)); }
        }
        return urls.isEmpty() ? "请先连接局域网" : String.join("\n", urls);
    }
    static String path(Context c) {
        return ConsoleSettings.path(c);
    }

    private volatile boolean open = true;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    BackgroundUploadServer(Context c, Listener callback) throws IOException {
        context = c.getApplicationContext(); listener = callback;
        server = new ServerSocket(); server.setReuseAddress(true);
        try{
            server.bind(new InetSocketAddress(ConsoleSettings.port(c)));
            if(ConsoleSettings.port(c)!=8899){internal=new ServerSocket();internal.setReuseAddress(true);internal.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),8899));}
        }catch(IOException failure){server.close();if(internal!=null)internal.close();throw failure;}
        accept(server);if(internal!=null)accept(internal);
    }
    private void accept(ServerSocket listening){
        Thread accept = new Thread(() -> {
            while (open) try {
                Socket socket = listening.accept();
                connections.add(socket);
                try { worker.execute(() -> handle(socket)); }
                catch (RejectedExecutionException busy) { connections.remove(socket); socket.close(); }
            } catch (IOException | RejectedExecutionException stopped) { if (!open) break; }
        }, "background-upload");
        accept.setDaemon(true); accept.start();
    }
    String address() throws SocketException {
        return address(context);
    }
    private static String line(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') out.write(b);
            if (out.size() > limit) throw new IOException("请求头过长");
        }
        if (b == -1 && out.size() == 0) throw new EOFException();
        return out.toString(StandardCharsets.US_ASCII.name());
    }
    private static final ThreadLocal<Integer> responseStatus=new ThreadLocal<>();
    private void handle(Socket socket) {
        File temp = null;String auditIp=socket.getInetAddress().getHostAddress(),auditAction="",auditDetail="";boolean audit=false,handedOff=false;responseStatus.set(500);
        try {
            socket.setSoTimeout(60_000);
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            String[] request = line(input, 2048).split(" ");
            Map<String,String> headers = new HashMap<>(); int total = 0;
            while (true) {
                String header = line(input, 4096); if (header.isEmpty()) break;
                total += header.length(); if (total > 16_384) throw new IOException("请求头过长");
                int split = header.indexOf(':');
                if (split > 0) headers.put(header.substring(0, split).toLowerCase(Locale.ROOT), header.substring(split + 1).trim());
            }
            if(ConsolePath.prepare(context,socket,request,headers,output))return;
            String route=request[1].split("\\?",2)[0];
            if("GET".equals(request[0]) && (route.equals("/api/v1/events") || route.equals("/api/v1/system/screen.mjpeg"))) {
                String scope=ConsolePath.captureScope();
                try {
                    streamWorker.execute(() -> {
                        ConsolePath.restoreScope(scope);
                        try { AuroraHttp.handle(context,socket,request,headers,input,output); }
                        catch(Exception disconnected) { /* Viewer disconnected. */ }
                        finally { responseStatus.remove();ConsolePath.clear();closeConnection(socket); }
                    });
                    handedOff=true;
                } catch(RejectedExecutionException busy) {
                    respond(output,503,"application/json","{\"ok\":false,\"error\":{\"code\":\"BUSY\",\"message\":\"Too many live streams\"}}");
                }
                return;
            }
            if(AuroraHttp.handle(context,socket,request,headers,input,output))return;
            String base = ConsolePath.base(context)+"desktop/";
            if (request.length != 3 || !request[1].startsWith(base)) { respond(output, 404, "text/plain", "页面不存在"); return; }
            String peer = socket.getInetAddress().getHostAddress();
            synchronized (attempts) {
                long now = android.os.SystemClock.elapsedRealtime();
                long[] state = attempts.get(peer);
                if (state != null && now-state[0] < 60_000 && state[1] >= 8) { respond(output,429,"text/plain","密码尝试过多，请一分钟后重试"); return; }
                if (!AuroraLogin.valid(context,headers.get("cookie"))) {
                    if("GET".equals(request[0])&&(request[1].equals(base)||request[1].startsWith(base+"?")))ConsolePath.redirect(output,ConsolePath.base(context)+"console/login.html");
                    else respond(output,401,"application/json","{\"ok\":false,\"error\":{\"code\":\"AUTH_REQUIRED\",\"message\":\"Sign in through the main console\"}}");
                    return;
                }
                attempts.remove(peer);WebAudit.login(context,peer,true);
            }
            auditAction=request[0]+" "+request[1].substring(base.length());audit="POST".equals(request[0])||("GET".equals(request[0])&&!request[1].endsWith(".js")&&!request[1].endsWith(".css")&&!request[1].contains("thumb/")&&WebAudit.view(peer,auditAction));auditDetail=headers.containsKey("x-file-name")?"文件："+URLDecoder.decode(headers.get("x-file-name"),"UTF-8")+"，字节："+headers.getOrDefault("content-length",""):"";
            if("GET".equals(request[0])&&request[1].equals(base+"api/log-export")){binary(output,"application/x-ndjson",WebAudit.export(context));return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/logs")){respond(output,200,"application/json",WebAudit.list(context).toString());return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/adb-status")){respond(output,200,"application/json",TvAdb.status().toString());return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/installed")){respond(output,200,"application/json",WebApps.list(context).toString());return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/inputs")){respond(output,200,"application/json",WebInputs.list(context).toString());return;}
            if ("GET".equals(request[0]) && request[1].equals(base+"usb-volumes")) {
                org.json.JSONArray result = new org.json.JSONArray();
                for (File root:UsbMusic.volumes()) { org.json.JSONObject item=new org.json.JSONObject(); item.put("id",root.getName()); item.put("label","U盘 "+root.getName()+" · 剩余 "+(root.getUsableSpace()/1024/1024)+" MB"); result.put(item); }
                respond(output,200,"application/json",result.toString()); return;
            }
            if ("GET".equals(request[0]) && (request[1].equals(base)||request[1].startsWith(base+"?"))) {
                String current=AuroraThemes.current(context);
                java.net.URI pageUri=new java.net.URI(request[1]);Map<String,String> params=new HashMap<>();
                if(pageUri.getRawQuery()!=null)for(String entry:pageUri.getRawQuery().split("&")){String[] pair=entry.split("=",2);if(pair.length==2)params.put(pair[0],java.net.URLDecoder.decode(pair[1],"UTF-8"));}
                String section=params.getOrDefault("section","overview"),theme=params.getOrDefault("theme",current);
                if(!theme.equals(current)){respond(output,409,"text/plain","Theme changed. Reload the main console.");return;}
                if(section.equals("system")){ConsolePath.redirect(output,ConsolePath.base(context)+"console/settings.html#services");return;}
                if(section.equals("backgrounds")){ConsolePath.redirect(output,ConsolePath.base(context)+"console/settings.html#background");return;}
                if(section.equals("security")){ConsolePath.redirect(output,ConsolePath.base(context)+"console/settings.html#security");return;}
                if(!current.equals("legacy")&&!java.util.Arrays.asList("apps","usb","logs","security").contains(section)){ConsolePath.redirect(output,ConsolePath.base(context)+"console/settings.html#legacy");return;}
                if(!java.util.Arrays.asList("overview","layout","appearance","music","ambient","backgrounds","apps","usb","system","devices","logs","advanced","security").contains(section)){respond(output,400,"text/plain","Invalid settings section");return;}
                String page = asset("web/index.html").replace("__BASE__",base);
                if(params.containsKey("section")){
                    String early="<script>document.documentElement.classList.add(\"embedded\");try{document.documentElement.dataset.consoleTheme=localStorage.getItem(\"aurora-console-theme\")||\"light\";}catch(e){document.documentElement.dataset.consoleTheme=\"light\";}</script><link id=\"console-day-css\" rel=\"stylesheet\" href=\""+ConsolePath.base(context)+"console/day.css\"><style id=\"section-boot-style\">aside{display:none!important}.page{display:none!important}.page#"+section+"{display:block!important}</style>";
                    page=page.replace("</head>",early+"</head>");
                }
                respond(output, 200, "text/html", page); return;
            }
            if("GET".equals(request[0])&&(request[1].equals(base+"app.js")||request[1].equals(base+"style.css"))){boolean script=request[1].endsWith(".js");respond(output,200,script?"application/javascript":"text/css",asset(script?"web/app.js":"web/style.css"));return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/state")){respond(output,200,"application/json",WebSettings.state(context).toString());return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/apps")){respond(output,200,"application/json",WebSettings.apps(context).toString());return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/cards")){respond(output,200,"application/json",WebSettings.cards(context).toString());return;}
            if("GET".equals(request[0])&&request[1].equals(base+"api/preview")){
                try{binary(output,"image/jpeg",TvAdb.screen(context));}catch(Exception e){respond(output,409,"text/plain",e.getMessage());}return;
            }
            if ("GET".equals(request[0]) && request[1].equals(base+"audio-library")) {
                org.json.JSONArray result=new org.json.JSONArray();
                String selected=AmbientMusic.prefs(context).getString("selected","");
                for(File f:AmbientMusic.items(context)){
                    org.json.JSONObject item=new org.json.JSONObject();item.put("id",f.getName());
                    item.put("label",AmbientMusic.name(context,f));item.put("selected",selected.equals(f.getName()));result.put(item);
                }
                respond(output,200,"application/json",result.toString());return;
            }
            if ("GET".equals(request[0]) && request[1].equals(base + "library")) {
                org.json.JSONArray items = new org.json.JSONArray();
                String selected=context.getSharedPreferences("background",0).getString("selected", "");
                for(File f:BackgroundStore.items(context)) {
                    org.json.JSONObject item = new org.json.JSONObject(); item.put("id",f.getName()); item.put("kind",BackgroundStore.kind(f));
                    item.put("label", (BackgroundStore.kind(f).equals("video")?"视频":"图片") + " · " + new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(f.lastModified())));
                    item.put("selected", f.getName().equals(selected) && !BackgroundStore.mode(context).equals("default"));
                    String ambientMode=context.getSharedPreferences("background",0).getString("ambient_mode","follow");
                    item.put("ambientSelected",!ambientMode.equals("follow")&&!ambientMode.equals("default")&&f.getName().equals(context.getSharedPreferences("background",0).getString("ambient_selected","")));items.put(item);
                }
                respond(output,200,"application/json",items.toString()); return;
            }
            if ("GET".equals(request[0]) && request[1].startsWith(base + "thumb/")) {
                File f=BackgroundStore.byId(context,request[1].substring((base+"thumb/").length()));
                if(f==null){respond(output,404,"text/plain","背景不存在");return;}
                File thumb=BackgroundStore.thumbnail(context,f);
                if(!thumb.isFile()){respond(output,404,"text/plain","暂无预览");return;}
                byte[] bytes=new byte[(int)thumb.length()];
                try(DataInputStream in=new DataInputStream(new FileInputStream(thumb))){in.readFully(bytes);}
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(bytes);output.flush();return;
            }
            if (!"POST".equals(request[0])) { respond(output, 405, "text/plain", "不支持该操作"); return; }
            String origin = headers.get("origin"), host = headers.get("host");
            if (origin != null && !origin.equals("http://" + host)) { respond(output,403,"text/plain","请使用电视显示的上传地址"); return; }
            if(request[1].startsWith(base+"api/")){
                try{
                    org.json.JSONObject body=jsonBody(input,headers);String api=request[1].substring((base+"api/").length());auditDetail=WebAudit.safe(body);boolean rebuild=false,ambientSettings=false;
                    switch(api){
                        case "weather-search":respond(output,200,"application/json",WeatherSearch.search(body.getString("name")).toString());return;
                        case "authorize":respond(output,200,"application/json",TvAdb.authorize(context).toString());return;
                        case "launch-app":WebApps.launch(context,body.getString("package"));break;
                        case "uninstall-app":WebApps.uninstall(context,body.getString("package"),body.optBoolean("confirm",false));break;
                        case "input-name":WebInputs.name(context,body.getString("id"),body.getString("name"));break;
                        case "input":WebInputs.open(context,body.getString("id"));break;
                        case "slideshow":AmbientSlideshow.save(context,body);break;
                        case "settings":{if(!AuroraThemes.current(context).equals("legacy"))throw new IllegalArgumentException("当前主题不是极简桌面，请使用当前主题设置");org.json.JSONObject changes=body.getJSONObject("changes");rebuild=WebSettings.apply(context,changes);java.util.Iterator<String> keys=changes.keys();while(keys.hasNext())if(keys.next().startsWith("ambient:"))ambientSettings=true;break;}
                        case "apps":if(!AuroraThemes.current(context).equals("legacy"))throw new IllegalArgumentException("请在当前主题设置应用排列");WebSettings.saveApps(context,body.getJSONArray("apps"));rebuild=true;break;
                        case "card":WebSettings.card(context,body.getString("package"),body.getBoolean("shown"));rebuild=true;break;
                        case "reset-color":rebuild=WebSettings.resetColor(context,body.getString("id"));break;
                        case "ambient-background":{
                            String mode=body.getString("mode");
                            if(mode.equals("follow")||mode.equals("default"))context.getSharedPreferences("background",0).edit().putString("ambient_mode",mode).putLong("revision",System.currentTimeMillis()).commit();
                            else {File f=BackgroundStore.byId(context,body.getString("id"));if(f==null)throw new IllegalArgumentException("背景不存在");BackgroundStore.selectAmbient(context,f);}break;
                        }
                        case "desktop-default":BackgroundStore.select(context,"default");break;
                        case "audio-builtin":AmbientMusic.prefs(context).edit().remove("selected").putLong("play_request",System.currentTimeMillis()).commit();ambientSettings=true;break;
                        case "follow-theme":context.getSharedPreferences(Config.SETTINGS_NAME,0).edit().remove("columns").remove("animations").remove("advanced_effects").putLong("web_layout_revision",System.currentTimeMillis()).commit();rebuild=true;break;
                        case "upload":{
                            org.json.JSONObject update=new org.json.JSONObject().put("path",body.optString("path",path(context)));
                            String password=body.optString("password","");if(!password.isEmpty())update.put("password",password).put("passwordConfirm",body.optString("passwordConfirm",""));
                            ConsoleSettings.save(context,update);break;
                        }
                        case "open-system":context.startActivity(SystemSettings.intent(context,android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));break;
                        case "open-home-settings":context.startActivity(new android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));break;
                        case "weather-refresh":context.getSharedPreferences("weather_leiyang",0).edit().putLong("fetched",0).commit();break;
                        default:respond(output,404,"text/plain","设置操作不存在");return;
                    }
                    listener.uploaded(rebuild?"settings-layout":ambientSettings?"settings-ambient":"settings");respond(output,200,"application/json",new org.json.JSONObject().put("ok",true).put("path",path(context)).toString());
                }catch(Exception invalid){respond(output,400,"text/plain",invalid.getMessage()==null?"设置未能保存":invalid.getMessage());}return;
            }
            for(String operation:new String[]{"audio-select/","audio-delete/"}) if(request[1].startsWith(base+operation)){
                File f=AmbientMusic.byId(context,request[1].substring((base+operation).length()));
                if(f==null){respond(output,404,"text/plain","音乐不存在");return;}
                if(operation.equals("audio-select/"))AmbientMusic.prefs(context).edit().putString("selected",f.getName()).commit();
                else AmbientMusic.delete(context,f);
                respond(output,200,"text/plain","操作成功");listener.uploaded("settings-ambient");return;
            }
            for(String operation:new String[]{"select/","delete/"}) if(request[1].startsWith(base+operation)) {
                File f=BackgroundStore.byId(context,request[1].substring((base+operation).length()));
                if(f==null){respond(output,404,"text/plain","背景不存在");return;}
                if(operation.equals("select/"))BackgroundStore.selectFile(context,f);else BackgroundStore.delete(context,f);
                respond(output,200,"text/plain","操作成功");listener.uploaded("changed");return;
            }
            String kind = request[1].equals(base + "image") ? "image" : request[1].equals(base + "video") ? "video" : request[1].equals(base+"audio")?"audio":request[1].equals(base+"usb-audio")?"usb":request[1].equals(base+"apk")?"apk":"";
            if (kind.isEmpty()) { respond(output, 404, "text/plain", "页面不存在"); return; }
            if (headers.containsKey("transfer-encoding")) { respond(output, 400, "text/plain", "请使用网页选择文件上传"); return; }
            long length;
            try { length = Long.parseLong(headers.getOrDefault("content-length", "-1")); }
            catch (NumberFormatException invalid) { length = -1; }
            long max = "apk".equals(kind)?300L*1024*1024:"usb".equals(kind)?1024L*1024*1024:"audio".equals(kind)?200L*1024*1024:"video".equals(kind) ? 80L * 1024 * 1024 : 20L * 1024 * 1024;
            if (length <= 0 || length > max) { respond(output, 413, "text/plain", "图片限20 MB，视频限80 MB，音乐限200 MB"); return; }
            File destination = "apk".equals(kind)?WebApps.staging(context):context.getFilesDir();
            String usbName = null;
            if ("usb".equals(kind)) {
                try { destination=UsbMusic.folder(headers.getOrDefault("x-usb-volume","")); usbName=UsbMusic.safeName(URLDecoder.decode(headers.getOrDefault("x-file-name",""),StandardCharsets.UTF_8.name())); }
                catch (IOException invalid) { respond(output,400,"text/plain",invalid.getMessage()); return; }
            }
            if (destination.getUsableSpace() < length + 30L * 1024 * 1024) {
                respond(output, 400, "text/plain", "电视存储空间不足"); return;
            }
            temp = File.createTempFile("upload-", "apk".equals(kind)?".apk":".tmp", destination);
            try (FileOutputStream file = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32 * 1024]; long left = length;
                while (left > 0) {
                    int count = input.read(buffer, 0, (int)Math.min(buffer.length, left));
                    if (count < 0) throw new EOFException("上传中断，请重新上传");
                    file.write(buffer, 0, count); left -= count;
                }
                file.getFD().sync();
            }
            try {
                if("apk".equals(kind)){WebApps.install(context,temp);respond(output,200,"text/plain","应用已安装");return;}
                if ("usb".equals(kind)) {
                    android.media.MediaExtractor extractor = new android.media.MediaExtractor(); boolean audio=false;
                    try { extractor.setDataSource(temp.getPath()); for(int i=0;i<extractor.getTrackCount();i++) { String mime=extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME); if(mime!=null&&mime.startsWith("audio/"))audio=true; } } finally { extractor.release(); }
                    if(!audio)throw new IOException("文件不是可读取的音频，未保存");
                    File saved;
                    synchronized (UsbMusic.class) { saved=UsbMusic.reserve(destination,usbName); if(!temp.renameTo(saved))throw new IOException("U盘保存失败，请检查是否已拔出或只读"); }
                    android.media.MediaScannerConnection.scanFile(context,new String[]{saved.getPath()},null,null);
                    respond(output,200,"text/plain","已保存到 U盘 /Music/极简桌面上传/"+saved.getName()+"。在 Chora 重新扫描本地歌曲即可；同名歌曲保留两份。");
                    listener.uploaded("usb"); return;
                } else if("audio".equals(kind)){
                    String name=URLDecoder.decode(headers.getOrDefault("x-file-name","上传的纯音乐"),StandardCharsets.UTF_8.name()).replaceAll("[\\p{Cntrl}]","");
                    AmbientMusic.save(context,temp,name.substring(0,Math.min(120,name.length())));
                }else BackgroundStore.save(context, temp, kind,"ambient".equals(headers.get("x-background-target")));
                respond(output, 200, "text/plain", "audio".equals(kind)?"纯音乐已保存到电视，在背景音乐设置开启即可使用。":"上传成功，已加入背景库。请手动选择“用于桌面”或“用于屏保”。");
                listener.uploaded(kind);
            } catch (Exception invalid) {
                respond(output, 400, "text/plain", invalid.getMessage() == null ? "媒体文件无法读取" : invalid.getMessage());
            }
        } catch (Exception ignored) {
            // The previous background remains selected if the browser disconnects or validation fails.
        } finally { if(audit)WebAudit.record(context,auditIp,auditAction,auditDetail,responseStatus.get()==null?500:responseStatus.get());responseStatus.remove();ConsolePath.clear();if (temp != null) temp.delete();if(!handedOff)closeConnection(socket); }
    }
    private void closeConnection(Socket socket) {
        connections.remove(socket);
        try { socket.close(); } catch(IOException ignored) {}
    }
    private static void respond(OutputStream out, int code, String type, String body) throws IOException {
        responseStatus.set(code);
        byte[] bytes = ConsolePath.body(type,body.getBytes(StandardCharsets.UTF_8));
        String header = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\nContent-Type: " + type
                + "; charset=utf-8\r\nContent-Length: " + bytes.length
                + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII)); out.write(bytes); out.flush();
    }
    private String asset(String path)throws IOException{try(InputStream in=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private static org.json.JSONObject jsonBody(InputStream in,Map<String,String> headers)throws Exception{
        if(headers.containsKey("transfer-encoding"))throw new IOException("请求格式不支持");int length=Integer.parseInt(headers.getOrDefault("content-length","-1"));if(length<2||length>65536)throw new IOException("设置请求大小无效");byte[] b=new byte[length];new DataInputStream(in).readFully(b);return new org.json.JSONObject(new String(b,StandardCharsets.UTF_8));
    }
    private static void binary(OutputStream out,String type,byte[] bytes)throws IOException{responseStatus.set(200);out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();}
    @Override public void close() {
        open = false;
        try { server.close(); } catch (IOException ignored) {}
        if(internal!=null)try{internal.close();}catch(IOException ignored){}
        for(Socket current:connections)try { current.close(); } catch(IOException ignored) {}
        connections.clear();
        worker.shutdownNow();
        streamWorker.shutdownNow();
    }
    private static final String USB_HTML = """
        <h2>上传歌曲到 U 盘</h2><p>歌曲直接保存到 U 盘。每首最多1 GB；同名文件自动编号，不覆盖。</p>
        <select id="usbVolume"></select> <button onclick="usbVolumes()">刷新U盘</button>
        <input id="usbFiles" type="file" multiple accept="audio/*,.mp3,.flac,.wav,.m4a,.aac,.ogg,.opus"><button id="usbSend">批量上传歌曲到U盘</button>
        <p><small>保存位置：U盘 /Music/极简桌面上传。完成后在 Chora 重新扫描本地歌曲。已添加U盘根目录即可扫描到。</small></p>
        """;
    private static final String USB_SCRIPT = """
        async function usbVolumes(){const select=document.getElementById('usbVolume');try{const r=await fetch('__BASE__usb-volumes');if(!r.ok)throw Error();const list=await r.json();select.replaceChildren();for(const item of list){const o=document.createElement('option');o.value=item.id;o.textContent=item.label;select.append(o);}if(!list.length){const o=document.createElement('option');o.value='';o.textContent='未识别到U盘';select.append(o);}}catch(e){status.textContent='读取U盘失败，请刷新或重新登录';}}usbVolumes();
        document.getElementById('usbSend').onclick=async()=>{
          const volume=document.getElementById('usbVolume').value,files=Array.from(document.getElementById('usbFiles').files),button=document.getElementById('usbSend');
          if(!volume||!files.length){status.textContent='请选择U盘和歌曲';return;}button.disabled=true;
          try{for(let i=0;i<files.length;i++){const f=files[i];
            if(!/\\.(mp3|flac|wav|m4a|aac|ogg|opus)$/i.test(f.name)||f.size>1024*1024*1024)throw Error('歌曲格式不支持或大于1 GB：'+f.name);
            status.textContent='正在上传 '+(i+1)+' / '+files.length+'：'+f.name;
            await new Promise((resolve,reject)=>{const x=new XMLHttpRequest();x.open('POST','__BASE__usb-audio');x.setRequestHeader('X-File-Name',encodeURIComponent(f.name));x.setRequestHeader('X-USB-Volume',volume);x.timeout=3600000;
              x.upload.onprogress=e=>{if(e.lengthComputable)progress.value=e.loaded/e.total*100;};
              x.onload=()=>x.status===200?resolve():reject(Error(x.responseText||'上传失败'));x.onerror=x.ontimeout=()=>reject(Error('上传中断，请检查电视和U盘连接'));x.send(f);});}
            status.textContent='已上传 '+files.length+' 首歌曲到 U盘 /Music/极简桌面上传。请在播放器重新扫描本地歌曲。';usbVolumes();
          }catch(e){status.textContent=e.message+'；已成功的歌曲会保留。';}finally{button.disabled=false;}
        };
        """;
    private static final String PAGE = """
        <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>电视背景上传</title><style>
        body{background:#101419;color:#eef2f5;font:17px system-ui;max-width:600px;margin:60px auto;padding:24px;line-height:1.7}
        h1{font-size:30px}button{background:#bddccf;color:#101419;border:0;border-radius:12px;padding:12px 24px;font:inherit;cursor:pointer}
        input{display:block;margin:26px 0;max-width:100%}small{color:#a9bac6}#status{white-space:pre-wrap;margin-top:24px}progress{width:100%;margin-top:20px}
        </style><h1>给电视换个背景</h1><p>选一张图片，或者一段短视频。文件直接传到你的电视。</p>
        <input id="file" type="file" accept="image/jpeg,image/png,image/webp,video/mp4"><button id="send">上传到背景库</button>
        <progress id="progress" max="100" value="0"></progress><div id="status"></div>
        <p><small>图片：JPG / PNG / WebP，20 MB 以内。<br>视频：H.264 / H.265 MP4，30 秒以内、支持 4K / 8K 等分辨率，能否播放取决于电视，80 MB 以内，静音循环。<br>上传时保持电视的背景管理页面打开；完成后在电视按返回键。</small></p>
        <h2>背景纯音乐</h2><p>保存到电视本地，无需音乐服务器。MP3 / M4A / FLAC / WAV，每首最多200 MB。上传后到电视背景音乐设置开启。</p><input id="audioFile" type="file" accept="audio/*,.mp3,.m4a,.flac,.wav"><button id="audioSend">上传纯音乐</button><div id="audioLibrary"></div><h2>已上传的背景</h2><button onclick="library()">刷新背景库</button><div id="library"></div><script>
        document.getElementById('audioSend').onclick=()=>{const f=document.getElementById('audioFile').files[0];if(!f){status.textContent='请选择音乐文件';return;}if(f.size>200*1024*1024){status.textContent='音乐限200 MB';return;}const b=document.getElementById('audioSend');b.disabled=true;const x=new XMLHttpRequest();x.open('POST','__BASE__audio');x.setRequestHeader('X-File-Name',encodeURIComponent(f.name));x.timeout=600000;x.upload.onprogress=e=>{if(e.lengthComputable)progress.value=e.loaded/e.total*100;};x.onload=()=>{status.textContent=x.responseText;b.disabled=false;audioLibrary();};x.onerror=x.ontimeout=()=>{status.textContent='上传中断，请重试';b.disabled=false;};x.send(f);};
        async function audioLibrary(){const box=document.getElementById('audioLibrary');try{const r=await fetch('__BASE__audio-library');if(!r.ok)throw Error();const items=await r.json();box.replaceChildren();for(const item of items){const row=document.createElement('p');const label=document.createElement('span');label.textContent=item.label+(item.selected?' · 已选择':'');row.append(label);for(const op of ['audio-select','audio-delete']){const b=document.createElement('button');b.textContent=op==='audio-select'?'使用':'删除';b.onclick=async()=>{if(op==='audio-delete'&&!confirm('删除这首音乐？'))return;const r=await fetch('__BASE__'+op+'/'+item.id,{method:'POST'});status.textContent=await r.text();audioLibrary();};row.append(b);}box.append(row);}}catch(e){box.textContent='保持电视背景管理页面打开后刷新';}}audioLibrary();
        const file=document.getElementById('file'),send=document.getElementById('send'),status=document.getElementById('status'),progress=document.getElementById('progress');
        send.onclick=()=>{const f=file.files[0];if(!f){status.textContent='请先选择文件';return;}
        const video=f.type.startsWith('video/')||/\\.mp4$/i.test(f.name);if(f.size>(video?80:20)*1024*1024){status.textContent='文件太大，请先缩小文件';return;}
        send.disabled=true;status.textContent='正在上传…';const x=new XMLHttpRequest();x.open('POST','__BASE__'+(video?'video':'image'));x.timeout=180000;
        x.upload.onprogress=e=>{if(e.lengthComputable)progress.value=e.loaded/e.total*100;};
        x.onload=()=>{status.textContent=x.responseText;send.disabled=false;library();};x.onerror=x.ontimeout=()=>{status.textContent='连接中断，请确认电视页面仍打开，然后重试';send.disabled=false;};x.send(f);};
        async function library(){const box=document.getElementById('library');try{const r=await fetch('__BASE__library');const items=await r.json();box.replaceChildren();
        if(!items.length){box.textContent='还没有上传背景';return;}for(const item of items){const card=document.createElement('div');card.style='padding:18px 0;border-bottom:1px solid #38434b';
        const img=document.createElement('img');img.src='__BASE__thumb/'+item.id;img.style='display:block;width:100%;max-height:240px;object-fit:contain';card.append(img);
        const label=document.createElement('p');label.textContent=item.label+(item.selected?' · 正在使用':'');card.append(label);
        for(const op of ['select','delete']){const b=document.createElement('button');b.textContent=op==='select'?'使用这个背景':'删除';b.style.marginRight='12px';
        b.onclick=async()=>{if(op==='delete'&&!confirm('删除这项背景？'))return;const response=await fetch('__BASE__'+op+'/'+item.id,{method:'POST'});status.textContent=await response.text();library();};card.append(b);}box.append(card);}}
        catch(e){box.textContent='请保持电视背景管理页面打开，再点击刷新';}}library();
        </script></html>
        """;
}
