/*
 * Common parts for the OCPP implementation
 */

package se.toel.ocpp.deviceEmulator.communication;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.toel.ocpp.deviceEmulator.communication.WebSocket;
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
    protected final boolean echo = true;
    protected boolean stdoutneednewline = false;  
    protected final String id;
    protected final String url;
    
    
    

     /***************************************************************************
     * Constructor
     **************************************************************************/
    public OcppCommon(String id, String url) {
        this.id = id;
        this.url = url;
    }

     /***************************************************************************
     * Public methods
     **************************************************************************/

    /***************************************************************************
     * Protected methods
     **************************************************************************/
    protected void echo(String s) {
        if (echo) {
            if (stdoutneednewline) System.out.println("");
            Dev.info(s);
            stdoutneednewline=false;
        }
    }
    
    protected URI getBackendUri() {
        URI uri = null;
        try {
            uri = new URI(url+"/"+id);
        } catch (URISyntaxException e) {
            log.error("While creating backend URI", e);
        }
        return uri;
    }
    
    
    
    
    

}
