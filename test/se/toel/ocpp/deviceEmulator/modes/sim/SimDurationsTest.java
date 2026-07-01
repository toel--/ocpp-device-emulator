/*
 * Verifies the production charge/pause/tick durations.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

import org.junit.Test;
import static org.junit.Assert.*;

public class SimDurationsTest {

    @Test
    public void productionDurationsAreTenAndFiveMinutes() {
        SimDurations d = SimDurations.production();
        assertEquals(600000L, d.chargeMs());
        assertEquals(300000L, d.pauseMs());
        assertEquals(1000L, d.tickMs());
    }
}
