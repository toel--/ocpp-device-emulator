/*
 * Production SimClock backed by the system clock.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

public final class SystemSimClock implements SimClock {

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public void sleep(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
