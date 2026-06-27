/*
 * End-to-end negative for 2.0.1: the device must survive CALLERROR, malformed
 * text, and a mid-session socket drop without hanging or crashing.
 */
package se.toel.ocpp.deviceEmulator.scenarios;

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
public class Ocpp201BackendFaultsTest {

    @Test
    public void test01_callErrorOnBoot_shutsDownClean() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18192);
        cs.onCall("BootNotification", c -> Reply.error("InternalError", "boom"));
        cs.startAndWait();
        DeviceIF device = DeviceFactory.create("CP201_ERR", "ws://localhost:18192/CP201_ERR", "ocpp2.0.1");
        device.start();
        cs.awaitReceived("BootNotification", 10000);
        assertShutsDownWithin(device, 10000);
        cs.stop();
    }

    @Test
    public void test02_malformedResponse_isSurvived() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18193);
        cs.onCall("BootNotification", c -> Reply.malformed("not-json-at-all"));
        cs.startAndWait();
        DeviceIF device = DeviceFactory.create("CP201_BAD", "ws://localhost:18193/CP201_BAD", "ocpp2.0.1");
        device.start();
        cs.awaitReceived("BootNotification", 10000);
        assertShutsDownWithin(device, 12000);
        cs.stop();
    }

    @Test
    public void test03_backendDropsSocket_isHandled() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18194);
        cs.onCall("BootNotification", c -> Reply.drop());
        cs.startAndWait();
        DeviceIF device = DeviceFactory.create("CP201_DROP", "ws://localhost:18194/CP201_DROP", "ocpp2.0.1");
        device.start();
        cs.awaitReceived("BootNotification", 10000);
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
