/*
 * Builds the device implementation for the requested OCPP version.
 */
package se.toel.ocpp.deviceEmulator.device;

import se.toel.ocpp.deviceEmulator.device.ocpp16.Device;

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
                return new Device(deviceId, url, ocppVersion);
            default:
                throw new IllegalArgumentException("Unsupported OCPP version: " + ocppVersion);
        }

    }

}
