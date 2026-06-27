/*
 * End-to-end: ChangeConfiguration answers NotSupported for an unknown key and
 * Accepted for a known one.
 */
package se.toel.ocpp.deviceEmulator.scenarios;

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
public class Ocpp16ChangeConfigurationTest {

    @Test
    public void test01_unknownKeyNotSupportedKnownKeyAccepted() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18187);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status", "Accepted").put("currentTime", "2026-06-27T00:00:00Z").put("interval", 300)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("Heartbeat", c -> Reply.result(new JSONObject().put("currentTime", "2026-06-27T00:00:00Z")));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_CFG", "ws://localhost:18187/CP_CFG", "ocpp1.6");
        device.start();
        cs.awaitReceived("BootNotification", 10000);
        cs.awaitReceived("Authorize", 10000);

        String unknown = cs.sendCall("ChangeConfiguration", new JSONObject().put("key", "FooBar").put("value", "1"));
        assertEquals("NotSupported", cs.awaitResult(unknown, 10000).getString("status"));

        String known = cs.sendCall("ChangeConfiguration", new JSONObject().put("key", "MeterValueSampleInterval").put("value", "10"));
        assertEquals("Accepted", cs.awaitResult(known, 10000).getString("status"));

        device.shutdown();
        cs.stop();
    }
}
