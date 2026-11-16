package com.gathin.tvhome;

/** Pure rules for looping a track without replaying the silence recorded at its start and end. */
final class LoopTiming {
    /** Decoded level below which the recording counts as silent (about -48 dBFS). */
    static final double SILENCE=0.004;
    /** Silence must last this long before the loop takes over, so short rests in the music are kept. */
    static final long HOLD_MS=800;
    private LoopTiming(){}
    /** Only the last part of a track may end early: 15 s at most, a quarter of a short clip. */
    static long tailWindow(long durationMs){return Math.max(0,Math.min(15000,durationMs/4));}
    static boolean tailSilent(long remainingMs,long windowMs,long quietSince,long now){return quietSince>0&&remainingMs<=windowMs&&now-quietSince>=HOLD_MS;}
    /** Start position for later loops; anything under 0.3 s is not worth a seek. */
    static long head(long positionMs){return positionMs<300?0:Math.min(positionMs,20000);}
}
