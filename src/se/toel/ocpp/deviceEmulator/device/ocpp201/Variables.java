/*
 * Holds the OCPP 2.0.1 variables
 */

package se.toel.ocpp.deviceEmulator.device.ocpp201;

import se.toel.collection.DataMap;
import se.toel.ocpp.deviceEmulator.utils.DateTimeUtil;

/**
 *
 * @author toel
 */
public class Variables extends DataMap {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    public static String SecurityCtrlr = "SecurityCtrlr";
    
     /***************************************************************************
     * Constructor
     **************************************************************************/
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public Variables() {
        
        // Default values
        
    }
    
     /***************************************************************************
     * Public methods
     **************************************************************************/
    
    public String getBasicAuthPassword() {
        return get(SecurityCtrlr, "BasicAuthPassword", "Actual");
    }
    public void setBasicAuthPassword(String password) {
        set(SecurityCtrlr, "BasicAuthPassword", "Actual", password);
    }
    
    
    public String get(String component, String variable, String type ) {
        if (type==null) type = "Actual";
        String key = component+"_"+variable+"_"+type;
        String value = get(key);
        
        // Override some variables
        switch (key) {
            case "ClockCtrlr_DateTime_Actual": value = DateTimeUtil.toIso8601(System.currentTimeMillis());
            
        }
        
        
        return value;
    }
    
    public void set(String component, String variable, String type, String value ) {
        String key = component+"_"+variable+"_"+type;
        set(key, value);
    }
    
    
    
    /***************************************************************************
     * Private methods
     **************************************************************************/

}
