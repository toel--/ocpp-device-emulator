/*
 * Callback Interface
 */

package se.toel.ocpp16.deviceEmulator.communication;

import org.java_websocket.handshake.ServerHandshake;

/**
 *
 * @author toel
 */
public interface CallbackIF {

    void onMessage( String message );
    void onOpen(ServerHandshake handshake);
    void onClose(int code, String reason, boolean remote);
    void onError(Exception ex);

}
