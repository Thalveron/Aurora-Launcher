package com.gathin.tvhome;
/** Logical TV design space, fitted to runtime pixels; no device IDs or output-mode overrides. */
final class ViewportScale {
 static int densityDpi(int width,int height){if(width<=0||height<=0)throw new IllegalArgumentException("Invalid viewport");return Math.max(80,Math.round(160f*Math.min(width/1280f,height/720f)));}
}
