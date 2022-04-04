/*
 * Authorization Cache
 */

package se.toel.ocpp.deviceEmulator.device.impl;

import java.util.Map;
import org.json.JSONObject;
import se.toel.collection.DataMap;
import se.toel.ocpp.deviceEmulator.utils.DateTimeUtil;

/**
 *
 * @author toel
 */
public class AuthorizationCache extends DataMap {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/

     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public void update(String idTag, JSONObject idTagInfo ) {
     
        set(idTag, idTagInfo.toString());
        
    }
    
    public void tick() {
     
        for (Map.Entry<String, String> entry : data.entrySet()) {
            JSONObject idTagInfo = new JSONObject(entry);
            String expiryDate = idTagInfo.optString("expiryDate");
            if (!expiryDate.isEmpty()) {
                long t = DateTimeUtil.fromIso8601(expiryDate);
                if (t>System.currentTimeMillis()) {
                    idTagInfo.put("status", "Expired");
                }
            }
        }
        
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/

}
