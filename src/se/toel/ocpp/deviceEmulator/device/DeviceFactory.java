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

    /** Create a 1.6 device exposing the sim-specific API. Only OCPP 1.6 is supported. */
    public static SimDeviceIF createSimDevice(String deviceId, String url) {
        se.toel.ocpp.deviceEmulator.device.ocpp16.Device device =
                new se.toel.ocpp.deviceEmulator.device.ocpp16.Device(deviceId, url, "ocpp1.6");
        device.setAutoPlugin(false);    // the simulation drives plug/unplug + status explicitly
        return device;
    }

}
