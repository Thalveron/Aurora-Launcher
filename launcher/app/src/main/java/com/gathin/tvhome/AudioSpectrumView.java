package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.media.audiofx.Visualizer;
import android.os.*;
import android.view.View;

/** Real output FFT only. No microphone recording or simulated spectrum. */
final class AudioSpectrumView extends View {
    private static final int BARS=28;
    private final android.graphics.Path wavePath=new android.graphics.Path();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels=new float[BARS],targets=new float[BARS];
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Visualizer visualizer;
    private byte[] fft;
    private boolean active,playing,failed;
    private int frames,style,size=100,opacity=65,color1,color2,angle=315;private boolean sharedSaver;
    /** A caller-supplied spectrum (fullscreen player); overrides the saved desktop/saver colours. */
    private org.json.JSONObject customSpectrum;
    void setSharedSaver(){sharedSaver=true;updateColors();}
    /** True when the device refused the output capture (usually a missing RECORD_AUDIO grant). */
    boolean failed(){return failed;}
    AudioSpectrumView(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setPlaying(boolean value){
        playing=value;
        if(value){if(!active)start();}
        else{stop();}
        invalidate();
    }
    void start(){updateColors();if(active)return;active=true;failed=false;frames=0;handler.post(tick);}
    void stop(){active=false;handler.removeCallbacks(tick);release();java.util.Arrays.fill(levels,0);java.util.Arrays.fill(targets,0);invalidate();}
    private void release(){if(visualizer!=null){try{visualizer.release();}catch(RuntimeException ignored){}visualizer=null;}}
    private void capture(){
        if(!playing){java.util.Arrays.fill(targets,0);return;}
        if(visualizer==null&&!failed)try{
            visualizer=new Visualizer(0);int[] range=Visualizer.getCaptureSizeRange();int size=Math.min(1024,range[1]);
            visualizer.setCaptureSize(size);visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);visualizer.setEnabled(true);fft=new byte[size];
        }catch(RuntimeException e){failed=true;release();android.util.Log.w("TvHomeSpectrum","Output spectrum unavailable",e);}
        if(visualizer==null)return;
        try{
            if(visualizer.getFft(fft)!=Visualizer.SUCCESS)return;
            int bins=fft.length/2;
            for(int b=0;b<BARS;b++){
                int from=Math.max(1,(int)Math.pow(bins-1,b/(double)BARS));
                int to=Math.min(bins-1,Math.max(from+1,(int)Math.pow(bins-1,(b+1)/(double)BARS)));
                float sum=0;
                for(int k=from;k<to;k++){float re=fft[k*2],im=fft[k*2+1];sum+=re*re+im*im;}
                float magnitude=(float)Math.sqrt(sum/Math.max(1,to-from));
                targets[b]=Math.min(1,(float)(Math.log1p(magnitude)/Math.log(129)));
            }
        }catch(RuntimeException e){failed=true;release();}
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(!active)return;if(frames++%3==0)capture();
        for(int i=0;i<BARS;i++)levels[i]+=(targets[i]-levels[i])*(targets[i]>levels[i]?.65f:.18f);
        invalidate();handler.postDelayed(this,50);
    }};
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        updateColors();
    }
    private void updateColors(){
        if(customSpectrum!=null){applySpectrum(customSpectrum);return;}
        android.content.SharedPreferences p=DesktopAppearance.prefs(getContext());
        style=p.getInt("card_style",0);size=p.getInt("card_size",100);angle=p.getInt("card_angle",315);
        color1=p.getInt("card_color",p.getInt("spectrum",0xFF69E7FF));color2=p.getInt("card_color2",0xFF6172FF);
        opacity=p.getInt("card_opacity",p.getInt("opacity",65));
        if(sharedSaver)try{
            org.json.JSONObject spec=AuroraSettings.stored(getContext()).getJSONObject("saverSpectrum");
            String[] styles={"bars","wave","mirror","dots","ring"};style=java.util.Arrays.asList(styles).indexOf(spec.getString("style"));
            color1=Color.parseColor(spec.getString("color").substring(0,7));color2=Color.parseColor(spec.getString("color2").substring(0,7));
            opacity=spec.getInt("opacity");size=spec.getInt("size");angle=spec.optInt("angle",315);
        }catch(Exception ignored){}
        applyGradient(null);
    }
    /** Same schema as the screensaver spectrum: style, color, color2, opacity, size, angle, stops. */
    void setSpectrum(org.json.JSONObject spec){customSpectrum=spec;updateColors();invalidate();}
    private void applySpectrum(org.json.JSONObject spec){
        String[] styles={"bars","wave","mirror","dots","ring"};
        style=Math.max(0,java.util.Arrays.asList(styles).indexOf(spec.optString("style","wave")));
        color1=parseColor(spec.optString("color","#55ddffff"),0xFF55DDFF);
        color2=parseColor(spec.optString("color2","#8266ffff"),0xFF8266FF);
        opacity=Math.max(0,Math.min(100,spec.optInt("opacity",50)));
        size=Math.max(30,Math.min(200,spec.optInt("size",100)));
        angle=Math.max(0,Math.min(360,spec.optInt("angle",315)));
        applyGradient(spec.optJSONArray("stops"));
    }
    private static int parseColor(String value,int fallback){
        try{String hex=value==null?"":value.trim();if(hex.length()==9)hex=hex.substring(0,7);return Color.parseColor(hex);}catch(Exception invalid){return fallback;}
    }
    private void applyGradient(org.json.JSONArray stops){
        double radians=angle*Math.PI/180;float dx=(float)Math.cos(radians)*Math.max(1,getWidth())/2,dy=(float)Math.sin(radians)*Math.max(1,getHeight())/2;
        java.util.TreeMap<Float,Integer> points=new java.util.TreeMap<>();points.put(0f,color1);points.put(1f,color2);
        if(stops!=null)for(int i=0;i<stops.length();i++)try{
            org.json.JSONObject stop=stops.getJSONObject(i);
            points.put(Math.max(0f,Math.min(1f,(float)stop.optDouble("position",50)/100f)),parseColor(stop.optString("color",""),color1));
        }catch(Exception ignored){}
        int[] colors=new int[points.size()];float[] positions=new float[points.size()];int index=0;
        for(java.util.Map.Entry<Float,Integer> entry:points.entrySet()){positions[index]=entry.getKey();colors[index]=entry.getValue();index++;}
        paint.setShader(new LinearGradient(getWidth()/2f-dx,getHeight()/2f-dy,getWidth()/2f+dx,getHeight()/2f+dy,colors,positions,Shader.TileMode.CLAMP));
        paint.setAlpha(Math.round(255*opacity/100f));
    }
    org.json.JSONObject diagnostics()throws org.json.JSONException{
        float peak=0;for(float level:levels)peak=Math.max(peak,level);
        return new org.json.JSONObject().put("style",new String[]{"bars","wave","mirror","dots","ring"}[Math.max(0,Math.min(4,style))])
            .put("size",size).put("opacity",opacity).put("angle",angle).put("color",String.format("#%06X",color1&0xffffff)).put("color2",String.format("#%06X",color2&0xffffff))
            .put("width",getWidth()).put("height",getHeight()).put("playing",playing).put("active",active).put("visualizer",visualizer!=null).put("peak",peak);
    }
    @Override protected void onDraw(Canvas c){
        if(!playing||opacity==0)return;
        float peak=0;for(float level:levels)peak=Math.max(peak,level);if(peak<.005f)return;
        float density=getResources().getDisplayMetrics().density,scale=size/100f;
        float width=Math.min(getWidth(),getWidth()*.9f*scale),height=Math.min(getHeight()*.94f,getHeight()*.55f*scale);
        float left=(getWidth()-width)/2,centerX=getWidth()/2f,centerY=getHeight()/2f,step=width/BARS;
        paint.clearShadowLayer();paint.setStrokeWidth(Math.max(1.5f*density,width/160));paint.setStyle(Paint.Style.FILL);
        if(style==1){
            wavePath.reset();
            for(int i=0;i<BARS;i++){float x=left+i*width/(BARS-1),y=centerY-(float)Math.sin(i*.6)*levels[i]*height*.48f;if(i==0)wavePath.moveTo(x,y);else wavePath.lineTo(x,y);}
            paint.setStyle(Paint.Style.STROKE);c.drawPath(wavePath,paint);
        }else if(style==4){
            float radius=Math.min(width,height)*.27f;
            for(int i=0;i<BARS;i++){double a=i*2*Math.PI/BARS;float length=levels[i]*height*.22f;if(length<density*.4f)continue;
                c.drawLine(centerX+(float)Math.cos(a)*radius,centerY+(float)Math.sin(a)*radius,centerX+(float)Math.cos(a)*(radius+length),centerY+(float)Math.sin(a)*(radius+length),paint);}
        }else for(int i=0;i<BARS;i++){
            float bar=levels[i]*height;if(bar<density*.4f)continue;
            float gap=Math.max(density,step*.28f),bottom=centerY+height/2;
            if(style==3){for(float y=bottom-density*2;y>bottom-bar;y-=density*6)c.drawCircle(left+i*step+step/2,y,Math.min(density*1.8f,step/3),paint);}
            else {float top=style==2?centerY-bar/2:bottom-bar,end=style==2?centerY+bar/2:bottom;
                c.drawRoundRect(left+i*step+gap/2,top,left+(i+1)*step-gap/2,end,density*1.5f,density*1.5f,paint);}
        }
        paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
