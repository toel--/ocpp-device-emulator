/*
 * End-to-end happy path for 2.0.1: boot accepted, authorized, full transaction.
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
public class Ocpp201TransactionTest {

    @Test
    public void test01_fullTransactionHappyPath() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18191);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status", "Accepted").put("currentTime", "2026-06-25T00:00:00Z").put("interval", 300)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("Heartbeat", c -> Reply.result(new JSONObject().put("currentTime", "2026-06-25T00:00:00Z")));
        cs.onCall("MeterValues", c -> Reply.result(new JSONObject()));
        cs.onCall("StartTransaction", c -> Reply.result(new JSONObject()
                .put("transactionId", 77)
                .put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StopTransaction", c -> Reply.result(new JSONObject()
                .put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP201_TX", "ws://localhost:18191/CP201_TX", "ocpp2.0.1");
        device.start();

        cs.awaitReceived("BootNotification", 10000);
        // 2.0.1 does not auto-Authorize after boot (unlike 1.6); just let the
        // device settle as connected + booted before driving the transaction.
        Thread.sleep(2000);

        assertTrue("StartTransaction should succeed", device.doStartTransaction(1, "TAG201"));
        JSONArray start = cs.awaitReceived("StartTransaction", 10000);
        assertEquals("TAG201", start.getJSONObject(3).getString("idTag"));

        assertEquals(77, device.getConnector(1).getTransactionId());

        device.doMeterValues(1);
        cs.awaitReceived("MeterValues", 10000);

        assertTrue("StopTransaction should succeed", device.doStopTransaction(77));
        cs.awaitReceived("StopTransaction", 10000);

        device.shutdown();
        cs.stop();
    }
}
