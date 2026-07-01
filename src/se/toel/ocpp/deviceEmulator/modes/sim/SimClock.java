/*
 * Injectable time source so sessions run in real time in production and instantly in tests.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

public interface SimClock {
    long now();
    void sleep(long ms) throws InterruptedException;
}
