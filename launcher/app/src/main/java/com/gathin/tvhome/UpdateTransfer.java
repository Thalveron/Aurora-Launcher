package com.gathin.tvhome;
import java.net.*;import java.io.*;import java.util.*;import java.security.*;
/** Short-lived loopback transfer; private settings never staged in shared external storage. */
final class UpdateTransfer implements AutoCloseable {
 final String token=AuroraRules.token();private final ServerSocket server;private final Map<String,File> files;private volatile boolean open=true;
 UpdateTransfer(Map<String,File> source)throws Exception{files=new HashMap<>(source);server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));server.setSoTimeout(2000);Thread t=new Thread(this::serve,"aurora-update-transfer");t.setDaemon(true);t.start();}
 int port(){return server.getLocalPort();}
 private void serve(){long deadline=System.currentTimeMillis()+120000;while(open&&System.currentTimeMillis()<deadline)try(Socket s=server.accept()){
  s.setSoTimeout(10000);InputStream reader=s.getInputStream();String request=line(reader),auth="";int length=0;String line;while((line=line(reader))!=null&&!line.isEmpty()){length+=line.length();if(length>8192)throw new IOException("Header too large");if(line.toLowerCase(Locale.ROOT).startsWith("x-aurora-transfer:"))auth=line.substring(line.indexOf(':')+1).trim();}
  String[] parts=request==null?new String[0]:request.split(" ");File file=parts.length==3&&parts[0].equals("GET")?files.get(parts[1]):null;OutputStream out=s.getOutputStream();
  if(file==null||!MessageDigest.isEqual(token.getBytes("UTF-8"),auth.getBytes("UTF-8"))){out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));continue;}
  out.write(("HTTP/1.1 200 OK\r\nContent-Length: "+file.length()+"\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}out.flush();
 }catch(SocketTimeoutException ignored){}catch(Exception ignored){}close();}
 private static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1&&b!=10){if(out.size()>=4096)throw new IOException("Header too large");if(b!=13)out.write(b);}return out.toString("US-ASCII");}
 public void close(){open=false;try{server.close();}catch(Exception ignored){}}
}
