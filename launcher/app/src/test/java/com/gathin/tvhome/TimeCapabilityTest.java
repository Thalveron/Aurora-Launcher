package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class TimeCapabilityTest {
 @Test public void requiresClockCapabilityNotJustUid(){assertTrue(TimeCapability.rootClockCapability("Name: cat\nCapEff:\t0000000002000000\n"));assertFalse(TimeCapability.rootClockCapability("CapEff:\t0000000000000000\n"));}
 @Test public void acceptsOtherCapabilitiesOnlyWhenClockBitPresent(){assertTrue(TimeCapability.rootClockCapability("CapEff: 0000003fffffffff"));assertFalse(TimeCapability.rootClockCapability("CapEff: 000000000000ffff"));}
 @Test public void malformedOrMissingCapabilityIsDenied(){assertFalse(TimeCapability.rootClockCapability("Uid: 0"));assertFalse(TimeCapability.rootClockCapability("CapEff: unknown"));}
}
