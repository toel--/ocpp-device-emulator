/*
 * Connector class
 */

package se.toel.ocpp.deviceEmulator.device.impl;

import org.json.JSONArray;
import org.json.JSONObject;
import se.toel.collection.DataMap;
import se.toel.ocpp.deviceEmulator.utils.DateTimeUtil;

/**
 *
 * @author toel
 */
public class Connector extends DataMap {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    // Transaction variables
    private final int id;
    
    private final String 
            CHARGING_CURRENT = "chargingCurrent",
            ERROR_CODE = "errorCode",
            ID_TAG = "idTag",
            METER_WH = "meterWh",
            RESERVATION_ID = "reservationId",
            TRANSACTION_ID = "transactionId",
            STATUS = "status",
            VENDOR_ERROR_CODE = "vendorErrorCode",
            PLUGGED_IN = "pluggedIn";
    
    public String stopReason = StopReason_Local;
    public String lastStatus = STATUS_UNAVAILABLE;
    private JSONObject txDefaultProfile = null;
    private JSONObject txProfile = null;
    
    public static final String 
            StopReason_EmergencyStop = "EmergencyStop",             // Emergency stop button was used.
            StopReason_EVDisconnected = "EVDisconnected",           // disconnecting of cable, vehicle moved away from inductive charge unit.
            StopReason_HardReset = "HardReset",                     // A hard reset command was received.
            StopReason_Local = "Local",                             // Stopped locally on request of the user at the Charge Point. This is a regular termination of a transaction. Examples: presenting an RFID tag, pressing a button to stop.
            StopReason_Other = "Other",                             // Any other reason.
            StopReason_PowerLoss = "PowerLoss",                     // Complete loss of power.
            StopReason_Reboot = "Reboot",                           // A locally initiated reset/reboot occurred. (for instance watchdog kicked in)
            StopReason_Remote = "Remote",                           // Stopped remotely on request of the user. This is a regular termination of a transaction. Examples: termination using a smartphone app, exceeding a (non local) prepaid credit.
            StopReason_SoftReset = "SoftReset",                     // A soft reset command was received.
            StopReason_UnlockCommand = "UnlockCommand",             // Central System sent an Unlock Connector command.
            StopReason_DeAuthorized = "DeAuthorized";               // The transaction was stopped because of the authorization status in a StartTransaction.conf
                    
    public static final String
            STATUS_AVAILABLE = "Available",
            STATUS_PREPARING = "Preparing",
            STATUS_CHARGING = "Charging",
            STATUS_SUSPENDEDEVSE = "SuspendedEVSE",
            STATUS_SUSPENDEDEV = "SuspendedEV",
            STATUS_FINISHING = "Finishing",
            STATUS_RESERVED = "Reserved",
            STATUS_UNAVAILABLE = "Unavailable",
            STATUS_FAULTED = "Faulted";
    
    
     /***************************************************************************
     * Constructor
     **************************************************************************/
    public Connector(int id, String storePath) {
        this.id = id;
        set(CHARGING_CURRENT, "16");
        set(ERROR_CODE, "NoError");
        set(VENDOR_ERROR_CODE, "");
        load(storePath+"/connector_"+id+".dat");
        set(STATUS, STATUS_UNAVAILABLE);                    // Force the first status to be "unavailable": it will be recomputed later
    }
    

     /***************************************************************************
     * Public methods
     **************************************************************************/
    
    public int getId() {
        return id;
    }
    
    public String getStatus() {
        return get(STATUS);
    }
    public void setStatus(String status) {
        set(STATUS, status);
    }
    
    public String getIdTag() {
        return get(ID_TAG);
    }
    public void setIdTag(String idTag) {
        set(ID_TAG, idTag);
    }
    
    public int getTransactionId() {
        return _getInt(TRANSACTION_ID);
    }
    public void setTransactionId(int id) {
        // Dev.info("Connector.setTransactionId("+id+")");
        _set(TRANSACTION_ID, id);
    }
    
