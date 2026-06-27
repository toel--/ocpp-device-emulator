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
        assertTrue("AuthorizationKey is conditionally supported but still recognised", c.isKnownKey("AuthorizationKey"));
    }

    @Test
    public void unknownKeysAreNotRecognised() {
        Configuration c = new Configuration();
        assertFalse(c.isKnownKey("FooBar"));
        assertFalse(c.isKnownKey("SoC"));
        assertFalse(c.isKnownKey(""));
    }
}
