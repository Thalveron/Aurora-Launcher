package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class StandbyTimingTest {
 @Test public void minuteIsPartOfTotalDelay(){assertEquals(900000,StandbyTiming.delay(15));assertEquals(1200000,StandbyTiming.delay(20));assertEquals(1800000,StandbyTiming.delay(30));assertFalse(StandbyTiming.warning(900000,839999));assertTrue(StandbyTiming.warning(900000,840000));}
 @Test public void secondBoundariesAndEnd(){assertEquals("01:00",StandbyTiming.label(StandbyTiming.seconds(60000,0)));assertEquals("00:59",StandbyTiming.label(StandbyTiming.seconds(60000,1000)));assertEquals(1,StandbyTiming.seconds(60000,59999));assertEquals(0,StandbyTiming.seconds(60000,60001));}
 @Test public void rejectUnsupportedDurations(){try{StandbyTiming.minutes(16);fail();}catch(IllegalArgumentException expected){}}
 @Test public void onlyCurrentExternalMediaCounts(){String header="  players:\n";String own=" ID:55 -- u/pid:10051/4451 -- state:started -- attr: usage=USAGE_MEDIA\n";String external=" ID:56 -- u/pid:10052/4452 -- state:started -- attr: usage=USAGE_MEDIA\n";assertFalse(StandbyPlayback.externalAudio(header+own+"  ducked players\n"+external,10051));assertTrue(StandbyPlayback.externalAudio(header+external+"  ducked players",10051));assertFalse(StandbyPlayback.externalAudio(header+external.replace("started","paused")+"  ducked players",10051));assertFalse(StandbyPlayback.externalAudio(header+external.replace("USAGE_MEDIA","USAGE_ASSISTANCE_SONIFICATION")+"  ducked players",10051));}
 @Test public void onlyLiveVideoResourcesAndFreshFramesCount(){
  String dump="  Processes:\n    Pid: 42\n      Client:\n        Resources:\n          non-secure-codec/video-codec:1\n    Pid: 43\n      Client:\n          non-secure-codec/audio-codec:1\n  Events logs:\n    Pid: 44\n       video-codec:1\n";
  assertEquals(java.util.Arrays.asList(42),StandbyPlayback.videoPids(dump));
  assertTrue(StandbyPlayback.recentFrames("16666666\n 9999000000 10000000000 9999000000\n",15000));
  assertFalse(StandbyPlayback.recentFrames("16666666\n 9999000000 10000000000 9999000000\n",21000));
  assertFalse(StandbyPlayback.recentFrames("16666666\n 0 9223372036854775807 0\n",15000));
  assertTrue(StandbyPlayback.videoLayer("com.player/Video#1","com.player"));
  assertFalse(StandbyPlayback.videoLayer("abc123 com.player/Video#0","com.player"));
  assertFalse(StandbyPlayback.videoLayer("AppWindowToken com.player/Video","com.player"));
 }
}
