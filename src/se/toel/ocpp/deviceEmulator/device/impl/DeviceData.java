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
            FIRMWARE_VERSION = "firmware_version",
            BASIC_AUTH_ENABLED = "basic_auth_enabled";

     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public String getModel() {
        return optString(CHARGE_POINT_MODEL, "Njord Go");
    }
    
    public void setModel(String model) {
        set(CHARGE_POINT_MODEL, model);
    }
    
    public String getVendor() {
        return optString(CHARGE_POINT_VENDOR, "vendor");
    }
    
    public void setVendor(String vendor) {
        set(CHARGE_POINT_VENDOR, vendor);
    }
    
    public String getFirmwareVersion() {
        return optString(FIRMWARE_VERSION, "0.0.1");
    }
    
    public void setFirmwareVersion(String firmware) {
        set(FIRMWARE_VERSION, firmware);
    }
    
    public boolean getBasicAuthEnabled() {
        return optBoolean(BASIC_AUTH_ENABLED, false);
    }
    
    public void setBasicAuthEnabled(boolean basicAuthEnabled) {
        setBoolean(BASIC_AUTH_ENABLED, basicAuthEnabled);
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/

}
