/*
 * Verifies the ocpp16 Connector clamps its charging current to the ceiling
 * handed down from the charge-point budget, and exposes its raw demand.
 */
package se.toel.ocpp.deviceEmulator.device;

import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import se.toel.ocpp.deviceEmulator.device.ocpp16.Connector;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Ocpp16ChargingProfileTest {

    private static final String STORE = System.getProperty("java.io.tmpdir") + "/ocpp16-cp-profile-test";

    @BeforeClass
    public static void prepareStore() {
        File dir = new File(STORE);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.mkdirs();
    }

    @Test
    public void test01_ceilingClampsActiveCharging() {
        Connector c = chargingConnector(1, 16);
        assertEquals(16, c.getChargingCurrent(), 0.01);

        c.setChargePointMaxCurrent(10);
        assertEquals("ceiling should pull the charging current down to 10 A", 10, c.getChargingCurrent(), 0.01);
        assertEquals("the raw demand is unchanged", 16, c.getRequestedCurrent(), 0.01);
        assertEquals(Connector.STATUS_CHARGING, c.getStatus());
    }

    @Test
    public void test02_zeroCeilingSuspendsCharging() {
        Connector c = chargingConnector(2, 16);

        c.setChargePointMaxCurrent(0);
        assertEquals(0, c.getChargingCurrent(), 0.01);
        assertEquals(Connector.STATUS_SUSPENDEDEVSE, c.getStatus());
        assertTrue("a throttled connector still demands power", c.isDemanding());
    }

    @Test
    public void test03_ceilingNeverRaisesAboveDemand() {
        Connector c = chargingConnector(3, 16);

        c.setChargePointMaxCurrent(32);
        assertEquals("a ceiling above the demand must not raise the current", 16, c.getChargingCurrent(), 0.01);
    }

    @Test
    public void test04_removingCeilingRestoresDemand() {
        Connector c = chargingConnector(4, 16);
        c.setChargePointMaxCurrent(8);
        assertEquals(8, c.getChargingCurrent(), 0.01);

        c.setChargePointMaxCurrent(-1);
        assertEquals("removing the ceiling restores the requested current", 16, c.getChargingCurrent(), 0.01);
        assertEquals(Connector.STATUS_CHARGING, c.getStatus());
    }

    @Test
    public void test05_newTxProfileStaysClampedToCeiling() {
        Connector c = chargingConnector(5, 16);
        c.setChargePointMaxCurrent(10);

        // A fresh TxProfile asking for 16 A must still be capped to the 10 A ceiling.
        c.setChargingProfile(txProfile(16));
        assertEquals(10, c.getChargingCurrent(), 0.01);
        assertEquals("the raw demand reflects the new TxProfile", 16, c.getRequestedCurrent(), 0.01);
    }

    @Test
    public void test06_isDemandingReflectsStatus() {
        Connector c = new Connector(6, STORE);
        c.setStatus(Connector.STATUS_AVAILABLE);
        assertFalse(c.isDemanding());

        c.setStatus(Connector.STATUS_CHARGING);
        assertTrue(c.isDemanding());

        c.setStatus(Connector.STATUS_SUSPENDEDEVSE);
        assertTrue(c.isDemanding());
    }

    @Test
    public void test10_vehicleCurrentIsDeliveredWhenNoCap() {
        Connector c = new Connector(10, STORE);
        c.setStatus(Connector.STATUS_CHARGING);
        c.setVehicleCurrent(90);                       // ~20.7 kW summed
        assertEquals(90, c.getChargingCurrent(), 0.01);
    }

    @Test
    public void test11_chargePointMaxClampsVehicleCurrent() {
        Connector c = new Connector(11, STORE);
        c.setStatus(Connector.STATUS_CHARGING);
        c.setVehicleCurrent(90);
        c.setChargePointMaxCurrent(40);                // backend budget share caps it
        assertEquals(40, c.getChargingCurrent(), 0.01);
    }

    @Test
    public void test12_vehicleActiveDoesNotAutoFlipStatusWhenLow() {
        // With a vehicle active the session owns status: a low/zero current must NOT flip to SuspendedEVSE.
        Connector c = new Connector(12, STORE);
        c.setStatus(Connector.STATUS_CHARGING);
        c.setVehicleCurrent(3);                         // below the legacy <6 sum threshold
        assertEquals(3, c.getChargingCurrent(), 0.01);
        assertEquals("vehicle active: status untouched", Connector.STATUS_CHARGING, c.getStatus());
    }

    @Test
    public void test13_multiPeriodScheduleAdvancesOverTime() {
        Connector c = new Connector(13, STORE);
        c.setStatus(Connector.STATUS_CHARGING);
        c.setVehicleCurrent(90);                        // EV wants 90 A

        // TxProfile: 0 A for the first 60 s, then 20 A (no transaction -> reference is profile-apply time)
        JSONObject period0 = new JSONObject().put("startPeriod", 0).put("limit", 0);
        JSONObject period1 = new JSONObject().put("startPeriod", 60).put("limit", 20);
        JSONObject schedule = new JSONObject().put("chargingRateUnit", "A")
                .put("chargingSchedulePeriod", new JSONArray().put(period0).put(period1));
        JSONObject profile = new JSONObject().put("chargingProfileId", 1).put("stackLevel", 0)
                .put("chargingProfilePurpose", "TxProfile").put("chargingProfileKind", "Absolute")
                .put("chargingSchedule", schedule);
        c.setChargingProfile(profile);

        assertEquals("first period limits to 0 A", 0, c.getChargingCurrent(), 0.01);

        c.tickSchedule(System.currentTimeMillis() + 65000);   // 65 s later
        assertEquals("schedule advances to 20 A after 60 s", 20, c.getChargingCurrent(), 0.01);
    }

    @Test
    public void test14_scheduleUsesTransactionStartAsReference() {
        Connector c = new Connector(14, STORE);
        c.setStatus(Connector.STATUS_CHARGING);
        c.setVehicleCurrent(90);
        c.setTransactionId(1);                          // starts the schedule clock (transaction reference)

        // delayed start: 0 A for 60 s, then 20 A
        JSONObject period0 = new JSONObject().put("startPeriod", 0).put("limit", 0);
        JSONObject period1 = new JSONObject().put("startPeriod", 60).put("limit", 20);
        JSONObject schedule = new JSONObject().put("chargingRateUnit", "A")
                .put("chargingSchedulePeriod", new JSONArray().put(period0).put(period1));
        JSONObject profile = new JSONObject().put("chargingProfileId", 101).put("stackLevel", 0)
                .put("chargingProfilePurpose", "TxProfile").put("chargingProfileKind", "Absolute")
                .put("transactionId", 1)
                .put("chargingSchedule", schedule);
        c.setChargingProfile(profile);

        assertEquals("delayed start: 0 A in the first period", 0, c.getChargingCurrent(), 0.01);

        c.tickSchedule(System.currentTimeMillis() + 65000);    // 65 s into the transaction
        assertEquals("resumes to 20 A after 60 s", 20, c.getChargingCurrent(), 0.01);
    }

    /***************************************************************************
     * Helpers
     **************************************************************************/

    private Connector chargingConnector(int id, double amps) {
        Connector c = new Connector(id, STORE);
        c.setStatus(Connector.STATUS_CHARGING);
        c.setChargingProfile(txProfile(amps));
        return c;
    }

    private JSONObject txProfile(double amps) {
        JSONObject period = new JSONObject().put("startPeriod", 0).put("limit", amps);
        JSONObject schedule = new JSONObject()
                .put("chargingRateUnit", "A")
                .put("chargingSchedulePeriod", new JSONArray().put(period));
        return new JSONObject()
                .put("chargingProfileId", 1)
                .put("stackLevel", 0)
                .put("chargingProfilePurpose", "TxProfile")
                .put("chargingProfileKind", "Relative")
                .put("chargingSchedule", schedule);
    }
}
