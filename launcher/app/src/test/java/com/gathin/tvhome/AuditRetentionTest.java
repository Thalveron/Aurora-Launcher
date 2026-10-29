package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class AuditRetentionTest {
 @Test public void allDurationsAndForever(){long now=1800000000000L;for(int days:new int[]{1,3,7,15,30})assertEquals(now-days*86400000L,AuditRetention.cutoff(days,now));assertEquals(Long.MIN_VALUE,AuditRetention.cutoff(0,now));}
 @Test public void rejectUnexpectedRetention(){for(int days:new int[]{-1,2,31,Integer.MAX_VALUE})try{AuditRetention.days(days);fail();}catch(IllegalArgumentException expected){}}
 @Test public void voiceGainPreservesOriginalAndBoostsThreeTimes(){assertEquals(0,VoiceGain.millibels(0));assertEquals(0,VoiceGain.millibels(1));assertEquals(602,VoiceGain.millibels(2));assertEquals(954,VoiceGain.millibels(3));for(double value:new double[]{-1,3.1,Double.NaN,Double.POSITIVE_INFINITY})try{VoiceGain.millibels(value);fail();}catch(IllegalArgumentException expected){}}
}
