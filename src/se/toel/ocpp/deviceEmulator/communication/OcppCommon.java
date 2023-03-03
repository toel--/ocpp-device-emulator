/*
 * Common parts for the OCPP implementation
 */

package se.toel.ocpp.deviceEmulator.communication;

import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public abstract class OcppCommon {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private final static Logger log = LoggerFactory.getLogger(OcppCommon.class);
    protected WebSocket websocket;
    protected boolean busy = false;
    private boolean isConnected = false;
    protected final boolean echo = true;
    protected boolean stdoutneednewline = false;  
    protected final String deviceId;
    protected final String url;
    
    
    

     /***************************************************************************
     * Constructor
     **************************************************************************/
    public OcppCommon(String deviceId, String url) {
        this.deviceId = deviceId;
        this.url = url;
    }

     /***************************************************************************
     * Public methods
     **************************************************************************/

    /***************************************************************************
     * Protected methods
     **************************************************************************/
        
    protected URI getBackendUri() {
        URI uri = null;
        try {
            uri = new URI(url+"/"+deviceId);
        } catch (URISyntaxException e) {
            log.error("While creating backend URI", e);
        }
        return uri;
    }
    
    protected String fromHex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return new String(data);
    }
    
    
    
    

}
