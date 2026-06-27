/*
 * Splits a charge-point wide current budget across the connectors that draw from it.
 */
package se.toel.ocpp.deviceEmulator.utils;

/**
 * Max-min fair (water-filling) division of a total current budget.
 *
 * A ChargePointMaxProfile caps the whole charge point, not a single connector, so
 * the budget has to be shared by the connectors that actually want power. Each
 * connector first gets an equal share; whoever asks for less than its share keeps
 * only what it asked for and the leftover is re-divided among the rest. This
 * maximises utilisation without ever exceeding the budget.
 *
 * @author toel
 */
public final class ChargePointBudget {

    private ChargePointBudget() {
    }

    /**
     * @param demands the current (A) each connector would draw, uncapped
     * @param budget  the total current (A) the charge point may deliver
     * @return the current (A) granted to each connector, index-aligned with {@code demands}
     */
    public static double[] split(double[] demands, double budget) {

        int count = demands.length;
        double[] grant = new double[count];
        boolean[] settled = new boolean[count];
        int open = count;
        double remaining = budget;

        while (open>0) {
            double share = remaining/open;
            boolean progressed = false;

            for (int i=0; i<count; i++) {
                if (!settled[i] && demands[i]<=share) {
                    grant[i] = demands[i];
                    remaining -= demands[i];
                    settled[i] = true;
                    open--;
                    progressed = true;
                }
            }

            if (!progressed) {
                for (int i=0; i<count; i++) {
                    if (!settled[i]) grant[i] = share;
                }
                break;
            }
        }

        return grant;

    }

    /** Convert a charging-schedule limit to amps; a W limit is divided by the nominal voltage. */
    public static double limitToAmps(double limit, String unit, double voltage) {

        return "W".equalsIgnoreCase(unit) ? limit/voltage : limit;

    }

}
