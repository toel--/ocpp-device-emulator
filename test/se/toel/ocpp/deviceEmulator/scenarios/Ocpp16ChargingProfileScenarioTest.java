/*
 * End-to-end: the central system sets a ChargePointMaxProfile on a charging
 * device and the cap is accepted and actually throttles the connector.
 */
package se.toel.ocpp.deviceEmulator.scenarios;

import java.io.File;
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
public class Ocpp16ChargingProfileScenarioTest {

    @Test
    public void test01_chargePointMaxProfileThrottlesCharging() throws Exception {
        // Start from a clean slate: the connector persists its (possibly throttled) charging current.
        deleteRecursively(new File("data/CP_PROFILE"));

        TestCentralSystem cs = new TestCentralSystem(18185);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status", "Accepted").put("currentTime", "2026-06-27T00:00:00Z").put("interval", 300)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("Heartbeat", c -> Reply.result(new JSONObject().put("currentTime", "2026-06-27T00:00:00Z")));
        cs.onCall("MeterValues", c -> Reply.result(new JSONObject()));
        cs.onCall("StartTransaction", c -> Reply.result(new JSONObject()
                .put("transactionId", 7)
                .put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StopTransaction", c -> Reply.result(new JSONObject()
                .put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_PROFILE", "ws://localhost:18185/CP_PROFILE", "ocpp1.6");
        device.start();

        cs.awaitReceived("BootNotification", 10000);
        cs.awaitReceived("Authorize", 10000);

        // Get the connector charging at its default 16 A.
        assertTrue(device.doStartTransaction(1, "TAG7"));
        cs.awaitReceived("StartTransaction", 10000);
        assertEquals(16, device.getConnector(1).getChargingCurrent(), 0.01);

        // A 0 W charge-point cap must be accepted and stop the connector.
        String off = cs.sendCall("SetChargingProfile", setMaxProfile(1, "W", 0));
        assertEquals("Accepted", cs.awaitResult(off, 10000).getString("status"));
        assertEquals(0, device.getConnector(1).getChargingCurrent(), 0.01);
        assertEquals("SuspendedEVSE", device.getConnector(1).getStatus());

        // Raising the cap to 2300 W (== 10 A) resumes charging at 10 A.
        String low = cs.sendCall("SetChargingProfile", setMaxProfile(1, "W", 2300));
        assertEquals("Accepted", cs.awaitResult(low, 10000).getString("status"));
        assertEquals(10, device.getConnector(1).getChargingCurrent(), 0.01);
        assertEquals("Charging", device.getConnector(1).getStatus());

        // Clearing the charge-point cap restores the full 16 A.
        String clear = cs.sendCall("ClearChargingProfile", new JSONObject()
                .put("connectorId", 0)
                .put("chargingProfilePurpose", "ChargePointMaxProfile"));
        assertEquals("Accepted", cs.awaitResult(clear, 10000).getString("status"));
        assertEquals(16, device.getConnector(1).getChargingCurrent(), 0.01);

        device.shutdown();
        cs.stop();
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private JSONObject setMaxProfile(int profileId, String unit, double limit) {
        JSONObject period = new JSONObject().put("startPeriod", 0).put("limit", limit);
        JSONObject schedule = new JSONObject()
                .put("chargingRateUnit", unit)
                .put("chargingSchedulePeriod", new JSONArray().put(period));
        JSONObject profile = new JSONObject()
                .put("chargingProfileId", profileId)
                .put("stackLevel", 0)
                .put("chargingProfilePurpose", "ChargePointMaxProfile")
                .put("chargingProfileKind", "Absolute")
                .put("chargingSchedule", schedule);
        return new JSONObject().put("connectorId", 0).put("csChargingProfiles", profile);
    }
}
