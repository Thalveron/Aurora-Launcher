package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class ClockSyncTest {
    @Test public void detectsSystemClockJump(){assertFalse(ClockSync.jumped(1000,1000));assertFalse(ClockSync.jumped(1000,5900));assertTrue(ClockSync.jumped(1000,7000));assertTrue(ClockSync.jumped(1000,-86400000));}
    @Test public void retriesQuicklyThenEveryMinute(){assertEquals(5000,ClockSync.retryDelay(0));assertEquals(10000,ClockSync.retryDelay(1));assertEquals(40000,ClockSync.retryDelay(3));assertEquals(60000,ClockSync.retryDelay(4));assertEquals(60000,ClockSync.retryDelay(500));}
    @Test public void acceptsServerTimeEvenWhenTvClockIsDecadesOff(){assertTrue(ClockSync.plausible(1794800000000L));assertFalse(ClockSync.plausible(0));assertFalse(ClockSync.plausible(1577836800000L));assertFalse(ClockSync.plausible(4102444800000L));}
}
