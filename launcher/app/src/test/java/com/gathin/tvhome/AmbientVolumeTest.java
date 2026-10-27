package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;

public class AmbientVolumeTest {
    @Test public void stoppedOutputRestoresPreviousVolume() { assertEquals(25,AmbientVolume.afterStop(25,3,3)); }
    @Test public void lowerPreviousVolumeCanBeRestored() { assertEquals(2,AmbientVolume.afterStop(2,8,8)); }
    @Test public void desktopVolumeRestoredDespiteDeviceVolumeDifference() { assertEquals(25,AmbientVolume.afterStop(25,3,5)); }
    @Test public void noPreviousVolumeLeavesCurrentAlone() { assertEquals(3,AmbientVolume.afterStop(-1,-1,3)); }
}
