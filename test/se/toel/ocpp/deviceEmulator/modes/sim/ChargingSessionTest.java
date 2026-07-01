/*
 * Verifies the per-vehicle session state machine: accepted, blocked (DeAuthorized cleanup),
 * call-failed, ends-on-full, ends-on-timer. Driven with a fake device, virtual clock and fixed RNG.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.StartTransactionOutcome;
import static org.junit.Assert.*;

public class ChargingSessionTest {

    // virtual clock: sleep advances time, never blocks
    private static final class FakeClock implements SimClock {
        long t = 0;
        public long now() { return t; }
        public void sleep(long ms) { t += ms; }
    }

    private Vehicle vehicleStartingAt(double soc) {
        // taper 0.80, startSoc fixed via [soc,soc] range
        return new Vehicle("SE-EV-X", 40, 22, 0.80, soc, soc);
    }

    private SimDurations shortDurations() {
        return new SimDurations(10000, 5000, 1000);   // 10s charge, 5s pause, 1s tick
    }

    @Test
    public void blockedVehicleClosesTransactionWithDeAuthorized() throws Exception {
        FakeSimDevice dev = new FakeSimDevice();
        dev.startOutcome = new StartTransactionOutcome(true, "Blocked", 99);
        ChargingSession s = new ChargingSession(dev, 1, vehicleStartingAt(0.50), shortDurations(), new FakeClock(), () -> 0.0);

        assertEquals(ChargingSession.Result.BLOCKED, s.run());
        assertEquals("DeAuthorized", dev.lastStopReason);
        assertTrue(dev.events.contains("unplug"));
    }

    @Test
    public void callFailedSendsNoStopTransaction() throws Exception {
        FakeSimDevice dev = new FakeSimDevice();
        dev.startOutcome = StartTransactionOutcome.failed();
        ChargingSession s = new ChargingSession(dev, 1, vehicleStartingAt(0.50), shortDurations(), new FakeClock(), () -> 0.0);

        assertEquals(ChargingSession.Result.CALL_FAILED, s.run());
        assertNull("no transaction to close", dev.lastStopReason);
        assertTrue(dev.events.contains("unplug"));
    }

    @Test
    public void reachesFullThenSuspendedEvAndStopsLocal() throws Exception {
        FakeSimDevice dev = new FakeSimDevice();
        // 1st read = meterStart (0); 2nd read (loop 1) delivers enough to push 0.975 over 0.98 (40 kWh).
        dev.meterReadings.add(0.0);
        dev.meterReadings.add(40000 * (0.98 - 0.975) + 10);
        ChargingSession s = new ChargingSession(dev, 1, vehicleStartingAt(0.975), shortDurations(), new FakeClock(), () -> 0.0);

        assertEquals(ChargingSession.Result.COMPLETED_FULL, s.run());
        assertTrue(dev.events.contains("status:" + se.toel.ocpp.deviceEmulator.device.ocpp16.Connector.STATUS_SUSPENDEDEV));
        assertEquals("Local", dev.lastStopReason);
    }

    @Test
    public void zeroCapWhileWantingPowerReportsSuspendedEvse() throws Exception {
        FakeSimDevice dev = new FakeSimDevice();
        dev.meterWh = 0;                                 // SoC stays low (wants power), never full
        dev.effectiveOverride = 0.0;                     // backend cap forces delivered current to 0
        ChargingSession s = new ChargingSession(dev, 1, vehicleStartingAt(0.20), shortDurations(), new FakeClock(), () -> 0.0);

        assertEquals(ChargingSession.Result.COMPLETED_TIMER, s.run());
        assertTrue("a 0-cap while wanting power must report SuspendedEVSE",
                dev.events.contains("status:" + se.toel.ocpp.deviceEmulator.device.ocpp16.Connector.STATUS_SUSPENDEDEVSE));
    }

    @Test
    public void lowSocEndsOnTimer() throws Exception {
        FakeSimDevice dev = new FakeSimDevice();
        dev.meterWh = 0;                                 // SoC never advances
        ChargingSession s = new ChargingSession(dev, 1, vehicleStartingAt(0.20), shortDurations(), new FakeClock(), () -> 0.0);

        assertEquals(ChargingSession.Result.COMPLETED_TIMER, s.run());
        assertEquals("Local", dev.lastStopReason);
        assertNotNull("charging current was driven", dev.lastVehicleAmps);
    }
}
