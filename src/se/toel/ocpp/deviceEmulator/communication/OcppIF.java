/*
 * The OCPP interface
 * Will expose the OCPP 1.6 at first and see it the 2.0.1 can be mapped here as well
 */
package se.toel.ocpp.deviceEmulator.communication;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author toel
 */
public interface OcppIF {
    
    public boolean connect();
    public boolean disconnect();
    public void sendReq(String id, String message, JSONObject payload);
    public void sendConf(String id, JSONObject payload);
    
}
