/*
 * End-to-end: the central system configures MeterValuesSampledData. A list with
 * an unsupported measurand (SoC) is rejected; a fully-supported list is accepted.
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
public class Ocpp16MeterValuesConfigTest {

    @Test
    public void test01_rejectsUnsupportedMeasurandAcceptsSupported() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18186);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status", "Accepted").put("currentTime", "2026-06-27T00:00:00Z").put("interval", 300)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status", "Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("Heartbeat", c -> Reply.result(new JSONObject().put("currentTime", "2026-06-27T00:00:00Z")));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_MV", "ws://localhost:18186/CP_MV", "ocpp1.6");
        device.start();
        cs.awaitReceived("BootNotification", 10000);
        cs.awaitReceived("Authorize", 10000);

        // The backend's list in the field log includes SoC, which we cannot produce -> Rejected.
        String withSoc = cs.sendCall("ChangeConfiguration", new JSONObject()
                .put("key", "MeterValuesSampledData")
                .put("value", "Energy.Active.Import.Register,Power.Active.Import,Voltage,Current.Import,SoC"));
        assertEquals("Rejected", cs.awaitResult(withSoc, 10000).getString("status"));

        // The same list without SoC is fully supported -> Accepted.
        String supported = cs.sendCall("ChangeConfiguration", new JSONObject()
                .put("key", "MeterValuesSampledData")
                .put("value", "Energy.Active.Import.Register,Power.Active.Import,Voltage,Current.Import"));
        assertEquals("Accepted", cs.awaitResult(supported, 10000).getString("status"));

        device.shutdown();
        cs.stop();
    }
}
