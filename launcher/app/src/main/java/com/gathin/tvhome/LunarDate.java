package com.gathin.tvhome;

import android.icu.util.ChineseCalendar;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;

final class LunarDate {
    static String today() {
        ChineseCalendar c = new ChineseCalendar(TimeZone.getTimeZone("Asia/Shanghai"));
        String[] months={"正","二","三","四","五","六","七","八","九","十","冬","腊"};
        int day=c.get(Calendar.DAY_OF_MONTH);
        String[] digits={"一","二","三","四","五","六","七","八","九","十"};
        String name=day<=10?"初"+digits[day-1]:day<20?"十"+digits[day-11]:day==20?"二十":day<30?"廿"+digits[day-21]:"三十";
        String festival=ChineseFestivals.today();
        return "农历"+(c.get(Calendar.IS_LEAP_MONTH)==1?"闰":"")+months[c.get(Calendar.MONTH)]+"月"+name+(festival.isEmpty()?"":" · "+festival);
    }
}
