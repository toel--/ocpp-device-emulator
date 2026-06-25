/*
 * Verifies the ocpp201 Device satisfies the shared DeviceIF.
 */
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class Ocpp201DeviceTest {

    @Test
    public void test01_implementsDeviceIF() {
        assertTrue(DeviceIF.class.isAssignableFrom(
            se.toel.ocpp.deviceEmulator.device.ocpp201.Device.class));
    }
}
