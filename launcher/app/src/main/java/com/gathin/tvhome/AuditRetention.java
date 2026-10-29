package com.gathin.tvhome;
final class AuditRetention {
 static int days(int value){for(int valid:new int[]{0,1,3,7,15,30})if(value==valid)return value;throw new IllegalArgumentException("日志保存期限仅支持1、3、7、15、30天或永久");}
 static long cutoff(int days,long now){return days(days)==0?Long.MIN_VALUE:now-days*86400000L;}
}
