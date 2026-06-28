/*
 * Verifies the ocpp16 Configuration recognises its known keys and nothing else.
 */
package se.toel.ocpp.deviceEmulator.communication;

import org.junit.Test;
import se.toel.ocpp.deviceEmulator.communication.ocpp16.Configuration;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class Ocpp16ConfigurationTest {

    @Test
    public void knownKeysAreRecognised() {
        Configuration c = new Configuration();
        assertTrue(c.isKnownKey("MeterValueSampleInterval"));
        assertTrue(c.isKnownKey("MeterValuesSampledData"));
        assertTrue(c.isKnownKey("NumberOfConnectors"));
        // Core/profile keys added in the full OCPP 1.6 sweep
        assertTrue(c.isKnownKey("ConnectionTimeOut"));
        assertTrue(c.isKnownKey("HeartbeatInterval"));
        assertTrue(c.isKnownKey("ResetRetries"));
        assertTrue(c.isKnownKey("MaxChargingProfilesInstalled"));
        assertTrue("AuthorizationKey is conditionally supported but still recognised", c.isKnownKey("AuthorizationKey"));
    }

    @Test
    public void readOnlyKeysAreFlagged() {
        Configuration c = new Configuration();
        assertTrue(c.isReadOnly("NumberOfConnectors"));
        assertTrue(c.isReadOnly("ChargingScheduleAllowedChargingRateUnit"));
        assertTrue(c.isReadOnly("SupportedFeatureProfiles"));
        assertFalse("writable keys are not read-only", c.isReadOnly("MeterValueSampleInterval"));
        assertFalse(c.isReadOnly("MeterValuesSampledData"));
    }

    @Test
    public void meterValuesSampledDataMaxLengthIsReported() {
        Configuration c = new Configuration();
        assertTrue(c.isKnownKey("MeterValuesSampledDataMaxLength"));
        assertEquals("99", c.get("MeterValuesSampledDataMaxLength"));
    }

    @Test
    public void chargingScheduleAllowedChargingRateUnitIsReported() {
        Configuration c = new Configuration();
        assertTrue(c.isKnownKey("ChargingScheduleAllowedChargingRateUnit"));
        assertEquals("Current,Power", c.get("ChargingScheduleAllowedChargingRateUnit"));
    }

    @Test
    public void unknownKeysAreNotRecognised() {
        Configuration c = new Configuration();
        assertFalse(c.isKnownKey("FooBar"));
        assertFalse(c.isKnownKey("SoC"));
        assertFalse(c.isKnownKey(""));
    }
}
