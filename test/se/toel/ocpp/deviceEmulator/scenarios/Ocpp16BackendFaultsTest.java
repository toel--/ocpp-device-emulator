/*
 * End-to-end negative: the device must survive hostile/abnormal backend
 * behaviour - CALLERROR, malformed text, and a mid-session socket drop -
 * without hanging or killing the process.
 */
package se.toel.ocpp.deviceEmulator.scenarios;

import org.json.JSONArray;
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
public class Ocpp16BackendFaultsTest {

    @Test
    public void test01_callErrorOnBoot_doesNotProceedAndShutsDownClean() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18183);
        cs.onCall("BootNotification", c -> Reply.error("InternalError", "boom"));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_ERR", "ws://localhost:18183/CP_ERR", "ocpp1.6");
        device.start();

        cs.awaitReceived("BootNotification", 10000);
        Thread.sleep(2000);
        // A CALLERROR boot is not an acceptance, so the device must not authorize.
        for (JSONArray f : cs.getReceived()) {
            if (f.getInt(0) == 2) assertNotEquals("Authorize", f.getString(2));
        }
        assertShutsDownWithin(device, 10000);
        cs.stop();
    }

    @Test
    public void test02_malformedResponse_isSurvived() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18184);
        cs.onCall("BootNotification", c -> Reply.malformed("not-json-at-all"));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_BAD", "ws://localhost:18184/CP_BAD", "ocpp1.6");
        device.start();

        cs.awaitReceived("BootNotification", 10000);
        Thread.sleep(1000);
        // The malformed reply must not crash or hang the device.
        assertShutsDownWithin(device, 12000);
        cs.stop();
    }

    @Test
    public void test03_backendDropsSocket_isHandled() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18185);
        cs.onCall("BootNotification", c -> Reply.drop());
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_DROP", "ws://localhost:18185/CP_DROP", "ocpp1.6");
        device.start();

        cs.awaitReceived("BootNotification", 10000);
        Thread.sleep(1000);
        // onClose must run and shutdown must not hang.
        assertShutsDownWithin(device, 10000);
        cs.stop();
    }

    private void assertShutsDownWithin(final DeviceIF device, long limitMs) throws InterruptedException {
        final boolean[] done = {false};
        Thread t = new Thread(() -> { device.shutdown(); done[0] = true; });
        t.start();
        t.join(limitMs);
        assertTrue("device.shutdown() did not complete within " + limitMs + "ms", done[0]);
    }
}
