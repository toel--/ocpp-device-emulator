/*
 * Version-agnostic device contract that the application modes drive.
 */
package se.toel.ocpp.deviceEmulator.device;

/**
 *
 * @author toel
 */
public interface DeviceIF {

    void start();

    void shutdown();

    void setAutoMeterValues(boolean autoMeterValues);

    boolean doStartTransaction(int connectorId, String idTag);

    boolean doStopTransaction(int transactionId);

    void doMeterValues(int connectorId);

    ConnectorIF getConnector(int connectorId);

}
