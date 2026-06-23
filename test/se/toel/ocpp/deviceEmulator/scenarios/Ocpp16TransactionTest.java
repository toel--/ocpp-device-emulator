/*
 * End-to-end happy path: boot accepted, authorized, then a full transaction
 * (StartTransaction -> MeterValues -> StopTransaction) over the wire.
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
public class Ocpp16TransactionTest {

    @Test
    public void test01_fullTransactionHappyPath() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18182);
        // Answer every message the device sends so it never blocks on waitForAnswer.
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status", "Accepted").put("currentTime", "2026-06-22T00:00:00Z").put("interval", 300)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("Heartbeat", c -> Reply.result(new JSONObject().put("currentTime", "2026-06-22T00:00:00Z")));
        cs.onCall("MeterValues", c -> Reply.result(new JSONObject()));
        cs.onCall("StartTransaction", c -> Reply.result(new JSONObject()
                .put("transactionId", 42)
                .put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StopTransaction", c -> Reply.result(new JSONObject()
                .put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_TX", "ws://localhost:18182/CP_TX", "ocpp1.6");
        device.start();

        // Wait until booted+authorized (Authorize is only sent after boot is accepted).
        cs.awaitReceived("BootNotification", 10000);
        cs.awaitReceived("Authorize", 10000);

        // Drive the transaction (same calls DeviceTester makes).
        assertTrue("StartTransaction should succeed", device.doStartTransaction(1, "TAG123"));
        JSONArray start = cs.awaitReceived("StartTransaction", 10000);
        assertEquals("TAG123", start.getJSONObject(3).getString("idTag"));

        assertEquals("backend transactionId should be recorded on the connector",
                42, device.getConnector(1).getTransactionId());

        device.doMeterValues(1);
        cs.awaitReceived("MeterValues", 10000);

        assertTrue("StopTransaction should succeed", device.doStopTransaction(42));
        JSONArray stop = cs.awaitReceived("StopTransaction", 10000);
        assertEquals(42, stop.getJSONObject(3).getInt("transactionId"));

        device.shutdown();
        cs.stop();
    }
}
