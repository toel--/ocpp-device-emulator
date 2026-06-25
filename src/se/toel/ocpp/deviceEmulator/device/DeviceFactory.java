/*
 * Builds the device implementation for the requested OCPP version.
 */
package se.toel.ocpp.deviceEmulator.device;

/**
 *
 * @author toel
 */
public class DeviceFactory {

    /***************************************************************************
     * Public methods
     **************************************************************************/
    public static DeviceIF create(String deviceId, String url, String ocppVersion) {

        switch (ocppVersion) {
            case "ocpp1.6":
                return new se.toel.ocpp.deviceEmulator.device.ocpp16.Device(deviceId, url, ocppVersion);
            case "ocpp2.0.1":
                return new se.toel.ocpp.deviceEmulator.device.ocpp201.Device(deviceId, url, ocppVersion);
            default:
                throw new IllegalArgumentException("Unsupported OCPP version: " + ocppVersion);
        }

    }

}
