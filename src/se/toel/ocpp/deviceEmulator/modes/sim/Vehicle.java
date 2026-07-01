/*
 * Pure EV model: battery, max power, and the CC-CV charging curve.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

import java.util.function.DoubleSupplier;

public final class Vehicle {

    public static final double NOMINAL_VOLTAGE = 230;
    public static final double FULL_SOC_THRESHOLD = 0.98;
    public static final double MIN_CV_POWER_KW = 4.1;            // ~6 A/phase floor; non-zero so "full" is reachable

    private final String id;
    private final double batteryCapacityKWh;
    private final double maxPowerKW;
    private final double taperStartSoc;
    private final double startSocMin;
    private final double startSocMax;

    public Vehicle(String id, double batteryCapacityKWh, double maxPowerKW, double taperStartSoc,
                   double startSocMin, double startSocMax) {
        this.id = id;
        this.batteryCapacityKWh = batteryCapacityKWh;
        this.maxPowerKW = maxPowerKW;
        this.taperStartSoc = taperStartSoc;
        this.startSocMin = startSocMin;
        this.startSocMax = startSocMax;
    }

    public String getId() {
        return id;
    }

    public double getBatteryCapacityKWh() {
        return batteryCapacityKWh;
    }

    public boolean isFull(double soc) {
        return soc >= FULL_SOC_THRESHOLD;
    }

    /** Power (kW) the vehicle wants at this SoC: CC plateau, linear CV taper to a floor, 0 when full. */
    public double desiredPowerKW(double soc) {
        if (soc >= FULL_SOC_THRESHOLD) return 0;
        if (soc < taperStartSoc) return maxPowerKW;
        double frac = (soc - taperStartSoc) / (FULL_SOC_THRESHOLD - taperStartSoc);
        return maxPowerKW - (maxPowerKW - MIN_CV_POWER_KW) * frac;
    }

    public double randomStartSoc(DoubleSupplier rng) {
        return startSocMin + (startSocMax - startSocMin) * rng.getAsDouble();
    }

    /** Convert kW to the connector's 3-phase summed amps (power = amps * NOMINAL_VOLTAGE). */
    public static double powerKWToAmps(double kw) {
        return kw * 1000.0 / NOMINAL_VOLTAGE;
    }
}
