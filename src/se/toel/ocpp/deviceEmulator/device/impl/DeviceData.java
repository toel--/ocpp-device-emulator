/*
 * Hodls the device data
 */

package se.toel.ocpp.deviceEmulator.device.impl;

import se.toel.collection.DataMap;

/**
 *
 * @author toel
 */
public class DeviceData extends DataMap {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private final String 
            CHARGE_POINT_MODEL = "charge_point_model",
            CHARGE_POINT_VENDOR = "charge_point_vendor",
            FIRMWARE_VERSION = "firmware_version";

     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public String getModel() {
        return opt(CHARGE_POINT_MODEL, "Njord Go");
    }
    
    public void setModel(String model) {
        set(CHARGE_POINT_MODEL, model);
    }
    
    public String getVendor() {
        return opt(CHARGE_POINT_VENDOR, "vendor");
    }
    
    public void setVendor(String vendor) {
        set(CHARGE_POINT_VENDOR, vendor);
    }
    
    public String getFirmwareVersion() {
        return opt(FIRMWARE_VERSION, "0.0.1");
    }
    
    public void setFirmwareVersion(String firmware) {
        set(FIRMWARE_VERSION, firmware);
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/

}
