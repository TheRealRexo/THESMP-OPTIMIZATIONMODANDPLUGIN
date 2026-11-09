package com.rexo.bedwars.clienthelper;

/**
 * Ping equalization manager — REMOVED in v1.1.0.
 * This class is kept as a stub to avoid breaking imports.
 * Ping hiding did not work reliably and has been replaced by health display.
 */
@Deprecated
public class PingEqualizerManager {
    private static final PingEqualizerManager instance = new PingEqualizerManager();
    private volatile boolean active = false;

    public static PingEqualizerManager getInstance() {
        return instance;
    }

    public void init() {
        // Removed in v1.1.0
    }

    public int getReportedPing() {
        return 0;
    }

    public long applyInputCompensation() {
        return 0;
    }

    public boolean isActive() { return active; }
    public boolean isHidePing() { return false; }
}