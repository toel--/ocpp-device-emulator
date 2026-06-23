/*
 * Verifies Device exposes the version-agnostic DeviceIF the modes drive.
 */
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.ocpp16.Device;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class DeviceIFTest {

    @Test
    public void test01_deviceImplementsDeviceIF() {
        assertTrue("Device should implement DeviceIF",
                DeviceIF.class.isAssignableFrom(Device.class));
    }

    @Test
    public void test02_hasExpectedMethods() throws Exception {
        assertEquals(void.class, DeviceIF.class.getMethod("start").getReturnType());
        assertEquals(void.class, DeviceIF.class.getMethod("shutdown").getReturnType());
        assertEquals(boolean.class, DeviceIF.class.getMethod("doStartTransaction", int.class, String.class).getReturnType());
        assertEquals(boolean.class, DeviceIF.class.getMethod("doStopTransaction", int.class).getReturnType());
        assertEquals(void.class, DeviceIF.class.getMethod("doMeterValues", int.class).getReturnType());
        assertEquals(ConnectorIF.class, DeviceIF.class.getMethod("getConnector", int.class).getReturnType());
    }
}
