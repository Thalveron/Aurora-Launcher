package com.gathin.tvhome;

final class AmbientVolume {
    // The TCL output retains roughly half a second beyond AudioTrack.release().
    static final long OUTPUT_SETTLE_MS = 1200;
    /** Called only after our audio output is released; always return to the desktop volume saved at entry. */
    static int afterStop(int prior, int applied, int current) {
        return prior >= 0 ? prior : current;
    }
}