    public int getReservationId() {
        return _getInt(RESERVATION_ID);
    }
    public void setReservationId(int id) {
        _set(RESERVATION_ID, id);
    }
    
    public double getMeterWh() {
        return _getDouble(METER_WH);
    }
    public void setMeterWh(double Wh) {
        _set(METER_WH, Wh);
    }
    
    public double getChargingCurrent() {
        return _getDouble(CHARGING_CURRENT);
    }
    public void setChargingCurrent(double current) {
        _set(CHARGING_CURRENT, current);
    }
    
    public String getErrorCode() {
        return get(ERROR_CODE);
    }
    public void setErrorCode(String errorCode) {
        set(ERROR_CODE, errorCode);
    }
    
    public String getVendorErrorCode() {
        return get(VENDOR_ERROR_CODE);
    }
    public void setVendorErrorCode(String errorCode) {
        set(VENDOR_ERROR_CODE, errorCode);
    }
    
    public boolean isPluggedIn() {
        return getBoolean(PLUGGED_IN);
    }
    public void setPluggedIn(boolean pluggedIn) {
        setBoolean(PLUGGED_IN, pluggedIn);
    }
        
    public void reset() {
     
        _set(ID_TAG, "");
        _set(RESERVATION_ID, 0);
        _set(TRANSACTION_ID, 0);
        set(STATUS, STATUS_UNAVAILABLE);
        set(ERROR_CODE, "NoError");
        stopReason = StopReason_Local;
        
    }
    
    
    
    
    public boolean setChargingProfile(JSONObject csChargingProfiles) {
        
       // {"chargingProfileId":1,"transactionId":31,"stackLevel":0,"chargingProfilePurpose":"TxProfile","chargingProfileKind":"Relative","chargingSchedule":{"chargingRateUnit":"A","chargingSchedulePeriod":[{"startPeriod":0,"limit":0.0}]}}}]
        
       boolean success = true;
       
       int profileTransactionId = csChargingProfiles.optInt("transactionId");
       int transactionId = _getInt(TRANSACTION_ID);
       if (profileTransactionId>0 && transactionId>0) {
           if (profileTransactionId!=transactionId) return false;
       }
       
       String chargingProfilePurpose = csChargingProfiles.getString("chargingProfilePurpose");
       switch (chargingProfilePurpose) {
           case "TxProfile": this.txProfile = csChargingProfiles; break;
           case "TxDefaultProfile": this.txDefaultProfile = csChargingProfiles; break;
           default: success = false;
       }
              
       processChargingProfile(csChargingProfiles);
       
       return success;
        
    }
    
    public boolean clearChargingProfile(String chargingProfilePurpose, int id, int stackLevel) {
     
        boolean success = false;
        
        if ("TxDefaultProfile".equals(chargingProfilePurpose)) {
            txDefaultProfile = null;
            success = true;
        }
        
        if ("TxProfile".equals(chargingProfilePurpose)) {
            if (txProfile==null) return true;
            if (id>0) {
                if (txProfile.getInt("chargingProfileId")==id) {
                    txProfile = null;
                    setChargingCurrent(16f);
                    success = true;
                }
            } else {
                txProfile = null;
                setChargingCurrent(16f);
                success = true;
            }
        }
        return success;
        
    }
    
