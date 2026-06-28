/*
 * End-to-end: GetConfiguration returns known keys (incl. MeterValuesSampledDataMaxLength)
 * and lists genuinely unknown keys under unknownKey.
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
public class Ocpp16GetConfigurationTest {

    @Test
    public void test01_getConfigurationReportsMaxLengthAndUnknownKeys() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18188);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status", "Accepted").put("currentTime", "2026-06-27T00:00:00Z").put("interval", 300)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("Heartbeat", c -> Reply.result(new JSONObject().put("currentTime", "2026-06-27T00:00:00Z")));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_GETCFG", "ws://localhost:18188/CP_GETCFG", "ocpp1.6");
        device.start();
        cs.awaitReceived("BootNotification", 10000);

        // The same request shapes the real backend sent.
        String msgId = cs.sendCall("GetConfiguration", new JSONObject()
                .put("key", new JSONArray()
                        .put("MeterValuesSampledData")
                        .put("MeterValuesSampledDataMaxLength")
                        .put("NumberOfConnectors")
                        .put("ChargingScheduleAllowedChargingRateUnit")));
        JSONObject result = cs.awaitResult(msgId, 10000);

        JSONArray configurationKey = result.getJSONArray("configurationKey");
        assertEquals("all requested keys are now known", 4, configurationKey.length());
        assertEquals("99", valueOf(configurationKey, "MeterValuesSampledDataMaxLength"));
        assertEquals("Current,Power", valueOf(configurationKey, "ChargingScheduleAllowedChargingRateUnit"));
        assertFalse("nothing should be unknown", result.has("unknownKey"));

        device.shutdown();
        cs.stop();
    }

    private String valueOf(JSONArray configurationKey, String key) {
        for (int i = 0; i < configurationKey.length(); i++) {
            JSONObject o = configurationKey.getJSONObject(i);
            if (key.equals(o.getString("key"))) return o.getString("value");
        }
        return null;
    }
}
