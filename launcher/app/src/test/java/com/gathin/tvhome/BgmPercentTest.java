package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class BgmPercentTest {
    @Test public void convertsDeviceStepsWithoutChangingRelativeGain(){
        assertEquals(20,BgmPercent.fromIndex(3,15));
        assertEquals(20,BgmPercent.fromIndex(20,100));
        assertEquals(87,BgmPercent.fromIndex(13,15));
    }
    @Test public void retainsSilenceAndMaximum(){
        assertEquals(0,BgmPercent.fromIndex(0,15));
        assertEquals(100,BgmPercent.fromIndex(15,15));
        assertEquals(0f,BgmPercent.gain(0),0f);
        assertEquals(1f,BgmPercent.gain(100),0f);
    }
    @Test public void boundsCorruptOrUnavailableLegacyValues(){
        assertEquals(0,BgmPercent.fromIndex(-10,15));
        assertEquals(100,BgmPercent.fromIndex(80,15));
        assertEquals(5,BgmPercent.fromIndex(5,0));
        assertEquals(1f,BgmPercent.gain(999),0f);
    }
    @Test public void supportsFineOnePercentStepsAcrossDevices(){
        assertEquals(.01f,BgmPercent.gain(1),.00001f);
        assertEquals(.02f,BgmPercent.gain(2),.00001f);
        assertEquals(.53f,BgmPercent.gain(53),.00001f);
    }
}
