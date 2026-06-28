/*
 * Holds the configuration
 */

package se.toel.ocpp.deviceEmulator.communication.ocpp16;

import java.util.Arrays;
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

    /** Standard OCPP 1.6 keys the Charge Point reports as read-only (rejects ChangeConfiguration of). */
    private static final Set<String> READ_ONLY_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ConnectorPhaseRotationMaxLength",
            "GetConfigurationMaxKeys",
            "MeterValuesAlignedDataMaxLength",
            "MeterValuesSampledDataMaxLength",
            "NumberOfConnectors",
            "StopTxnAlignedDataMaxLength",
            "StopTxnSampledDataMaxLength",
            "SupportedFeatureProfiles",
            "SupportedFeatureProfilesMaxLength",
            "LocalAuthListMaxLength",
            "SendLocalListMaxLength",
            "ReserveConnectorZeroSupported",
            "ChargeProfileMaxStackLevel",
            "ChargingScheduleAllowedChargingRateUnit",
            "ChargingScheduleMaxPeriods",
            "ConnectorSwitch3to1PhaseSupported",
            "MaxChargingProfilesInstalled")));

    private final Set<String> knownKeys;


     /***************************************************************************
     * Constructor
     **************************************************************************/
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public Configuration() {

        // --- Core profile ---
        _set("AllowOfflineTxForUnknownId", false);
        _set("AuthorizationCacheEnabled", true);                            // If this key exists, the Charge Point supports an Authorization Cache. If this key reports a value of true, the Authorization Cache is enabled.
        _set("AuthorizeRemoteTxRequests", false);
        _set("BlinkRepeat", 0);
        _set("ClockAlignedDataInterval", 0);
        _set("ConnectionTimeOut", 60);                                      // Interval (s) until incipient transaction is automatically cancelled, when EV is not plugged in.
        _set("ConnectorPhaseRotation", "NotApplicable");
        _set("ConnectorPhaseRotationMaxLength", 2);                         // (read-only)
        _set("GetConfigurationMaxKeys", 99);                               // (read-only)
        _set("HeartbeatInterval", 3600);
        _set("LightIntensity", 100);
        _set("LocalAuthorizeOffline", true);
        _set("LocalPreAuthorize", false);
        _set("MaxEnergyOnInvalidId", 0);
        _set("MeterValuesAlignedData", "Energy.Active.Import.Register");
        _set("MeterValuesAlignedDataMaxLength", 99);                        // (read-only)
        _set("MeterValuesSampledData", "Energy.Active.Import.Register");    // Sampled measurands to be included in a MeterValues.req PDU, every MeterValueSampleInterval seconds. Where applicable, the Measurand is combined with the optional phase; for instance: Voltage.L1
        _set("MeterValuesSampledDataMaxLength", 99);                        // (read-only)
        _set("MeterValueSampleInterval", 30);                              // Interval (s) between sampling of metering data. 0 means no sampled data transmitted.
        _set("MinimumStatusDuration", 0);
        _set("NumberOfConnectors", 1);                                      // (read-only)
        _set("ResetRetries", 1);
        _set("StopTransactionOnEVSideDisconnect", true);
        _set("StopTransactionOnInvalidId", true);
        _set("StopTxnAlignedData", "");
        _set("StopTxnAlignedDataMaxLength", 99);                            // (read-only)
        _set("StopTxnSampledData", "");
        _set("StopTxnSampledDataMaxLength", 99);                            // (read-only)
        _set("SupportedFeatureProfiles", "Core,FirmwareManagement,LocalAuthListManagement,Reservation,SmartCharging,RemoteTrigger"); // (read-only)
        _set("SupportedFeatureProfilesMaxLength", 6);                       // (read-only)
        _set("TransactionMessageAttempts", 3);                             // How often the Charge Point should try to submit a transaction-related message when the Central System fails to process it.
        _set("TransactionMessageRetryInterval", 60);                       // How long (s) the Charge Point should wait before resubmitting a transaction-related message that the Central System failed to process.
        _set("UnlockConnectorOnEVSideDisconnect", true);
        _set("WebSocketPingInterval", 0);

        // --- LocalAuthListManagement profile ---
        _set("LocalAuthListEnabled", true);
        _set("LocalAuthListMaxLength", 999);                               // Maximum number of identifications that can be stored in the Local Authorization List. (read-only)
        _set("SendLocalListMaxLength", 99);                                // (read-only)

        // --- Reservation profile ---
        _set("ReserveConnectorZeroSupported", false);                      // (read-only)

        // --- SmartCharging profile ---
        _set("ChargeProfileMaxStackLevel", 3);                             // Max StackLevel of a ChargingProfile. Also the max allowed number of installed charging schedules per Charging Profile Purpose. (read-only)
        _set("ChargingScheduleAllowedChargingRateUnit", "Current,Power");  // Supported quantities in a ChargingSchedule: 'Current' (A) and 'Power' (W). (read-only)
        _set("ChargingScheduleMaxPeriods", 99);                            // (read-only)
        _set("ConnectorSwitch3to1PhaseSupported", false);                  // (read-only)
        _set("MaxChargingProfilesInstalled", 99);                          // (read-only)

        // --- CTEK specific ---
        _set("CurrentMaxAssignment", 16);                                  // CTEK specific: Max current

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

    /** True if {@code key} is read-only (reported as such, and ChangeConfiguration of it is rejected). */
    public boolean isReadOnly(String key) {
        return READ_ONLY_KEYS.contains(key);
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
