/*
 * One vehicle's charging session on one connector: plug in, authorize via StartTransaction, drive the
 * CC-CV current until full or the charge timer, stop, unplug. SoC is a closed loop on delivered Wh.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

import java.util.function.DoubleSupplier;
import se.toel.ocpp.deviceEmulator.device.SimDeviceIF;
import se.toel.ocpp.deviceEmulator.device.StartTransactionOutcome;
import se.toel.ocpp.deviceEmulator.device.ocpp16.Connector;

public final class ChargingSession {

    public enum Result { COMPLETED_TIMER, COMPLETED_FULL, BLOCKED, CALL_FAILED }

    private final SimDeviceIF device;
    private final int connectorId;
    private final Vehicle vehicle;
    private final SimDurations durations;
    private final SimClock clock;
    private final DoubleSupplier rng;

    public ChargingSession(SimDeviceIF device, int connectorId, Vehicle vehicle, SimDurations durations,
                           SimClock clock, DoubleSupplier rng) {
        this.device = device;
        this.connectorId = connectorId;
        this.vehicle = vehicle;
        this.durations = durations;
        this.clock = clock;
        this.rng = rng;
    }

    public Result run() throws InterruptedException {

        device.plugIn(connectorId);

        StartTransactionOutcome outcome = device.startTransaction(connectorId, vehicle.getId());
        if (!outcome.callSucceeded()) {
            device.unplug(connectorId);
            return Result.CALL_FAILED;
        }
        if (!outcome.isAccepted()) {
            // OCPP 1.6 created a transaction even though the idTag was rejected — close it, don't leak it.
            device.stopTransaction(outcome.transactionId(), Connector.StopReason_DeAuthorized);
            device.unplug(connectorId);
            return Result.BLOCKED;
        }

        device.setConnectorStatus(connectorId, Connector.STATUS_CHARGING);

        double startSoc = vehicle.randomStartSoc(rng);
        double capacityWh = vehicle.getBatteryCapacityKWh() * 1000.0;
        double meterStart = device.getMeterWh(connectorId);
        long start = clock.now();

        Result result;
        while (true) {
            double deliveredWh = device.getMeterWh(connectorId) - meterStart;
            double soc = startSoc + deliveredWh / capacityWh;

            if (vehicle.isFull(soc)) {
                device.setVehicleChargingCurrent(connectorId, 0);
                device.setConnectorStatus(connectorId, Connector.STATUS_SUSPENDEDEV);
                result = Result.COMPLETED_FULL;
                break;
            }
            if (clock.now() - start >= durations.chargeMs()) {
                result = Result.COMPLETED_TIMER;
                break;
            }

            double wantAmps = Vehicle.powerKWToAmps(vehicle.desiredPowerKW(soc));
            device.setVehicleChargingCurrent(connectorId, wantAmps);
            // Status under cap: reduced-but-nonzero stays Charging; a cap forcing ~0 is SuspendedEVSE.
            double effective = device.getConnector(connectorId).getChargingCurrent();
            if (wantAmps > 0 && effective <= 0.001) {
                device.setConnectorStatus(connectorId, Connector.STATUS_SUSPENDEDEVSE);
            } else {
                device.setConnectorStatus(connectorId, Connector.STATUS_CHARGING);
            }

            clock.sleep(durations.tickMs());
        }

        device.stopTransaction(outcome.transactionId(), Connector.StopReason_Local);
        device.unplug(connectorId);
        return result;
    }
}
