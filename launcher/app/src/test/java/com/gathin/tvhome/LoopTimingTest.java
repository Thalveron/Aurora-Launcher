package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class LoopTimingTest {
    @Test public void tailWindowIsBoundedByTrackLength(){assertEquals(15000,LoopTiming.tailWindow(240000));assertEquals(11500,LoopTiming.tailWindow(46000));assertEquals(0,LoopTiming.tailWindow(-1));}
    @Test public void loopsEarlyOnlyAfterSustainedSilenceNearTheEnd(){
        assertTrue(LoopTiming.tailSilent(4000,15000,1000,1800));
        assertFalse("rest shorter than the hold time",LoopTiming.tailSilent(4000,15000,1000,1700));
        assertFalse("quiet passage in the middle of the track",LoopTiming.tailSilent(60000,15000,1000,9000));
        assertFalse("not silent",LoopTiming.tailSilent(4000,15000,0,9000));
    }
    @Test public void headSkipsOnlyMeaningfulLeadingSilence(){assertEquals(0,LoopTiming.head(120));assertEquals(2400,LoopTiming.head(2400));assertEquals(20000,LoopTiming.head(90000));}
}
