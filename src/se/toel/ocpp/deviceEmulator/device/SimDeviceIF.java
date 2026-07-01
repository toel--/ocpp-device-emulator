/*
 * Sim-specific device contract for the charging simulation (OCPP 1.6 only). Extends the generic
 * DeviceIF with plug/unplug, explicit transaction control, vehicle-current and status drive.
 */
package se.toel.ocpp.deviceEmulator.device;

public interface SimDeviceIF extends DeviceIF {

    void setChargePointIdentity(String model, String vendor, String firmware);

    /** True once the device is connected to the backend AND its BootNotification was accepted. */
    boolean isReady();

    /** Physical plug-in: pluggedIn=true, status Preparing, StatusNotification sent. Does NOT start a tx. */
    void plugIn(int connectorId);

    /** Physical unplug: status Finishing then Available, pluggedIn=false. Does NOT stop a tx. */
    void unplug(int connectorId);

    /** Send StartTransaction with the given idTag and return the auth outcome + transactionId. */
    StartTransactionOutcome startTransaction(int connectorId, String idTag);

    /** Send StopTransaction for the transaction with the given OCPP StopReason. */
    void stopTransaction(int transactionId, String reason);

    /** Set the EV's desired current (3-phase sum, A); delivered = min(this, backend caps). */
    void setVehicleChargingCurrent(int connectorId, double amps);

    /** Set the connector status and emit a StatusNotification immediately. */
    void setConnectorStatus(int connectorId, String status);

    /** Energy register (Wh) of the connector, for the SoC closed loop. */
    double getMeterWh(int connectorId);
}
