package com.gathin.tvhome;
import android.content.Context;
import android.media.audiofx.Visualizer;
import org.json.*;
/** Real output FFT. Silence/permission failure returns zeros, never random animation. */
final class AuroraSpectrum {
    private static Visualizer visualizer;private static final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());private static long touched;private static String error="";
    private static final Runnable cleanup=()->{synchronized(AuroraSpectrum.class){if(android.os.SystemClock.elapsedRealtime()-touched>1800)release();}};
    static JSONObject read(Context c)throws Exception{
        // BGM marshals onto the main thread. Its cleanup callback takes this monitor,
        // so obtain PCM before locking to avoid a main-thread/HTTP-worker lock cycle.
        double[] decoded=AuroraSaver.active?AuroraBgm.spectrum(c):null;
        synchronized(AuroraSpectrum.class){
        touched=android.os.SystemClock.elapsedRealtime();main.removeCallbacks(cleanup);main.postDelayed(cleanup,2000);JSONArray values=new JSONArray();double[] bins=new double[32];
        if(decoded!=null){for(double v:decoded)values.put(v);return new JSONObject().put("bins",values).put("available",true).put("source","decoded_pcm").put("reason","");}
        boolean active=AuroraActivity.visible&&(AuroraSaver.active||AuroraData.media(c).optBoolean("playing"));
        if(active)try{if(visualizer==null){visualizer=new Visualizer(0);visualizer.setCaptureSize(Math.min(1024,Visualizer.getCaptureSizeRange()[1]));visualizer.setEnabled(true);}byte[] fft=new byte[visualizer.getCaptureSize()];if(visualizer.getFft(fft)==Visualizer.SUCCESS)for(int i=0;i<32;i++){int from=Math.max(1,(int)Math.pow(fft.length/2.0,i/32.0)),to=Math.max(from+1,(int)Math.pow(fft.length/2.0,(i+1)/32.0));double power=0;for(int k=from;k<Math.min(to,fft.length/2);k++)power+=fft[2*k]*fft[2*k]+fft[2*k+1]*fft[2*k+1];bins[i]=Math.min(1,Math.sqrt(power/Math.max(1,to-from))/70);}error="";}catch(Exception e){error="系统未提供输出频谱或未授权音频捕获";release();}
        else release();for(double v:bins)values.put(v);return new JSONObject().put("bins",values).put("available",active&&visualizer!=null).put("reason",error);
        }
    }
    private static void release(){if(visualizer!=null){try{visualizer.release();}catch(Exception ignored){}visualizer=null;}}
}
