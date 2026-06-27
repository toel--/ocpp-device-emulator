/*
 * Connector class
 */

package se.toel.ocpp.deviceEmulator.device.ocpp16;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import se.toel.collection.DataMap;
import se.toel.ocpp.deviceEmulator.device.ConnectorIF;
import se.toel.ocpp.deviceEmulator.utils.DateTimeUtil;

/**
 *
 * @author toel
 */
public class Connector extends DataMap implements ConnectorIF {

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
    private double requestedCurrent = 16;                    // current this connector would draw (A) absent any charge-point cap
    private double chargePointMaxCurrent = -1;               // ceiling assigned from the charge-point budget (A); -1 = no cap
    
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

    public static final String
            MEASURAND_ENERGY = "Energy.Active.Import.Register",
            MEASURAND_POWER = "Power.Active.Import",
            MEASURAND_CURRENT = "Current.Import",
            MEASURAND_VOLTAGE = "Voltage";

    /** The MeterValues measurands this emulator can actually produce. */
    public static final Set<String> SUPPORTED_MEASURANDS = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList(MEASURAND_ENERGY, MEASURAND_POWER, MEASURAND_CURRENT, MEASURAND_VOLTAGE)));

    private static final double METER_VOLTAGE = 230.1;          // reported phase voltage


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
        double wh = _getDouble(METER_WH);
        if (wh<0) {
            wh = 0;
            setMeterWh(wh);
        }
        return wh; 
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

    /** Assign the ceiling distributed from the charge-point wide budget (A); -1 removes it. */
    public void setChargePointMaxCurrent(double ceiling) {

       if (ceiling==chargePointMaxCurrent) return;          // unchanged: avoid re-toggling the charging status every tick
       chargePointMaxCurrent = ceiling;
       applyChargingCurrent(applyChargePointMax(requestedCurrent));

    }

    /** The current this connector wants to draw (A), before any charge-point cap. */
    public double getRequestedCurrent() {
       return requestedCurrent;
    }

    /** True when the connector is actively drawing (or wanting to draw) power. */
    public boolean isDemanding() {
       return STATUS_CHARGING.equals(getStatus()) || STATUS_SUSPENDEDEVSE.equals(getStatus());
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
                    setRequestedCurrent(16);
                    success = true;
                }
            } else {
                txProfile = null;
                setRequestedCurrent(16);
                success = true;
            }
        }
        return success;
        
    }
    
    public JSONArray getMeterValues(String measurands) {

        JSONArray meterValues = new JSONArray();
        JSONObject meterValue = new JSONObject();           // Required. The sampled meter values with timestamps.
        meterValue.put("timestamp", DateTimeUtil.toIso8601(System.currentTimeMillis()));
        JSONArray sampledValues = new JSONArray();

        // Emit only the configured measurands we actually support, in the requested order.
        for (String measurand : splitMeasurands(measurands)) {
            switch (measurand) {
                case MEASURAND_ENERGY: {
                    JSONObject energy = sample(MEASURAND_ENERGY, String.valueOf(getMeterWh()), "Wh");
                    energy.put("format", "Raw");
                    sampledValues.put(energy);
                    break;
                }
                case MEASURAND_POWER:
                    sampledValues.put(sample(MEASURAND_POWER, Math.round(getChargingCurrent()*METER_VOLTAGE), "W"));
                    break;
                case MEASURAND_CURRENT:
                    addPerPhase(sampledValues, MEASURAND_CURRENT, getChargingCurrent()/3, "A");
                    break;
                case MEASURAND_VOLTAGE:
                    addPerPhase(sampledValues, MEASURAND_VOLTAGE, String.valueOf(METER_VOLTAGE), "V");
                    break;
            }
        }

        meterValue.put("sampledValue", sampledValues);
        meterValues.put(meterValue);

        return meterValues;

    }

    /** True only if every measurand in the comma-separated list is one we can produce. */
    public static boolean areMeasurandsSupported(String measurands) {

        for (String measurand : splitMeasurands(measurands)) {
            if (!SUPPORTED_MEASURANDS.contains(measurand)) return false;
        }
        return true;

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

    private static List<String> splitMeasurands(String measurands) {

       List<String> list = new ArrayList<>();
       if (measurands==null) return list;
       for (String part : measurands.split(",")) {
           String measurand = part.trim();
           if (!measurand.isEmpty()) list.add(measurand);
       }
       return list;

    }

    private JSONObject sample(String measurand, Object value, String unit) {

       JSONObject sampledValue = new JSONObject();
       sampledValue.put("context", "Sample.Periodic");      // Should be Trigger on a trigger message
       sampledValue.put("measurand", measurand);
       sampledValue.put("value", value);
       sampledValue.put("location", "Outlet");
       sampledValue.put("unit", unit);
       return sampledValue;

    }

    private void addPerPhase(JSONArray out, String measurand, Object value, String unit) {

       for (String phase : new String[]{"L1", "L2", "L3"}) {
           JSONObject sampledValue = sample(measurand, value, unit);
           sampledValue.put("phase", phase);
           out.put(sampledValue);
       }

    }

    private void processChargingProfile(JSONObject csChargingProfiles) {

       // TODO: should be dependant on chargingRateUnit

       JSONObject chargingSchedule = csChargingProfiles.getJSONObject("chargingSchedule");
       JSONObject chargingSchedulePeriod = chargingSchedule.getJSONArray("chargingSchedulePeriod").getJSONObject(0);

       double current = chargingSchedulePeriod.getDouble("limit");
       setRequestedCurrent(current);

    }

    /** Record the requested current and apply it through the charge-point ceiling. */
    private void setRequestedCurrent(double current) {

       requestedCurrent = current;
       applyChargingCurrent(applyChargePointMax(current));

    }

    /** Set the charging current and move between Charging/SuspendedEVSE accordingly. */
    private void applyChargingCurrent(double current) {

       setChargingCurrent(current);

       switch (getStatus()) {
           case STATUS_SUSPENDEDEVSE: if (current>0) setStatus(STATUS_CHARGING); break;
           case STATUS_CHARGING: if (current<6) setStatus(STATUS_SUSPENDEDEVSE); break;
       }

    }

    /** Clamp a requested current to the ceiling assigned from the charge-point budget, if any. */
    private double applyChargePointMax(double current) {

       if (chargePointMaxCurrent>=0 && current>chargePointMaxCurrent) return chargePointMaxCurrent;
       return current;

    }


}
