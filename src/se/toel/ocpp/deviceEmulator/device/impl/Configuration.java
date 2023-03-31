/*
 * Holds the configuration
 */

package se.toel.ocpp.deviceEmulator.device.impl;

import se.toel.collection.DataMap;

/**
 *
 * @author toel
 */
public class Configuration extends DataMap {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    
    
     /***************************************************************************
     * Constructor
     **************************************************************************/
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public Configuration() {
        
        // Default values
        _set("AuthorizeRemoteTxRequests", false);
        _set("AuthorizationCacheEnabled", true);                            // If this key exists, the Charge Point supports an Authorization Cache. If this key reports a value of true, the Authorization Cache is enabled.
        _set("GetConfigurationMaxKeys", 99);
        _set("LocalAuthListMaxLength", 999);                                // Maximum number of identifications that can be stored in the Local Authorization List
        _set("MeterValueSampleInterval", 30);                               // Interval between sampling of metering (or other) data, intended to be transmitted by "MeterValues" PDUs. For charging session data (ConnectorId>0), samples are acquired and transmitted periodically at this interval from the start of the charging transaction. A value of "0" (numeric zero), by convention, is to be interpreted to mean that no sampled data should be transmitted.           
        _set("NumberOfConnectors", 1);
        _set("TransactionMessageAttempts", 3);                              // How often the Charge Point should try to submit a transaction-related message when the Central System fails to process it.
        _set("TransactionMessageRetryInterval", 60);                        // How long (seconds) the Charge Point should wait before resubmitting a transaction- related message that the Central System failed to process.
        _set("SupportedFeatureProfiles", "Core,FirmwareManagement,LocalAuthListManagement,Reservation,SmartCharging,RemoteTrigger");               // A list of supported Feature Profiles. Possible profile identifiers: Core, FirmwareManagement, LocalAuthListManagement, Reservation, SmartCharging and RemoteTrigger.  
        _set("ChargeProfileMaxStackLevel", 3);                              // Max StackLevel of a ChargingProfile. The number defined also indicates the max allowed number of installed charging schedules per Charging Profile Purposes.
        _set("MeterValuesSampledData", "Energy.Active.Import.Register");    // Sampled measurands to be included in a MeterValues.req PDU, every MeterValueSampleInterval seconds. Where applicable, the Measurand is combined with the optional phase; for instance: Voltage.L1 Default: "Energy.Active.Import.Register"
        _set("CurrentMaxAssignment", 16);                                   // CTEK specific: Max current
    }
    
     /***************************************************************************
     * Public methods
     **************************************************************************/
    public int getInt(String key) {
        return _getInt(key);
    }
    
    public int getMeterValueSampleInterval() {
        return _getInt("MeterValueSampleInterval");
    }
    
    public int getCurrentMaxAssignment() {
        return _getInt("CurrentMaxAssignment");
    }

    public boolean autoPlugin() {
        return _getBoolean("_auto_plugin", true);
    }
    
    /***************************************************************************
     * Private methods
     **************************************************************************/

}
