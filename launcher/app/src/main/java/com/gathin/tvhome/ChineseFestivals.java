package com.gathin.tvhome;

import android.icu.util.ChineseCalendar;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Festival labels only; these are not official holiday or adjusted-workday schedules. */
final class ChineseFestivals {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    static String today(){return on(LocalDate.now(ZONE));}
    static String on(LocalDate date) {
        List<String> names=new ArrayList<>();
        switch(date.getMonthValue()*100+date.getDayOfMonth()) {
            case 101:names.add("元旦");break;
            case 308:names.add("妇女节");break;
            case 312:names.add("植树节");break;
            case 501:names.add("劳动节");break;
            case 504:names.add("青年节");break;
            case 601:names.add("儿童节");break;
            case 701:names.add("建党节");break;
            case 801:names.add("建军节");break;
            case 910:names.add("教师节");break;
            case 1001:names.add("国庆节");break;
        }
        ChineseCalendar lunar=lunar(date);
        if(lunar.get(Calendar.IS_LEAP_MONTH)==0) switch((lunar.get(Calendar.MONTH)+1)*100+lunar.get(Calendar.DAY_OF_MONTH)) {
            case 101:names.add("春节");break;
            case 115:names.add("元宵节");break;
            case 202:names.add("龙抬头");break;
            case 505:names.add("端午节");break;
            case 707:names.add("七夕");break;
            case 715:names.add("中元节");break;
            case 815:names.add("中秋节");break;
            case 909:names.add("重阳节");break;
            case 1208:names.add("腊八节");break;
        }
        // Lunar December can have 29 or 30 days; the day before New Year is always New Year's Eve.
        ChineseCalendar next=lunar(date.plusDays(1));
        if(next.get(Calendar.MONTH)==0 && next.get(Calendar.DAY_OF_MONTH)==1 && next.get(Calendar.IS_LEAP_MONTH)==0)names.add("除夕");
        if(date.equals(qingming(date.getYear())))names.add("清明节");
        return String.join(" · ",names);
    }
    private static ChineseCalendar lunar(LocalDate date) {
        ChineseCalendar c=new ChineseCalendar(TimeZone.getTimeZone("Asia/Shanghai"));
        c.setTimeInMillis(date.atTime(12,0).atZone(ZONE).toInstant().toEpochMilli());return c;
    }
    // Qingming is the apparent solar longitude crossing 15 degrees, converted to the local civil day.
    static LocalDate qingming(int year) {
        long lo=LocalDate.of(year,4,3).atStartOfDay(ZONE).toInstant().toEpochMilli();
        long hi=LocalDate.of(year,4,7).atStartOfDay(ZONE).toInstant().toEpochMilli();
        while(hi-lo>1000){long mid=lo+(hi-lo)/2;if(longitude(mid)<15)lo=mid;else hi=mid;}
        return java.time.Instant.ofEpochMilli(hi).atZone(ZONE).toLocalDate();
    }
    private static double longitude(long millis) {
        double t=(millis/86400000.0+2440587.5-2451545.0)/36525.0;
        double mean=280.46646+t*(36000.76983+0.0003032*t);
        double anomaly=Math.toRadians(357.52911+t*(35999.05029-0.0001537*t));
        double center=Math.sin(anomaly)*(1.914602-t*(0.004817+0.000014*t))
                +Math.sin(2*anomaly)*(0.019993-0.000101*t)+Math.sin(3*anomaly)*0.000289;
        double apparent=mean+center-0.00569-0.00478*Math.sin(Math.toRadians(125.04-1934.136*t));
        return (apparent%360+360)%360;
    }
}
