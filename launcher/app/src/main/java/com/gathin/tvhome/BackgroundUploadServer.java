package com.gathin.tvhome;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Password-protected LAN receiver. Uploads can only target managed media folders. */
final class BackgroundUploadServer implements AutoCloseable {
    private final Context context;
    private final ServerSocket server;
    private ServerSocket internal;
    private final ExecutorService worker = new ThreadPoolExecutor(6, 6, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16));
    // Long-lived event/video streams must never occupy the ordinary HTTP workers.
    private final ExecutorService streamWorker = new ThreadPoolExecutor(0, 8, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

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
    BackgroundUploadServer(Context c) throws IOException {
        context = c.getApplicationContext();
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
        boolean handedOff=false;responseStatus.set(500);
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
            AuroraHttp.json(output,404,null,"NOT_FOUND","页面或接口不存在");
        } catch (Exception failure) {
            // A disconnected client must not trigger a second response.
        } finally {responseStatus.remove();ConsolePath.clear();if(!handedOff)closeConnection(socket);}
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
    @Override public void close() {
        open = false;
        try { server.close(); } catch (IOException ignored) {}
        if(internal!=null)try{internal.close();}catch(IOException ignored){}
        for(Socket current:connections)try { current.close(); } catch(IOException ignored) {}
        connections.clear();
        worker.shutdownNow();
        streamWorker.shutdownNow();
    }
}
