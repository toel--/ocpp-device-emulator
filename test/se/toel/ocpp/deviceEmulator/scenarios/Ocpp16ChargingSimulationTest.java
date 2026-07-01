/*
 * End-to-end: the charging-simulation session over a real 1.6 device + TestCentralSystem.
 * Happy vehicle charges and stops; blocked vehicle's transaction is closed with DeAuthorized.
 */
package se.toel.ocpp.deviceEmulator.scenarios;

import java.util.function.DoubleSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.DeviceFactory;
import se.toel.ocpp.deviceEmulator.device.SimDeviceIF;
import se.toel.ocpp.deviceEmulator.modes.sim.ChargingSession;
import se.toel.ocpp.deviceEmulator.modes.sim.SimClock;
import se.toel.ocpp.deviceEmulator.modes.sim.SimDurations;
import se.toel.ocpp.deviceEmulator.modes.sim.Vehicle;
import se.toel.ocpp.deviceEmulator.support.Reply;
import se.toel.ocpp.deviceEmulator.support.TestCentralSystem;
import static org.junit.Assert.*;

public class Ocpp16ChargingSimulationTest {

    private static final class FakeClock implements SimClock {
        long t = 0;
        public long now() { return t; }
        public void sleep(long ms) { t += ms; }
    }

    private final DoubleSupplier rng = () -> 0.0;

    @Test
    public void test01_acceptedSessionChargesAndStops() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18200);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status","Accepted").put("currentTime","2026-06-29T00:00:00Z").put("interval",300)));
        cs.onCall("Authorize", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status","Accepted"))));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("MeterValues", c -> Reply.result(new JSONObject()));
        cs.onCall("StartTransaction", c -> Reply.result(new JSONObject().put("transactionId", 555).put("idTagInfo", new JSONObject().put("status","Accepted"))));
        cs.onCall("StopTransaction", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status","Accepted"))));
        cs.startAndWait();

        SimDeviceIF device = DeviceFactory.createSimDevice("CP_SIM", "ws://localhost:18200/CP_SIM");
        device.setChargePointIdentity("lokato.se", "vendor", "1.0.0");
        device.start();
        cs.awaitReceived("BootNotification", 10000);

        // Low-SoC big battery, tiny charge window -> ends on the timer after a couple of ticks.
        Vehicle v = new Vehicle("SE-EV-0001", 58, 11, 0.80, 0.20, 0.20);
        SimDurations d = new SimDurations(3000, 0, 1000);
        ChargingSession.Result r = new ChargingSession(device, 1, v, d, new FakeClock(), rng).run();

        assertEquals(ChargingSession.Result.COMPLETED_TIMER, r);
        JSONArray start = cs.awaitReceived("StartTransaction", 10000);
        assertEquals("SE-EV-0001", start.getJSONObject(3).getString("idTag"));
        JSONArray stop = cs.awaitReceived("StopTransaction", 10000);
        assertEquals(555, stop.getJSONObject(3).getInt("transactionId"));

        device.shutdown();
        cs.stop();
    }

    @Test
    public void test02_blockedVehicleIsClosedWithDeAuthorized() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18201);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject().put("status","Accepted").put("currentTime","2026-06-29T00:00:00Z").put("interval",300)));
        cs.onCall("StatusNotification", c -> Reply.result(new JSONObject()));
        cs.onCall("StartTransaction", c -> Reply.result(new JSONObject().put("transactionId", 777).put("idTagInfo", new JSONObject().put("status","Blocked"))));
        cs.onCall("StopTransaction", c -> Reply.result(new JSONObject().put("idTagInfo", new JSONObject().put("status","Accepted"))));
        cs.startAndWait();

        SimDeviceIF device = DeviceFactory.createSimDevice("CP_SIM2", "ws://localhost:18201/CP_SIM2");
        device.start();
        cs.awaitReceived("BootNotification", 10000);

        Vehicle v = new Vehicle("SE-EV-0002", 40, 22, 0.80, 0.50, 0.50);
        ChargingSession.Result r = new ChargingSession(device, 1, v, new SimDurations(3000, 0, 1000), new FakeClock(), rng).run();

        assertEquals(ChargingSession.Result.BLOCKED, r);
        JSONArray stop = cs.awaitReceived("StopTransaction", 10000);
        assertEquals(777, stop.getJSONObject(3).getInt("transactionId"));
        assertEquals("DeAuthorized", stop.getJSONObject(3).getString("reason"));

        device.shutdown();
        cs.stop();
    }
}
