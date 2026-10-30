package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Isolated opt-in theme container. The existing native Home remains the recovery path. */
public class AuroraActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(DesktopLanguage.wrap(DesktopViewport.wrap(base)));}

    static volatile boolean visible;
    private static java.lang.ref.WeakReference<AuroraActivity> current=new java.lang.ref.WeakReference<>(null);
    private WebView web;private FrameLayout saverLayer;private PersonalBackground saverBackground;
    private TextView loading;
    private final Handler main=new Handler(Looper.getMainLooper());
    private long deadline;
    private boolean ready,closed,triedPackaged,preparingDefaults;
    private String theme;private boolean saverOnly;
    private final Runnable check=new Runnable(){public void run(){
        if(closed||ready)return;
        if(android.os.SystemClock.elapsedRealtime()>deadline){fallback("主题没有及时准备好，已返回原生桌面");return;}
        web.evaluateJavascript("document.documentElement.dataset.auroraReady === 'true'",value->{
            if(closed)return;
            if("true".equals(value)){ready=true;UpdateHealth.uiReady(AuroraActivity.this);if(saverOnly){try{saverError="";AuroraSaver.enter(AuroraActivity.this);}catch(Exception error){saverError=String.valueOf(error);android.widget.Toast.makeText(AuroraActivity.this,error.getMessage(),android.widget.Toast.LENGTH_LONG).show();finish();}main.postDelayed(()->loading.setVisibility(android.view.View.GONE),250);return;}nativeSaver(AuroraSaver.active);loading.setVisibility(android.view.View.GONE);
                new Thread(()->{try{org.json.JSONObject config=AuroraThemes.config(AuroraActivity.this);config.put("lastGood",theme);if(triedPackaged){config.put("theme",AuroraThemes.FOUNDATION);config.put("forcePackaged",true);}AuroraThemes.save(AuroraActivity.this,config);}catch(Exception ignored){}},"aurora-ready").start();}
            else main.postDelayed(this,250);
        });
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(BundledDefaults.showLoading(this)){preparingDefaults=true;return;}UpdateHealth.begin(this,getIntent());saverOnly=this instanceof AuroraSaverActivity;if(!saverOnly&&AuroraThemes.current(this).equals("legacy")){AuroraThemes.open(this);finish();return;}current=new java.lang.ref.WeakReference<>(this);getWindow().setFlags(1024,1024);UploadService.ensureStarted(this);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(0xff101419);setContentView(frame);
        try{
            saverLayer=new FrameLayout(this);saverLayer.setBackgroundColor(0xff08121b);android.view.View sky=new android.view.View(this);sky.setId(R.id.sky);saverLayer.addView(sky,new FrameLayout.LayoutParams(-1,-1));frame.addView(saverLayer,new FrameLayout.LayoutParams(-1,-1));saverLayer.setVisibility(android.view.View.GONE);web=new WebView(this);web.setBackgroundColor(android.graphics.Color.TRANSPARENT);frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
            loading=new TextView(this);loading.setText(saverOnly?"正在进入屏保…":"正在启动 Aurora Launcher…");loading.setTextColor(-1);loading.setTextSize(24);loading.setGravity(17);loading.setBackgroundColor(0xff101419);frame.addView(loading,new FrameLayout.LayoutParams(-1,-1));
            android.webkit.WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);if(saverOnly){settings.setTextZoom(100);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);}
            settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setSupportMultipleWindows(false);
            if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)WebView.setWebContentsDebuggingEnabled(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
            CookieManager.getInstance().setCookie("http://127.0.0.1:8899/","aurora_session="+AuroraHttp.themeSession+"; Path=/; HttpOnly");
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !trusted(request.getUrl());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    if(!trusted(request.getUrl()))return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
                    String path=request.getUrl().getPath();
                    if(triedPackaged&&path!=null&&(path.startsWith("/themes/")||path.startsWith("/core/")))try{
                        String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".json")?"application/json":"text/html";
                        return new WebResourceResponse(mime,"UTF-8",new java.io.ByteArrayInputStream(AuroraThemes.packaged(AuroraActivity.this,path.substring(1))));
                    }catch(Exception error){return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}
                    return null;

                }
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())fallback("主题加载失败，已返回原生桌面");}
                @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){triedPackaged=true;fallback("网页渲染器已退出，已返回原生桌面");return true;}
            });
            triedPackaged=AuroraThemes.config(this).optBoolean("forcePackaged",false);theme=AuroraThemes.current(this);AuroraBgm.theme(this,theme);deadline=android.os.SystemClock.elapsedRealtime()+15000;
            main.postDelayed(()->{if(!closed)web.loadUrl("http://127.0.0.1:8899/themes/"+(saverOnly?"saver":theme)+"/index.html");},300);
            main.postDelayed(check,500);
        }catch(Exception failure){fallback("WebView不可用，已返回原生桌面");}
    }
    private boolean homeFocusPending;
    private void focusHome(){
        homeFocusPending=true;
        if(!ready||web==null||!hasWindowFocus())return;
        if(!web.hasFocus())web.requestFocus();
        main.postDelayed(()->{if(!closed&&homeFocusPending&&hasWindowFocus()){
            web.evaluateJavascript("requestAnimationFrame(function(){if(window.AuroraFocus)AuroraFocus.first();});",null);
            homeFocusPending=false;
        }},80);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus&&homeFocusPending)focusHome();}
    static boolean focusHomeInPlace(){
        AuroraActivity a=current.get();if(a==null||!visible||a.saverOnly||!a.ready||!java.util.Objects.equals(a.theme,AuroraThemes.current(a)))return false;
        a.main.post(()->{if(AuroraSaver.active)AuroraSaver.exit(a);a.focusHome();});return true;
    }
    static void finishSaverHost(){AuroraActivity a=current.get();if(a!=null&&a.saverOnly)a.main.post(()->{if(!a.isFinishing())a.finish();});}
    static void nativeSaver(boolean enabled){AuroraActivity a=current.get();if(a==null)return;a.main.post(()->{if(a.closed||a.web==null)return;boolean desktop=!AuroraBackgrounds.files(a,false).isEmpty();if(a.saverBackground==null){a.saverBackground=new PersonalBackground(a,a.saverLayer);a.saverBackground.useExternalDim();}a.saverBackground.setAmbient(enabled);a.saverLayer.setVisibility(enabled||desktop?android.view.View.VISIBLE:android.view.View.GONE);if(visible&&(enabled||desktop))a.saverBackground.resume();else a.saverBackground.pause();a.web.evaluateJavascript("document.body.dataset.nativeDesktop='"+desktop+"';document.body.dataset.nativeSaver='"+enabled+"';window.dispatchEvent(new Event('aurora-native-saver'));",null);});}
    static org.json.JSONObject diagnostics()throws Exception{
        AuroraActivity activity=current.get();if(activity==null||(activity.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new UnsupportedOperationException("仅限开发验收版");
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] result={null};
        activity.main.post(()->activity.web.evaluateJavascript("(function(){var w=document.getElementById(document.body.classList.contains('saver-active')?'saver-wallpaper':'wallpaper'),v=w&&w.querySelector('video'),s=w&&getComputedStyle(w);var g=document.getElementById('saver-greeting'),gs=g&&getComputedStyle(g),si=document.querySelector('.saver-info'),sp=document.getElementById('saver-spectrum');return {appColumns:(function(){var a=document.getElementById('apps');return a&&getComputedStyle(a).gridTemplateColumns;})(),visibleApps:Array.prototype.map.call(document.querySelectorAll('#apps .app'),function(b){return b.dataset.focusId;}),focused:document.activeElement&&document.activeElement.dataset.focusId,mediaButtons:Array.prototype.map.call(document.querySelectorAll(\'.controls button\'),function(b){return {id:b.id,disabled:b.disabled};}),pageStartedAt:performance.timing.navigationStart,greetingStyle:gs?{font:gs.fontFamily,weight:gs.fontWeight,left:gs.left,top:gs.top,size:gs.fontSize}:null,clockFont:document.body.dataset.saverClockFont,clockStyle:(function(){var c=document.getElementById(\'saver-clock\'),s=c&&getComputedStyle(c);return s?{font:s.fontFamily,size:s.fontSize,color:s.color,loaded:document.fonts.check(s.fontSize+\' \"\'+s.fontFamily.split(\',\')[0].replace(/\"/g,\'\')+\'\"\')}:null;})(),infoMode:document.body.dataset.saverInfo,infoDisplay:si&&getComputedStyle(si).display,spectrumBox:sp?{width:sp.clientWidth,height:sp.clientHeight}:null,greeting:(document.getElementById(\'saver-greeting\')||{}).textContent,spectrum:(document.getElementById(\'saver-spectrum\')||{}).dataset&&document.getElementById(\'saver-spectrum\').dataset.spec,ready:document.documentElement.dataset.auroraReady,body:document.body.className,nativeDesktop:document.body.dataset.nativeDesktop,nativeSaver:document.body.dataset.nativeSaver,wall:w?{width:w.clientWidth,height:w.clientHeight,children:w.children.length,z:s.zIndex,error:w.dataset.error}:null,video:v?{ready:v.readyState,paused:v.paused,position:v.currentTime,playError:v.dataset.playError,width:v.videoWidth,height:v.videoHeight,error:v.error&&v.error.code,src:v.currentSrc}:null,css:Array.prototype.map.call(document.styleSheets,function(s){return s.href})};})()",value->{result[0]=value;latch.countDown();}));
        if(!latch.await(2,java.util.concurrent.TimeUnit.SECONDS)||result[0]==null){org.json.JSONArray stack=new org.json.JSONArray();for(StackTraceElement e:android.os.Looper.getMainLooper().getThread().getStackTrace())stack.put(e.toString());return new org.json.JSONObject().put("ready","false").put("mainStack",stack);}return new org.json.JSONObject(result[0]).put("idleMillis",idleMillis()).put("saverActive",AuroraSaver.active).put("saverError",saverError);
    }
    private boolean trusted(android.net.Uri uri){return "http".equals(uri.getScheme())&&"127.0.0.1".equals(uri.getHost())&&uri.getPort()==8899;}
    private void fallback(String message){
        if(closed)return;
        if(saverOnly){android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show();finish();return;}
        if(!triedPackaged&&web!=null){triedPackaged=true;theme=AuroraThemes.FOUNDATION;ready=false;
            deadline=android.os.SystemClock.elapsedRealtime()+10000;
            web.loadUrl("http://127.0.0.1:8899/themes/foundation/index.html?packaged=1");main.removeCallbacks(check);main.postDelayed(check,500);return;}
        closed=true;main.removeCallbacksAndMessages(null);android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show();
        startActivity(new Intent(this,MainActivity.class).putExtra("aurora_recovery",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);if(preparingDefaults){setIntent(intent);return;}UpdateHealth.begin(this,intent);if(ready)UpdateHealth.uiReady(this);if(IdleStandby.home(this))return;setIntent(intent);if(!java.util.Objects.equals(theme,AuroraThemes.current(this)))recreate();else focusHome();}
    @Override protected void onResume(){super.onResume();if(preparingDefaults)return;visible=true;if(!saverOnly){lastUserInput=android.os.SystemClock.elapsedRealtime();}current=new java.lang.ref.WeakReference<>(this);if(!saverOnly)AuroraPlayback.warm(this);if(ready){nativeSaver(AuroraSaver.active);UpdateHealth.uiReady(this);}AuroraBgm.foreground(this,true);if(web!=null){web.onResume();web.evaluateJavascript("window.dispatchEvent(new Event('aurora-visible'))",null);}}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(preparingDefaults)return true;if(IdleStandby.key(this,event))return true;if(event.getAction()==android.view.KeyEvent.ACTION_DOWN)lastRemoteInput=android.os.SystemClock.elapsedRealtime();if(AuroraSaver.active){AuroraSaver.exit(this);if(web!=null)web.evaluateJavascript("window.dispatchEvent(new Event('aurora-saver-exit'))",null);consumeUntil=android.os.SystemClock.elapsedRealtime()+350;return true;}if(android.os.SystemClock.elapsedRealtime()<consumeUntil)return true;return super.dispatchKeyEvent(event);}
    static volatile long lastRemoteInput;private static volatile long lastUserInput=android.os.SystemClock.elapsedRealtime();
    static void panelResumed(){lastUserInput=android.os.SystemClock.elapsedRealtime();}
    static void recordPanelInput(){lastUserInput=lastRemoteInput=android.os.SystemClock.elapsedRealtime();}
    static String saverError="";static long idleMillis(){return android.os.SystemClock.elapsedRealtime()-lastUserInput;}
    @Override public void onUserInteraction(){super.onUserInteraction();lastUserInput=android.os.SystemClock.elapsedRealtime();if(web!=null)web.evaluateJavascript("window.dispatchEvent(new Event('aurora-user-input'))",null);}
    private long consumeUntil;
    @Override protected void onPause(){if(preparingDefaults){super.onPause();return;}if(!saverOnly)AuroraPlayback.cool();if(saverOnly&&AuroraSaver.active)AuroraSaver.exit(this);visible=false;if(saverBackground!=null)saverBackground.pause();AuroraBgm.foreground(this,false);if(web!=null){web.evaluateJavascript("window.dispatchEvent(new Event('aurora-hidden'))",null);web.onPause();}super.onPause();}
    @Override protected void onDestroy(){closed=true;main.removeCallbacksAndMessages(null);if(saverBackground!=null){saverBackground.close();saverBackground=null;}if(web!=null){((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();}super.onDestroy();}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("(function(){var overlay=!!document.querySelector('.queue-overlay');document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));if(!overlay&&window.AuroraFocus)AuroraFocus.first();})()",null);}
}
