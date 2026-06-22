/*
 * A small, scriptable OCPP central-system stand-in for tests.
 *
 * Acts as the backend the device emulator connects to. For each inbound OCPP
 * CALL frame ([2, msgId, action, payload]) a per-action handler decides how to
 * answer (CALLRESULT, CALLERROR, malformed text, nothing, or drop the socket).
 * All received frames are recorded so tests can assert on the device's traffic.
 */
package se.toel.ocpp.deviceEmulator.support;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author toel
 */
public class TestCentralSystem extends WebSocketServer {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private final int port;
    private final Map<String, Function<JSONArray, Reply>> handlers = new HashMap<>();
    private final List<JSONArray> received = new ArrayList<>();
    private final CountDownLatch startLatch = new CountDownLatch(1);

    /***************************************************************************
     * Constructor
     **************************************************************************/
    public TestCentralSystem(int port) {
        super(new InetSocketAddress(port));
        this.port = port;
        setReuseAddr(true);
    }

    /***************************************************************************
     * Public methods
     **************************************************************************/
    public int getListenPort() {
        return port;
    }

    public void onCall(String action, Function<JSONArray, Reply> handler) {
        handlers.put(action, handler);
    }

    public void startAndWait() throws InterruptedException {
        start();
        if (!startLatch.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("TestCentralSystem did not start within 2s on port " + port);
        }
    }

    public List<JSONArray> getReceived() {
        synchronized (received) {
            return new ArrayList<>(received);
        }
    }

    public JSONArray awaitReceived(String action, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (received) {
            while (true) {
                JSONArray found = findCall(action);
                if (found != null) return found;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) throw new AssertionError("Timed out waiting for CALL '" + action + "' after " + timeoutMs + "ms");
                received.wait(remaining);
            }
        }
    }

    @Override
    public void onStart() {
        startLatch.countDown();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // nothing to do
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // nothing to do
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONArray frame = new JSONArray(message);
        synchronized (received) {
            received.add(frame);
            received.notifyAll();
        }
        if (frame.getInt(0) == 2) {
            String action = frame.getString(2);
            Function<JSONArray, Reply> handler = handlers.get(action);
            if (handler != null) {
                respond(conn, frame.getString(1), handler.apply(frame));
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/
    private JSONArray findCall(String action) {
        for (JSONArray frame : received) {
            if (frame.getInt(0) == 2 && action.equals(frame.getString(2))) return frame;
        }
        return null;
    }

    private void respond(WebSocket conn, String msgId, Reply reply) {
        switch (reply.kind) {
            case RESULT:
                conn.send(new JSONArray().put(3).put(msgId).put(reply.payload).toString());
                break;
            case ERROR:
                conn.send(new JSONArray().put(4).put(msgId).put(reply.code).put(reply.description).put(new JSONObject()).toString());
                break;
            case MALFORMED:
                conn.send(reply.raw);
                break;
            case NONE:
                break;
            case DROP:
                conn.close();
                break;
        }
    }

}
