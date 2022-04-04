/*
 * The OCPP 1.6 implementation
 */
package se.toel.ocpp.deviceEmulator.communication;

import java.net.URI;
import org.json.JSONArray;
import org.json.JSONObject;
import se.toel.ocpp.deviceEmulator.communication.CallbackIF;
import se.toel.ocpp.deviceEmulator.communication.WebSocket;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class Ocpp16 extends OcppCommon implements OcppIF {
    
    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private boolean isConnected = false;
    private final CallbackIF callback;
    
    private final JSONObject jsonStatusAccepted = new JSONObject("{\"status\": \"Accepted\"}"); 
    private final JSONObject jsonStatusRejected = new JSONObject("{\"status\": \"Rejected\"}");
    private final JSONObject jsonStatusNotSupported = new JSONObject("{\"status\": \"NotSupported\"}");
    private final JSONObject jsonStatusNotImplemented = new JSONObject("{\"status\": \"NotImplemented\"}");
    private final JSONObject jsonStatusUnknown = new JSONObject("{\"status\": \"Unknown\"}");
    
    /***************************************************************************
     * Constructor
     **************************************************************************/
    public Ocpp16(String id, String url, CallbackIF callback) {
        super(id, url);
        this.callback = callback;
    }

    /***************************************************************************
     * Public methods
     **************************************************************************/
    @Override
    public boolean connect() {
        busy = true;
        
        try {
            echo("connecting...");
            URI uri = getBackendUri();
            Dev.info("Connecting using " + uri);
            websocket = new WebSocket(uri);
            websocket.registerCallback(callback);
            if (!websocket.isOpen()) {
                websocket.addHeader("Sec-WebSocket-Protocol", "ocpp1.6");
                websocket.connect();
                int timeout = 100;
                while (--timeout > 0 && !isConnected) {
                    Dev.sleep(10);
                }
            }

            if (isConnected) {
                echo("  success");
            } else {
                echo("  failure");
            }
        } finally {
            busy = false;
        }

        return websocket.isOpen();
    }
    
    @Override
    public boolean disconnect() {
        
        busy = true;
        
        try {
            websocket.close();
            int timeout = 100;
            while (--timeout>0 && isConnected) Dev.sleep(10);
        } finally {
            busy = false;
        }
        
        return websocket.isClosed();
    }
    
    
    @Override
    public void sendReq(String id, String message, JSONObject payload) {
                
        JSONArray msg = new JSONArray();
        msg.put(2);
        msg.put(id);
        msg.put(message);
        msg.put(payload);
        String s = msg.toString();
        
        echo("S: "+s);
        websocket.send(s);
        
    }
    
    @Override
    public void sendConf(String id, JSONObject payload) {
                
        // Dev.sleep((int)(Math.random()*1000));
        
        JSONArray msg = new JSONArray();
        msg.put(3);
        msg.put(id);
        msg.put(payload);
        String s = msg.toString();
        
        echo("S: "+s);
        websocket.send(s);
        
    }
    
    
    
    
    
    
    
    
    /***************************************************************************
     * Private methods
     **************************************************************************/
    
    
    
    
    
    
    
    
    
}
