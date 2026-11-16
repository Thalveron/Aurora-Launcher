package com.gathin.tvhome;

/** Pure clock-sync rules, kept apart from Android so they can be unit tested. */
final class ClockSync {
    /** Time without a clock read after which the desktop is treated as resumed. */
    static final long IDLE_GAP_MS=60000;
    private ClockSync(){}
    /** Wall clock minus uptime stays constant while the system clock runs normally; a change means it was set or stopped. */
    static boolean jumped(long skewAtSync,long skewNow){return Math.abs(skewNow-skewAtSync)>5000;}
    /** 5 s, 10 s, 20 s, 40 s, then every minute: the network is often not ready right after boot. */
    static long retryDelay(int failures){return failures>=4?60000:5000L<<Math.max(0,failures);}
    /** Accepts any server time from 2024 to 2099, however wrong the TV clock is. */
    static boolean plausible(long epochMs){return epochMs>=1704067200000L&&epochMs<4102444800000L;}
}
