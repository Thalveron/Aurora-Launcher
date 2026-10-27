package com.gathin.tvhome;
import java.net.URI;import java.security.MessageDigest;import java.io.*;
/** Fixed publisher and explicit HTTPS mirrors; never a general-purpose URL installer. */
final class UpdatePolicy {
 static final String REPO="Thalveron/Aurora-Launcher",RAW="https://raw.githubusercontent.com/"+REPO+"/main/updates/latest.json",PROXY="https://gh-proxy.com/";
 static String[] manifestSources(){return new String[]{RAW,PROXY+RAW};}
 static String[] downloadSources(String original){validateArtifact(original);return new String[]{original,PROXY+original};}
 static void validateArtifact(String url){URI u=URI.create(url);if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1||u.getQuery()!=null||u.getFragment()!=null||!u.getRawPath().matches("/"+REPO+"/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk")||u.getRawPath().contains(".."))throw new IllegalArgumentException("更新包必须来自指定 GitHub 仓库");}
 static boolean transport(String url){try{URI u=URI.create(url);String h=u.getHost();return "https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getPort()==-1&&h!=null&&(h.equals("api.github.com")||h.equals("github.com")||h.equals("raw.githubusercontent.com")||h.equals("release-assets.githubusercontent.com")||h.equals("objects.githubusercontent.com")||h.equals("gh-proxy.com"));}catch(Exception e){return false;}}
 static String sha(File file)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
 static void digest(String value){if(value==null||!value.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("更新文件校验值无效");}
 static void version(String value){if(value==null||!value.matches("\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?"))throw new IllegalArgumentException("更新版本号无效");}
 static int installedCode(String text){java.util.regex.Matcher match=java.util.regex.Pattern.compile("versionCode=(\\d+)").matcher(text);return match.find()?Integer.parseInt(match.group(1)):-1;}
 static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
}
