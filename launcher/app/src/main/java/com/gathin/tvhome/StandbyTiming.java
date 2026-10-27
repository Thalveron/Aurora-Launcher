package com.gathin.tvhome;
/** Monotonic timing policy: the warning is the final minute of the chosen idle period. */
final class StandbyTiming {
 static int minutes(int value){if(value!=15&&value!=20&&value!=30)throw new IllegalArgumentException("待机时间请选择15、20或30分钟");return value;}
 static long delay(int minutes){return minutes(minutes)*60_000L;}
 static int seconds(long deadline,long now){return (int)Math.max(0,(deadline-now+999)/1000);}
 static boolean warning(long deadline,long now){return deadline-now<=60_000;}
 static String label(int seconds){return java.lang.String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
}
