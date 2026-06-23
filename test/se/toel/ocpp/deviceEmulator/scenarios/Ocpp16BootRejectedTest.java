/*
 * End-to-end negative: when the backend rejects BootNotification, the device
 * must not proceed to Authorize or open a transaction.
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
public class Ocpp16BootRejectedTest {

    @Test
    public void test01_bootRejected_doesNotAuthorizeOrTransact() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18181);
        cs.onCall("BootNotification", call ->
            Reply.result(new JSONObject().put("status", "Rejected").put("interval", 10)));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_REJ", "ws://localhost:18181/CP_REJ", "ocpp1.6");
        device.start();

        cs.awaitReceived("BootNotification", 10000);
        Thread.sleep(3000); // give the device time to (wrongly) proceed, if it would

        for (JSONArray f : cs.getReceived()) {
            if (f.getInt(0) == 2) {
                String action = f.getString(2);
                assertNotEquals("device proceeded to Authorize despite rejected boot", "Authorize", action);
                assertNotEquals("device opened a transaction despite rejected boot", "StartTransaction", action);
            }
        }

        device.shutdown();
        cs.stop();
    }
}
