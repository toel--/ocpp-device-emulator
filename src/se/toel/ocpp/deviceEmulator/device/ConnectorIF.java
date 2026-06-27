/*
 * Version-agnostic connector view used by modes and tests.
 */
package se.toel.ocpp.deviceEmulator.device;

/**
 *
 * @author toel
 */
public interface ConnectorIF {

    int getTransactionId();

    String getStatus();

    double getChargingCurrent();

}
