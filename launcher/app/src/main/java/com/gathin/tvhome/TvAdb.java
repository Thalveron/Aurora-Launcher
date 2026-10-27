package com.gathin.tvhome;
import android.content.Context;import java.io.*;import java.net.*;import java.nio.*;import java.math.BigInteger;import java.security.*;import java.security.spec.*;import java.security.interfaces.RSAPublicKey;import java.util.*;
/** App-owned local debug connection. Android displays its normal authorization prompt for this key. */
final class TvAdb {
 private static final int CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43;
 private static KeyPair keys(Context c)throws Exception{File f=new File(c.getFilesDir(),"local-adb-key");KeyFactory k=KeyFactory.getInstance("RSA");if(f.isFile()){try(DataInputStream in=new DataInputStream(new FileInputStream(f))){int n=in.readInt();if(n<1||n>8192)throw new IOException();byte[] privateKey=new byte[n];in.readFully(privateKey);n=in.readInt();if(n<1||n>8192)throw new IOException();byte[] pub=new byte[n];in.readFully(pub);return new KeyPair(k.generatePublic(new X509EncodedKeySpec(pub)),k.generatePrivate(new PKCS8EncodedKeySpec(privateKey)));}}KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair pair=g.generateKeyPair();try(DataOutputStream out=new DataOutputStream(new FileOutputStream(f))){byte[] p=pair.getPrivate().getEncoded(),u=pair.getPublic().getEncoded();out.writeInt(p.length);out.write(p);out.writeInt(u.length);out.write(u);}return pair;}
 private static byte[] publicKey(KeyPair pair){RSAPublicKey key=(RSAPublicKey)pair.getPublic();BigInteger n=key.getModulus(),two=BigInteger.ONE.shiftLeft(32);ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);b.putInt(64).putInt(n.modInverse(two).negate().mod(two).intValue());little(b,n,256);little(b,BigInteger.ONE.shiftLeft(4096).mod(n),256);b.putInt(key.getPublicExponent().intValue());return (android.util.Base64.encodeToString(b.array(),android.util.Base64.NO_WRAP)+" tvhome@tv\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
 private static void little(ByteBuffer b,BigInteger n,int size){for(int i=0;i<size;i++)b.put(n.shiftRight(i*8).byteValue());}
 private static void send(OutputStream out,int command,int a,int b,byte[] data)throws IOException{int sum=0;for(byte x:data)sum+=x&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(command).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(command^(-1));out.write(h.array());out.write(data);out.flush();}
 private static class Packet{int command,a,b;byte[] data;Packet(DataInputStream in)throws IOException{byte[] h=new byte[24];in.readFully(h);ByteBuffer p=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);command=p.getInt();a=p.getInt();b=p.getInt();int n=p.getInt(),sum=p.getInt(),magic=p.getInt();if(n<0||n>16*1024*1024||magic!=(command^(-1)))throw new IOException("调试协议无效");data=new byte[n];in.readFully(data);}}
 private static final java.util.concurrent.locks.ReentrantLock connectionLock=new java.util.concurrent.locks.ReentrantLock();
 private static volatile Socket connection;
 private static DataInputStream connectionIn;
 private static OutputStream connectionOut;
 private static int nextStream=1;
 private static boolean needsAuthorization;
 private static void disconnect() { if(connection!=null)try{connection.close();}catch(IOException ignored){} connection=null;connectionIn=null;connectionOut=null; }
 private static void connect(Context c,boolean allowPrompt)throws Exception {
  if(connection!=null&&!connection.isClosed())return;
  if(needsAuthorization&&!allowPrompt)throw new IOException("调试未连接，请在设备页面点击连接电视调试；自动重试已停止");
  Socket socket=new Socket();
  try {
   socket.connect(new InetSocketAddress("127.0.0.1",5555),1500);socket.setSoTimeout(allowPrompt?300000:2000);
   DataInputStream in=new DataInputStream(socket.getInputStream());OutputStream out=socket.getOutputStream();
   send(out,CNXN,0x01000000,4096,"host::\0".getBytes());KeyPair pair=null;boolean signed=false,published=false;
   while(true){Packet p=new Packet(in);if(p.command==CNXN)break;if(p.command!=AUTH)throw new IOException("电视调试连接未就绪");
    if(pair==null)pair=keys(c);
    if(!signed){Signature sig=Signature.getInstance("NONEwithRSA");sig.initSign(pair.getPrivate());sig.update(new byte[]{0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14});sig.update(p.data);send(out,AUTH,2,0,sig.sign());signed=true;}
    else if(allowPrompt&&!published){send(out,AUTH,3,0,publicKey(pair));published=true;}
    else {needsAuthorization=true;throw new IOException("请在设备页面点击连接电视调试，并在电视上确认；自动重试已停止");}
   }
   connection=socket;connectionIn=in;connectionOut=out;nextStream=1;needsAuthorization=false;
  }catch(Exception e){try{socket.close();}catch(IOException ignored){}needsAuthorization=true;throw e;}
 }
 private static volatile boolean authorizationPending;
 private static volatile String authorizationMessage="尚未连接电视调试";
 static org.json.JSONObject status()throws org.json.JSONException {
  boolean connected=connection!=null&&!connection.isClosed();
  return new org.json.JSONObject().put("connected",connected).put("pending",authorizationPending)
   .put("message",connected?"已连接，电视调试授权成功":authorizationMessage);
 }
 static synchronized org.json.JSONObject authorize(Context c)throws Exception {
  if(!authorizationPending&&(connection==null||connection.isClosed())) {
   authorizationPending=true;authorizationMessage="等待电视确认，请在电视弹窗勾选一律允许后按确定";
   Context app=c.getApplicationContext();
   new Thread(() -> {
    connectionLock.lock();
    try{connect(app,true);authorizationMessage="已连接，电视调试授权成功";}
    catch(Exception e){authorizationMessage=e instanceof SocketTimeoutException?"授权等待已结束，请准备好遥控器后再次点击连接":String.valueOf(e.getMessage());}
    finally{authorizationPending=false;connectionLock.unlock();}
   },"tv-adb-authorize").start();
  }
  return status();
 }
 static byte[] run(Context c,String service,int max)throws Exception {
  boolean capture=service.equals("exec:screencap -p");
  if(authorizationPending)throw new IOException("正在等待电视确认，请在电视弹窗中按确定");
  boolean locked=capture?connectionLock.tryLock(2500,java.util.concurrent.TimeUnit.MILLISECONDS):connectionLock.tryLock();
  if(!locked)throw new IOException(authorizationPending?"正在等待电视确认，请在电视弹窗中按确定":"电视操作处理中，请稍后重试");
  try {
   connect(c,false);connection.setSoTimeout(service.contains("install")?120000:15000);
   int local=nextStream++;send(connectionOut,OPEN,local,0,(service+"\0").getBytes("UTF-8"));
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();long end=System.currentTimeMillis()+120000;
   while(System.currentTimeMillis()<end){Packet p=new Packet(connectionIn);if(p.b!=local)continue;
    if(p.command==WRTE){if(bytes.size()+p.data.length>max)throw new IOException("电视返回内容过大");bytes.write(p.data);send(connectionOut,OKAY,local,p.a,new byte[0]);}
    else if(p.command==CLSE){send(connectionOut,CLSE,local,p.a,new byte[0]);return bytes.toByteArray();}
   }throw new IOException("电视操作超时");
  }catch(Exception e){disconnect();needsAuthorization=true;authorizationMessage=String.valueOf(e.getMessage());throw e;}finally{connectionLock.unlock();}
 }
 static String shell(Context c,String command)throws Exception{return new String(run(c,"shell:"+command,1024*1024),"UTF-8").trim();}
 static byte[] screenPng(Context c)throws Exception{byte[] data=run(c,"exec:screencap -p",32*1024*1024);for(int i=0;i+8<=data.length;i++)if(data[i]==(byte)137&&data[i+1]==80&&data[i+2]==78&&data[i+3]==71)return java.util.Arrays.copyOfRange(data,i,data.length);throw new IOException("电视未返回画面");}
 static byte[] screen(Context c)throws Exception{byte[] png=run(c,"exec:screencap -p",12*1024*1024);int start=-1;for(int i=0;i+8<=png.length;i++)if(png[i]==(byte)137&&png[i+1]==80&&png[i+2]==78&&png[i+3]==71){start=i;break;}if(start<0)throw new IOException("电视未返回画面");android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeByteArray(png,start,png.length-start);if(b==null)throw new IOException("画面读取失败");android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(b,960,540,true);ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(android.graphics.Bitmap.CompressFormat.JPEG,75,out);if(small!=b)small.recycle();b.recycle();return out.toByteArray();}
}
