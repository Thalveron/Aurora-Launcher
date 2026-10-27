package com.gathin.tvhome;
import android.content.Context;
import java.util.*;
/** Eight-hour, process-scoped, HttpOnly browser sessions. Sessions are bound to the current path and password. */
final class AuroraLogin {
    private static final Map<String,Session> sessions=new HashMap<>();
    private static final class Session{final long end;final String binding;Session(long e,String b){end=e;binding=b;}}
    private static String binding(Context c){try{java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");return java.util.Base64.getEncoder().encodeToString(md.digest((BackgroundUploadServer.path(c)+"\n"+ConsoleSettings.port(c)+"\n"+UploadSecurity.username(c)+"\n"+UploadSecurity.password(c)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static synchronized String create(Context c){long now=android.os.SystemClock.elapsedRealtime();sessions.values().removeIf(v->v.end<now);if(sessions.size()>32)sessions.clear();String token=AuroraRules.token();sessions.put(token,new Session(now+8*3600000L,binding(c)));return token;}
    static synchronized boolean valid(Context c,String cookie){if(!UploadSecurity.required(c))return true;if(cookie!=null)for(String part:cookie.split(";"))if(part.trim().startsWith("aurora_web=")){Session s=sessions.get(part.trim().substring(11));if(s!=null&&s.end>android.os.SystemClock.elapsedRealtime()&&s.binding.equals(binding(c)))return true;}return false;}
    static synchronized void revokeAll(){sessions.clear();}
    static synchronized void logout(String cookie){if(cookie!=null)for(String part:cookie.split(";"))if(part.trim().startsWith("aurora_web="))sessions.remove(part.trim().substring(11));}
}
