package com.gathin.tvhome;

/** Device-independent PCM attenuation, with one-time conversion of the legacy slider. */
final class BgmPercent {
    static int clamp(int value){return Math.max(0,Math.min(100,value));}
    static int fromIndex(int index,int maximum){
        if(maximum<=0)return clamp(index);
        return clamp(Math.round(Math.max(0,index)*100f/maximum));
    }
    static float gain(int percent){return clamp(percent)/100f;}
}
