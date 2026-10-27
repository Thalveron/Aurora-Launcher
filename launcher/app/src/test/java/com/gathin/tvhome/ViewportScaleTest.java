package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class ViewportScaleTest {
 @Test public void equivalentLayoutAcrossOutputSizes(){assertEquals(160,ViewportScale.densityDpi(1280,720));assertEquals(240,ViewportScale.densityDpi(1920,1080));assertEquals(480,ViewportScale.densityDpi(3840,2160));}
 @Test public void wideScreensFitByHeight(){assertEquals(240,ViewportScale.densityDpi(2560,1080));}
 @Test(expected=IllegalArgumentException.class) public void invalidSizeIsRejected(){ViewportScale.densityDpi(0,1080);}
}
