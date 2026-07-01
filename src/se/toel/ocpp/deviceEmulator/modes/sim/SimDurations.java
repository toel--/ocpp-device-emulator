/*
 * Charge / pause / tick durations for the charging simulation.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

public final class SimDurations {

    private final long chargeMs;
    private final long pauseMs;
    private final long tickMs;

    public SimDurations(long chargeMs, long pauseMs, long tickMs) {
        this.chargeMs = chargeMs;
        this.pauseMs = pauseMs;
        this.tickMs = tickMs;
    }

    public static SimDurations production() {
        return new SimDurations(10 * 60 * 1000L, 5 * 60 * 1000L, 1000L);
    }

    public long chargeMs() {
        return chargeMs;
    }

    public long pauseMs() {
        return pauseMs;
    }

    public long tickMs() {
        return tickMs;
    }
}
