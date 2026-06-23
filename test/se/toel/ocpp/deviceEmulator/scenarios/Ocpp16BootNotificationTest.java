/*
 * End-to-end: a 1.6 device connects to a backend and sends BootNotification,
 * which the backend accepts. Characterizes the existing connect -> boot flow.
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
public class Ocpp16BootNotificationTest {

    @Test
    public void test01_sendsBootNotificationAndIsAccepted() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18180);
        cs.onCall("BootNotification", call ->
            Reply.result(new JSONObject()
                .put("status", "Accepted")
                .put("currentTime", "2026-06-22T00:00:00Z")
                .put("interval", 300)));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_TEST_1", "ws://localhost:18180/CP_TEST_1", "ocpp1.6");
        device.start();

        // connect is scheduled ~100ms after construction; the per-second tick runs it,
        // and onOpen schedules BootNotification immediately -> arrives within a few ticks.
        JSONArray boot = cs.awaitReceived("BootNotification", 10000);
        assertEquals(2, boot.getInt(0));                    // CALL
        assertFalse(boot.getString(1).isEmpty());           // has a message id
        assertNotNull(boot.getJSONObject(3));               // has a payload object

        device.shutdown();
        cs.stop();
    }
}
