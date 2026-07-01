/*
 * Verifies the pure CC-CV charging curve and SoC/amps helpers.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleTest {

    // 40 kWh, 22 kW, taper from 0.80
    private Vehicle b() {
        return new Vehicle("SE-EV-0002", 40, 22, 0.80, 0.970, 0.978);
    }

    @Test
    public void constantPowerBelowTaper() {
        assertEquals(22, b().desiredPowerKW(0.50), 0.001);
        assertEquals(22, b().desiredPowerKW(0.799), 0.001);
    }

    @Test
    public void linearTaperToFloorAcrossCvBand() {
        Vehicle v = b();
        // at taper start: full power
        assertEquals(22, v.desiredPowerKW(0.80), 0.001);
        // just below the full threshold: approaches the non-zero floor (at/above 0.98 it is full -> 0)
        assertEquals(Vehicle.MIN_CV_POWER_KW, v.desiredPowerKW(0.9799), 0.05);
        // halfway through the CV band (0.89): midpoint power
        assertEquals((22 + Vehicle.MIN_CV_POWER_KW) / 2, v.desiredPowerKW(0.89), 0.05);
    }

    @Test
    public void zeroAtOrAboveFull() {
        assertEquals(0, b().desiredPowerKW(0.98), 0.001);
        assertEquals(0, b().desiredPowerKW(1.0), 0.001);
        assertTrue(b().isFull(0.98));
        assertFalse(b().isFull(0.97));
    }

    @Test
    public void powerToAmpsIsThreePhaseSum() {
        // 22 kW / 230 V = 95.65 A summed
        assertEquals(95.65, Vehicle.powerKWToAmps(22), 0.05);
    }

    @Test
    public void randomStartSocStaysInRange() {
        Vehicle v = b();
        assertEquals(0.970, v.randomStartSoc(() -> 0.0), 1e-9);
        assertEquals(0.978, v.randomStartSoc(() -> 1.0), 1e-9);
        assertEquals(0.974, v.randomStartSoc(() -> 0.5), 1e-9);
    }
}
