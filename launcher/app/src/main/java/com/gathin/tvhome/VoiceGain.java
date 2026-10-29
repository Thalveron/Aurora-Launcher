package com.gathin.tvhome;
final class VoiceGain {
 static int millibels(double volume){if(!Double.isFinite(volume)||volume<0||volume>3)throw new IllegalArgumentException("朗读音量需为0～3");return volume<=1?0:(int)Math.round(2000*Math.log10(volume));}
}
