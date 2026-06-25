/*
 * The OCPP 2.0.1 implementation
 */
package se.toel.ocpp.deviceEmulator.communication.ocpp201;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import se.toel.ocpp.deviceEmulator.communication.CallbackIF;
import se.toel.ocpp.deviceEmulator.communication.OcppIF;
import se.toel.ocpp.deviceEmulator.communication.WebSocket;
import se.toel.ocpp.deviceEmulator.events.Event;
import se.toel.ocpp.deviceEmulator.events.EventHandler;
import se.toel.ocpp.deviceEmulator.events.EventIds;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class Ocpp201 extends Ocpp201Common implements OcppIF {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private final CallbackIF callback;
    EventHandler eventMgr = null;

    /***************************************************************************
     * Constructor
     **************************************************************************/
    public Ocpp201(String deviceId, String url, CallbackIF callback) {
        super(deviceId, url);
        this.callback = callback;
        eventMgr = EventHandler.getInstance();
    }

    /***************************************************************************
     * Public methods
     **************************************************************************/
    @Override
    public boolean connect(String basicAuthPassword) {

        busy = true;

        try {

            eventMgr.trigger(EventIds.INFO, deviceId, "connecting...");
            URI uri = getBackendUri();

            eventMgr.trigger(EventIds.CONNECTING, deviceId, "Connecting using " + uri);
            websocket = new WebSocket(uri);
            websocket.setConnectionLostTimeout(55);         // https://github.com/TooTallNate/Java-WebSocket/wiki/Lost-connection-detection

            websocket.registerCallback(callback);
            if (!websocket.isOpen()) {

                websocket.addHeader("Sec-WebSocket-Protocol", "ocpp2.0.1");
                websocket.addHeader("Sec-GridIt-Protocol", "1");

                if (basicAuthPassword != null) {
                    String auth = deviceId + ":" + basicAuthPassword;
                    auth = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    websocket.addHeader("Authorization", auth);
                }
                websocket.connect();
                int timeout = 1000;         // Ten second timeout
                while (--timeout > 0 && !websocket.isOpen()) {
                    Dev.sleep(10);
                }
            }

            if (websocket.isOpen()) {
                eventMgr.trigger(EventIds.CONNECTED, deviceId, "  success");
            } else {
                eventMgr.trigger(EventIds.CONNECTION_FAILED, deviceId, "  failure or timeout waiting for connection");
            }
        } finally {
            busy = false;
        }

        return websocket.isOpen();
    }

    @Override
    public boolean disconnect() {

        busy = true;

        eventMgr.trigger(EventIds.INFO, deviceId, "disconnecting...");

        try {
            websocket.close();
            int timeout = 100;
            while (--timeout > 0 && !websocket.isClosed()) Dev.sleep(10);
        } finally {
            busy = false;
        }

        boolean success = websocket.isClosed();
        if (success) {
            eventMgr.trigger(EventIds.INFO, deviceId, "disconnected!");
        } else {
            eventMgr.trigger(EventIds.INFO, deviceId, "disconnection failed!");
        }

        return success;
    }

    @Override
    public void sendReq(String id, String message, JSONObject payload) {

        JSONArray msg = new JSONArray();
        msg.put(2);
        msg.put(id);
        msg.put(message);
        msg.put(payload);
        String s = msg.toString();

        eventMgr.trigger(new Event(EventIds.OCPP_SENDING, deviceId, s));
        websocket.send(s);

    }

    @Override
    public void sendResponse(String id, JSONObject payload) {

        JSONArray msg = new JSONArray();
        msg.put(3);
        msg.put(id);
        msg.put(payload);
        String s = msg.toString();

        eventMgr.trigger(new Event(EventIds.OCPP_SENDING, deviceId, s));
        websocket.send(s);

    }

}
