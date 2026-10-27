package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.net.*;import java.util.*;
public class UpdatePolicyTest{
 @Test public void onlyPublisherApksAndHttpsTransports(){
  String original="https://github.com/Thalveron/Aurora-Launcher/releases/download/v0.1.1/Aurora-Launcher-v0.1.1.apk";
  assertEquals(original,UpdatePolicy.downloadSources(original)[0]);assertEquals(UpdatePolicy.PROXY+original,UpdatePolicy.downloadSources(original)[1]);
  for(String bad:new String[]{"http://github.com/Thalveron/Aurora-Launcher/releases/download/v1/a.apk","https://github.com/other/repo/releases/download/v1/a.apk",original+"?token=x",original+"#a",original.replace("github.com","github.com.evil.test"),original.replace("/v0.1.1/","/%2e%2e/")})reject(()->UpdatePolicy.validateArtifact(bad));
  assertTrue(UpdatePolicy.transport("https://release-assets.githubusercontent.com/github-production-release-asset/x"));
  assertFalse(UpdatePolicy.transport("http://127.0.0.1/a"));assertFalse(UpdatePolicy.transport("https://user@github.com/a"));assertFalse(UpdatePolicy.transport("https://evil.test/a"));
 }
 @Test public void digestsAndVersionsAreStrict()throws Exception{
  UpdatePolicy.version("0.1.1");UpdatePolicy.version("0.1.1-beta.1");reject(()->UpdatePolicy.version("v1"));reject(()->UpdatePolicy.digest("abc"));
  File f=File.createTempFile("aurora-update",".tmp");try{try(FileOutputStream out=new FileOutputStream(f)){out.write("abc".getBytes("UTF-8"));}assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",UpdatePolicy.sha(f));}finally{f.delete();}
 }
 @Test public void cleanupConfirmationCannotChangeOwnerOrPurpose(){
  AuroraRules.Confirmations tokens=new AuroraRules.Confirmations();String owner="client|updates-clean|session-a",token=tokens.issue(owner,1000);
  assertFalse(tokens.consume(token,"client|standby|session-a",1001));assertFalse(tokens.consume(token,"client|updates-clean|session-b",1002));assertTrue(tokens.consume(token,owner,1003));assertFalse(tokens.consume(token,owner,1004));
 }
 @Test public void loopbackTransferRequiresTokenAndRegisteredPath()throws Exception{
  File file=File.createTempFile("aurora-transfer",".tmp");try{try(FileOutputStream out=new FileOutputStream(file)){out.write("private-backup".getBytes("UTF-8"));}Map<String,File> files=new HashMap<>();files.put("/backup",file);
   try(UpdateTransfer transfer=new UpdateTransfer(files)){
    assertEquals(403,request(transfer,"/backup","wrong").getResponseCode());
    assertEquals(403,request(transfer,"/../backup",transfer.token).getResponseCode());
    HttpURLConnection c=request(transfer,"/backup",transfer.token);try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] a=new byte[32];int n;while((n=in.read(a))!=-1)b.write(a,0,n);assertEquals("private-backup",b.toString("UTF-8"));}finally{c.disconnect();}
   }
  }finally{file.delete();}
 }
 @Test public void parsesInstalledPackageVersion(){assertEquals(65,UpdatePolicy.installedCode("  versionCode=65 minSdk=26 targetSdk=34"));assertEquals(-1,UpdatePolicy.installedCode("package not found"));}
 private static HttpURLConnection request(UpdateTransfer transfer,String path,String token)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+transfer.port()+path).openConnection();c.setConnectTimeout(2000);c.setReadTimeout(2000);c.setRequestProperty("X-Aurora-Transfer",token);return c;}
 private static void reject(Runnable action){try{action.run();fail();}catch(IllegalArgumentException expected){}}
}
