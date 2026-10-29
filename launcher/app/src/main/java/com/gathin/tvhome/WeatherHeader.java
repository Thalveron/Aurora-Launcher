package com.gathin.tvhome;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.ZoneId;

/** Display-only header backed by the shared weather service and actual forecast dates. */
final class WeatherHeader {
    private static final long HOUR = 60L * 60 * 1000;
    private final Activity activity;
    private final TextView view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean active, closed;
    private final Runnable tick = this::refresh;
    WeatherHeader(Activity activity, TextView view) {
        this.activity=activity;this.view=view;
    }
    private boolean minimal;
    void setMinimal(boolean value){minimal=value;render(false);}
    void resume(){active=true;refresh();}
    void pause(){active=false;main.removeCallbacks(tick);}
    void close(){closed=true;pause();}
    private String cached() {
        try {
            JSONObject daily=AuroraServices.weather(activity).getJSONObject("daily");
            JSONArray days=daily.getJSONArray("time"),lo=daily.getJSONArray("temperature_2m_min"),hi=daily.getJSONArray("temperature_2m_max");
            LocalDate today=CityTime.date(AuroraServices.now(),LocationSettings.zone(activity));int first=-1,second=-1;
            for(int i=0;i<days.length();i++){if(days.getString(i).equals(today.toString()))first=i;if(days.getString(i).equals(today.plusDays(1).toString()))second=i;}
            if(first<0 || second<0)return null;
            return DesktopLanguage.t(activity,"今天 @")+daily.getJSONArray("weather_code").getInt(first)+"@ "+range(lo,hi,first)+DesktopLanguage.t(activity,"\n明天 @")+daily.getJSONArray("weather_code").getInt(second)+"@ "+range(lo,hi,second);
        }catch(Exception ignored){return null;}
    }
    private static String range(JSONArray lo,JSONArray hi,int index)throws Exception {
        double min=lo.getDouble(index),max=hi.getDouble(index);
        if(!Double.isFinite(min)||!Double.isFinite(max)||min>max)throw new IllegalArgumentException();
        return Math.round(min)+"～"+Math.round(max)+"°C";
    }
    private void render(boolean failure) {
        String text=cached();long age=System.currentTimeMillis()-activity.getSharedPreferences("aurora_service_state",0).getLong("weatherAt",0);
        String label=text==null?(failure?DesktopLanguage.city(activity)+DesktopLanguage.t(activity,"天气暂不可用 · 点此重试"):DesktopLanguage.city(activity)+DesktopLanguage.t(activity,"天气更新中…")):text+(failure||age>3*HOUR?DesktopLanguage.t(activity," · 待更新"):"");
        if(minimal&&label.contains("\n"))label=label.substring(0,label.indexOf("\n"));
        android.text.SpannableStringBuilder rich=new android.text.SpannableStringBuilder();
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("@(\\d+)@").matcher(label);int last=0;
        StringBuilder spoken=new StringBuilder();
        while(matcher.find()) {
            rich.append(label.substring(last,matcher.start()));spoken.append(label.substring(last,matcher.start()));
            int code=Integer.parseInt(matcher.group(1)),start=rich.length();rich.append("\uFFFC");
            int size=(int)(24*activity.getResources().getDisplayMetrics().density);
            WeatherIcon icon=new WeatherIcon(code);icon.setBounds(0,0,size,size);
            rich.setSpan(new android.text.style.ImageSpan(icon,android.text.style.ImageSpan.ALIGN_BASELINE),start,rich.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spoken.append(DesktopLanguage.t(activity,WeatherIcon.description(code)));last=matcher.end();
        }
        rich.append(label.substring(last));spoken.append(label.substring(last));view.setText(rich);view.setContentDescription(spoken.toString());
    }
    private void refresh() {
        if(closed||!active)return;
        main.removeCallbacks(tick);
        try{AuroraServices.weather(activity);render(false);}catch(Exception failure){render(true);}
        main.postDelayed(tick,2000);
    }
}