    public JSONArray getMeterValues() {
     
        JSONArray meterValues = new JSONArray();
        JSONObject meterValue = new JSONObject();           // Required. The sampled meter values with timestamps.
        meterValue.put("timestamp", DateTimeUtil.toIso8601(System.currentTimeMillis()));
        JSONArray sampledValues = new JSONArray();
        
        double voltage = 230.1;
        
        JSONObject sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        sampledValue.put("format", "Raw");
        sampledValue.put("measurand", "Energy.Active.Import.Register");
        sampledValue.put("value", Math.round(getMeterWh()));            // Send rounded values for Wh
        sampledValue.put("location", "Outlet");
        sampledValue.put("unit", "Wh");
        sampledValues.put(sampledValue);
        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        // sampledValue.put("format", "Raw");
        sampledValue.put("measurand", "Power.Active.Import");
        sampledValue.put("value", Math.round(getChargingCurrent()*voltage));            // Send rounded values for W
        sampledValue.put("location", "Outlet");
        sampledValue.put("unit", "W");
        sampledValues.put(sampledValue);
        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        // sampledValue.put("format", "Raw");
        sampledValue.put("measurand", "Power.Active.Import");
        sampledValue.put("value", Math.round(getChargingCurrent()*voltage));            // Send rounded values for W
        sampledValue.put("location", "Outlet");
        sampledValue.put("unit", "W");
        sampledValues.put(sampledValue);
        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        sampledValue.put("measurand", "Current.Import");
        sampledValue.put("value", getChargingCurrent()/3);
        sampledValue.put("location", "Outlet");
        sampledValue.put("phase", "L1");
        sampledValue.put("unit", "A");
        sampledValues.put(sampledValue);
        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        sampledValue.put("measurand", "Current.Import");
        sampledValue.put("value", getChargingCurrent()/3);
        sampledValue.put("location", "Outlet");
        sampledValue.put("phase", "L2");
        sampledValue.put("unit", "A");
        sampledValues.put(sampledValue);
        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        sampledValue.put("measurand", "Current.Import");
        sampledValue.put("value", getChargingCurrent()/3);
        sampledValue.put("location", "Outlet");
        sampledValue.put("phase", "L3");
        sampledValue.put("unit", "A");
        sampledValues.put(sampledValue);
                        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        sampledValue.put("measurand", "Voltage");
        sampledValue.put("value", String.valueOf(voltage));
        sampledValue.put("location", "Outlet");
        sampledValue.put("phase", "L1");
        sampledValue.put("unit", "V");
        sampledValues.put(sampledValue);
        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        sampledValue.put("measurand", "Voltage");
        sampledValue.put("value", String.valueOf(voltage));
        sampledValue.put("location", "Outlet");
        sampledValue.put("phase", "L2");
        sampledValue.put("unit", "V");
        sampledValues.put(sampledValue);
        
        sampledValue = new JSONObject();
        sampledValue.put("context", "Sample.Periodic");                 // Should be Trigger on trigger message
        sampledValue.put("measurand", "Voltage");
        sampledValue.put("value", String.valueOf(voltage));
        sampledValue.put("location", "Outlet");
        sampledValue.put("phase", "L3");
        sampledValue.put("unit", "V");
        sampledValues.put(sampledValue);
        
        meterValue.put("sampledValue", sampledValues);
        meterValues.put(meterValue);
        
        return meterValues;
        
    }
    
    // Will be called every seconds
    public void tick() {
        
        if (STATUS_CHARGING.equals(getStatus())) {
            double current = getChargingCurrent();
            int voltage = 230;
            double watts = current*voltage;
            setMeterWh(getMeterWh()+watts/3600);
        }
        
    }
    
    
    @Override
    public String toString() {
    
        return "connector "+id;
    
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/

    private void processChargingProfile(JSONObject csChargingProfiles) {
       
       // TODO: should be dependant on chargingRateUnit
        
       JSONObject chargingSchedule = csChargingProfiles.getJSONObject("chargingSchedule");
       JSONObject chargingSchedulePeriod = chargingSchedule.getJSONArray("chargingSchedulePeriod").getJSONObject(0);
       
       double current = chargingSchedulePeriod.getDouble("limit");
       setChargingCurrent(current);
       
       switch (getStatus()) {
           case STATUS_SUSPENDEDEVSE: if (current>0) setStatus(STATUS_CHARGING); break;
           case STATUS_CHARGING: if (current<6) setStatus(STATUS_SUSPENDEDEVSE); break;
       }
       
       
    }
       
    
}
