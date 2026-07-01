/*
 * End-to-end: the central system uses TriggerMessage (RemoteTrigger) to ask the device to send a
 * message now; the device answers Accepted and sends it. An unknown message -> NotImplemented.
 */
package se.toel.ocpp.deviceEmulator.scenarios;

import org.json.JSONObject;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.DeviceFactory;
import se.toel.ocpp.deviceEmulator.device.DeviceIF;
import se.toel.ocpp.deviceEmulator.support.Reply;
import se.toel.ocpp.deviceEmulator.support.TestCentralSystem;
import static org.junit.Assert.*;

public class Ocpp16TriggerMessageTest {

    @Test
    public void test01_triggerHeartbeatIsAcceptedAndSent() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18202);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status", "Accepted").put("currentTime", "2026-07-01T00:00:00Z").put("interval", 3600)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("Heartbeat", c -> Reply.result(new JSONObject().put("currentTime", "2026-07-01T00:00:00Z")));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_TRIG", "ws://localhost:18202/CP_TRIG", "ocpp1.6");
        device.start();
        cs.awaitReceived("BootNotification", 10000);

        // Trigger a Heartbeat: accepted, and the device then sends one.
        String msgId = cs.sendCall("TriggerMessage", new JSONObject().put("requestedMessage", "Heartbeat"));
        assertEquals("Accepted", cs.awaitResult(msgId, 10000).getString("status"));
        cs.awaitReceived("Heartbeat", 10000);

        // Unknown requested message -> NotImplemented.
        String bogus = cs.sendCall("TriggerMessage", new JSONObject().put("requestedMessage", "Bogus"));
        assertEquals("NotImplemented", cs.awaitResult(bogus, 10000).getString("status"));

        device.shutdown();
        cs.stop();
    }
}
