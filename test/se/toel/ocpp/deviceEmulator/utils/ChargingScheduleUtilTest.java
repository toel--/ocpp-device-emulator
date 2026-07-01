/*
 * Verifies time-based selection of the active chargingSchedule period.
 */
package se.toel.ocpp.deviceEmulator.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChargingScheduleUtilTest {

    private JSONObject schedule(int[][] periods) {
        JSONArray arr = new JSONArray();
        for (int[] p : periods) {
            arr.put(new JSONObject().put("startPeriod", p[0]).put("limit", p[1]));
        }
        return new JSONObject().put("chargingRateUnit", "A").put("chargingSchedulePeriod", arr);
    }

    @Test
    public void singlePeriodAlwaysApplies() {
        JSONObject s = schedule(new int[][]{{0, 16}});
        assertEquals(16, ChargingScheduleUtil.activePeriodLimit(s, 0), 0.001);
        assertEquals(16, ChargingScheduleUtil.activePeriodLimit(s, 9999), 0.001);
    }

    @Test
    public void twoPeriodsAdvanceAtBoundary() {
        // 0 A for the first 60 s, then 11 A — the field case (delayed start)
        JSONObject s = schedule(new int[][]{{0, 0}, {60, 11}});
        assertEquals(0, ChargingScheduleUtil.activePeriodLimit(s, 0), 0.001);
        assertEquals(0, ChargingScheduleUtil.activePeriodLimit(s, 59), 0.001);
        assertEquals(11, ChargingScheduleUtil.activePeriodLimit(s, 60), 0.001);
        assertEquals(11, ChargingScheduleUtil.activePeriodLimit(s, 600), 0.001);
    }

    @Test
    public void threePeriodsPickTheLastStarted() {
        JSONObject s = schedule(new int[][]{{0, 6}, {30, 16}, {90, 8}});
        assertEquals(6, ChargingScheduleUtil.activePeriodLimit(s, 10), 0.001);
        assertEquals(16, ChargingScheduleUtil.activePeriodLimit(s, 30), 0.001);
        assertEquals(16, ChargingScheduleUtil.activePeriodLimit(s, 89), 0.001);
        assertEquals(8, ChargingScheduleUtil.activePeriodLimit(s, 120), 0.001);
    }

    @Test
    public void negativeElapsedFallsBackToFirstPeriod() {
        JSONObject s = schedule(new int[][]{{0, 10}, {60, 20}});
        assertEquals(10, ChargingScheduleUtil.activePeriodLimit(s, -5), 0.001);
    }
}
