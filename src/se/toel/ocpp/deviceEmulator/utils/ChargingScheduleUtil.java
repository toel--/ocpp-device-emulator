/*
 * Helpers for interpreting an OCPP chargingSchedule over time.
 */
package se.toel.ocpp.deviceEmulator.utils;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ChargingScheduleUtil {

    private ChargingScheduleUtil() {
    }

    /**
     * The limit of the schedule period in effect at {@code elapsedSeconds}: the last period whose
     * startPeriod is &lt;= elapsedSeconds (periods are ordered by startPeriod). Before the first
     * period's start, the first period's limit applies.
     */
    public static double activePeriodLimit(JSONObject chargingSchedule, long elapsedSeconds) {
        JSONArray periods = chargingSchedule.getJSONArray("chargingSchedulePeriod");
        double limit = periods.getJSONObject(0).getDouble("limit");
        for (int i = 0; i < periods.length(); i++) {
            JSONObject period = periods.getJSONObject(i);
            if (period.getInt("startPeriod") <= elapsedSeconds) {
                limit = period.getDouble("limit");
            } else {
                break;
            }
        }
        return limit;
    }
}
