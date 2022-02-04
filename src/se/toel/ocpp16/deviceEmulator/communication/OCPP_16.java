package se.toel.ocpp16.deviceEmulator.communication;

import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author toel
 */
public class OCPP_16 extends WebSocketClient {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private final Logger log = LoggerFactory.getLogger(OCPP_16.class);
    private CallbackIF callback;

     /***************************************************************************
     * Constructor
     **************************************************************************/
    public OCPP_16(URI uri) {
        super(uri);
    }

    public void registerCallback(CallbackIF callback) {
        
        this.callback = callback;
        
    }
    
    
     /***************************************************************************
     * WebSocketClient callbacks
     **************************************************************************/
    @Override
    public void onMessage( String message ) {
        callback.onMessage(message);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        callback.onOpen(handshake);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        callback.onClose(code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        callback.onError(ex);
    }


    /***************************************************************************
     * Private methods
     **************************************************************************/
    
    
    
    
    

                   
    

}
