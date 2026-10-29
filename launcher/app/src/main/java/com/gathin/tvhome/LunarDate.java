package com.gathin.tvhome;

import android.icu.util.ChineseCalendar;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;

final class LunarDate {
    static String today() {
        return on(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
    }
    static String today(android.content.Context context){return on(CityTime.date(AuroraServices.now(),LocationSettings.zone(context)));}
    static ChineseCalendar calendar(java.time.LocalDate date){ChineseCalendar c=new ChineseCalendar(TimeZone.getTimeZone("Asia/Shanghai"));c.setTimeInMillis(date.atTime(12,0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());return c;}
    static String on(java.time.LocalDate date) {
        ChineseCalendar c = calendar(date);
        String[] months={"正","二","三","四","五","六","七","八","九","十","冬","腊"};
        int day=c.get(Calendar.DAY_OF_MONTH);
        String[] digits={"一","二","三","四","五","六","七","八","九","十"};
        String name=day<=10?"初"+digits[day-1]:day<20?"十"+digits[day-11]:day==20?"二十":day<30?"廿"+digits[day-21]:"三十";
        String festival=ChineseFestivals.on(date);
        return "农历"+(c.get(Calendar.IS_LEAP_MONTH)==1?"闰":"")+months[c.get(Calendar.MONTH)]+"月"+name+(festival.isEmpty()?"":" · "+festival);
    }
}
