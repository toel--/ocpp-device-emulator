/*
 * Holds the configuration
 */

package se.toel.ocpp.deviceEmulator.communication.ocpp16;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import se.toel.collection.DataMap;

/**
 *
 * @author toel
 */
public class Configuration extends DataMap {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private final Set<String> knownKeys;


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
        _set("ChargingScheduleAllowedChargingRateUnit", "Current,Power");   // A list of supported quantities for use in a ChargingSchedule. Allowed values: 'Current' (A) and 'Power' (W). (read-only)
        _set("MeterValuesSampledData", "Energy.Active.Import.Register");    // Sampled measurands to be included in a MeterValues.req PDU, every MeterValueSampleInterval seconds. Where applicable, the Measurand is combined with the optional phase; for instance: Voltage.L1 Default: "Energy.Active.Import.Register"
        _set("MeterValuesSampledDataMaxLength", 99);                        // Maximum number of items in a MeterValuesSampledData Configuration Key. (read-only)
        _set("CurrentMaxAssignment", 16);                                   // CTEK specific: Max current

        // Snapshot the keys we recognise (the defaults above plus the conditionally-supported
        // AuthorizationKey) so ChangeConfiguration can answer NotSupported for anything else.
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, String> entry : entrySet()) keys.add(entry.getKey());
        keys.add("AuthorizationKey");
        knownKeys = Collections.unmodifiableSet(keys);
    }
    
     /***************************************************************************
     * Public methods
     **************************************************************************/
    public int getInt(String key) {
        return _getInt(key);
    }

    /** True if {@code key} is a configuration key this Charge Point recognises. */
    public boolean isKnownKey(String key) {
        return knownKeys.contains(key);
    }
    
    public int getMeterValueSampleInterval() {
        return _getInt("MeterValueSampleInterval");
    }

    public String getMeterValuesSampledData() {
        return get("MeterValuesSampledData");
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
