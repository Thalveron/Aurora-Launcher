package com.gathin.tvhome;
final class TimeCapability {
 static boolean rootClockCapability(String status){try{for(String line:status.split("\n"))if(line.startsWith("CapEff:"))return new java.math.BigInteger(line.substring(7).trim(),16).testBit(25);}catch(Exception ignored){}return false;}
}
