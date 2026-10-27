package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class SystemControls {
    private final Context context;
    private final List<SystemControl> backends;
    SystemControls(Context context) {
        this.context=context.getApplicationContext();
        backends=Arrays.asList(new AndroidBackend(),new AdbLoopbackBackend(),new PrivilegedBackend(),new AccessibilityBackend());
    }
    SystemControl select(String capability) throws IOException {
        String preferred="auto";
        try {JSONObject values=AuroraThemes.config(context).optJSONObject("system_backends");if(values!=null)preferred=values.optString(capability,"auto");}catch(Exception ignored){}
        for (SystemControl backend:backends) if ((preferred.equals("auto")||preferred.equals(backend.name()))&&backend.supports(capability)) return backend;
        throw new IOException("此设备当前没有可用的"+capability+"后端，请查看系统能力状态");
    }
    JSONObject capabilities() throws Exception {
        JSONObject result=new JSONObject();
        for(String capability:new String[]{"standby","screenshot","key","launch"}) {
            JSONArray candidates=new JSONArray(); String chosen="";
            JSONObject preferences=AuroraThemes.config(context).optJSONObject("system_backends");
            String preferred=preferences==null?"auto":preferences.optString(capability,"auto");
            for(SystemControl backend:backends) {
                boolean available=backend.supports(capability);
                candidates.put(new JSONObject().put("backend",backend.name()).put("available",available)
                        .put("reason",available?"":backend.reason(capability)));
                if(chosen.isEmpty()&&available&&(preferred.equals("auto")||preferred.equals(backend.name())))chosen=backend.name();
            }
            result.put(capability,new JSONObject().put("available",!chosen.isEmpty()).put("backend",chosen).put("preferred",preferred).put("candidates",candidates));
        }
        result.put("adb",TvAdb.status());
        try { android.content.pm.PackageInfo p=android.webkit.WebView.getCurrentWebViewPackage(); result.put("webview",p==null?"不可用":p.packageName+" "+p.versionName); }
        catch(RuntimeException unavailable) { result.put("webview","不可用"); }
        return result;
    }
    abstract class Backend implements SystemControl {
        public void launch(String pkg)throws Exception { WebApps.launch(context,pkg); }
        public void powerStandby()throws Exception { throw new IOException("不支持待机"); }
        public byte[] screenshot()throws Exception { throw new IOException("不支持截图"); }
        public void key(int code)throws Exception { throw new IOException("不支持按键"); }
    }
    final class AndroidBackend extends Backend {
        public String name(){return "AndroidAPI";}
        public boolean supports(String capability){return capability.equals("launch");}
        public String reason(String capability){return "此操作需要ADB、系统权限或辅助功能，普通Android接口不能执行";}
    }
    final class AdbLoopbackBackend extends Backend {
        public String name(){return "AdbLoopbackBackend";}
        public boolean supports(String capability){try{return TvAdb.status().optBoolean("connected");}catch(Exception e){return false;}}
        public String reason(String capability){return "本机ADB未连接或未授权；只在主动连接时申请授权";}
        public void powerStandby()throws Exception{TvAdb.shell(context,"input keyevent 223");}
        public byte[] screenshot()throws Exception{return TvAdb.screen(context);}
        public void key(int code)throws Exception{TvAdb.shell(context,"input keyevent "+code);}
    }
    final class PrivilegedBackend extends Backend {
        public String name(){return "PrivilegedBackend";}
        boolean permission(String value){return context.checkSelfPermission(value)==PackageManager.PERMISSION_GRANTED;}
        public boolean supports(String capability){
            if(capability.equals("launch"))return false;
            if(capability.equals("key"))return permission("android.permission.INJECT_EVENTS");
            if(capability.equals("standby"))return permission("android.permission.DEVICE_POWER");
            if(capability.equals("screenshot")&&permission("android.permission.READ_FRAME_BUFFER"))
                try{Class.forName("android.view.SurfaceControl").getDeclaredMethod("screenshot",int.class,int.class);return true;}catch(Exception e){return false;}
            return false;
        }
        public String reason(String capability){return "未获系统权限或此系统没有兼容接口；放入priv-app并不自动授权";}
        public void powerStandby()throws Exception{
            Object power=context.getSystemService(Context.POWER_SERVICE);
            power.getClass().getMethod("goToSleep",long.class).invoke(power,SystemClock.uptimeMillis());
        }
        public void key(int code)throws Exception{
            Class<?> type=Class.forName("android.hardware.input.InputManager");
            Object manager=type.getMethod("getInstance").invoke(null);
            Method inject=type.getMethod("injectInputEvent",android.view.InputEvent.class,int.class);
            long now=SystemClock.uptimeMillis();
            for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})
                if(!Boolean.TRUE.equals(inject.invoke(manager,new KeyEvent(now,now,action,code,0),0)))throw new IOException("系统拒绝发送按键");
        }
        public byte[] screenshot()throws Exception{
            Bitmap bitmap=(Bitmap)Class.forName("android.view.SurfaceControl").getDeclaredMethod("screenshot",int.class,int.class).invoke(null,960,540);
            if(bitmap==null)throw new IOException("系统未返回画面");
            ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);bitmap.recycle();return out.toByteArray();
        }
    }
    final class AccessibilityBackend extends Backend {
        public String name(){return "AccessibilityBackend";}
        public boolean supports(String capability){return HomeKeyService.current!=null&&(capability.equals("key")||capability.equals("standby"));}
        public String reason(String capability){return capability.equals("screenshot")?"此兼容后端暂不提供截图":"辅助功能服务未连接";}
        public void powerStandby()throws Exception{
            HomeKeyService service=HomeKeyService.current;
            if(service==null||android.os.Build.VERSION.SDK_INT<28||!service.performGlobalAction(8))throw new IOException("辅助功能无法待机");
        }
        public void key(int code)throws Exception{
            HomeKeyService service=HomeKeyService.current;if(service==null)throw new IOException("辅助功能未连接");
            if(code==3||code==4){if(!service.performGlobalAction(code==3?2:1))throw new IOException("系统拒绝全局按键");return;}
            android.view.accessibility.AccessibilityNodeInfo root=service.getRootInActiveWindow();
            if(root==null)throw new IOException("当前页面没有可访问的控件");
            android.view.accessibility.AccessibilityNodeInfo focus=root.findFocus(1);
            if(focus==null)throw new IOException("当前应用没有可操作的辅助功能焦点，请使用ADB后端");
            if(code==23){if(!focus.performAction(16))throw new IOException("当前控件不能点击");return;}
            int direction=code==19?33:code==20?130:code==21?17:66;
            android.view.accessibility.AccessibilityNodeInfo next=focus.focusSearch(direction);
            if(next==null||!next.performAction(1))throw new IOException("当前应用不支持此方向的辅助功能导航");
        }
    }
}
