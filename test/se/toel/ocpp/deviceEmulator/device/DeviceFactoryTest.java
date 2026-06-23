/*
 * Verifies DeviceFactory selects the right implementation by OCPP version.
 */
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class DeviceFactoryTest {

    @Test
    public void test01_createsOcpp16Device() {
        DeviceIF d = DeviceFactory.create("CP1", "ws://localhost:1/x", "ocpp1.6");
        assertTrue(d instanceof se.toel.ocpp.deviceEmulator.device.ocpp16.Device);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test02_rejectsUnknownVersion() {
        DeviceFactory.create("CP1", "ws://localhost:1/x", "ocpp9.9");
    }
}
