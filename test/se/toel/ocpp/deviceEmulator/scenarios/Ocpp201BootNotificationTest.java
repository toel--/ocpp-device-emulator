/*
 * End-to-end: a 2.0.1 device connects and sends BootNotification, accepted.
 */
package se.toel.ocpp.deviceEmulator.scenarios;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.DeviceFactory;
import se.toel.ocpp.deviceEmulator.device.DeviceIF;
import se.toel.ocpp.deviceEmulator.support.Reply;
import se.toel.ocpp.deviceEmulator.support.TestCentralSystem;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class Ocpp201BootNotificationTest {

    @Test
    public void test01_sendsBootNotificationAndIsAccepted() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18190);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject()
            .put("status", "Accepted").put("currentTime", "2026-06-25T00:00:00Z").put("interval", 300)));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP201_BOOT", "ws://localhost:18190/CP201_BOOT", "ocpp2.0.1");
        device.start();

        JSONArray boot = cs.awaitReceived("BootNotification", 10000);
        assertEquals(2, boot.getInt(0));
        assertNotNull(boot.getJSONObject(3));

        device.shutdown();
        cs.stop();
    }
}
