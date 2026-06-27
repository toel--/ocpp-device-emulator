/*
 * A small, scriptable OCPP central-system stand-in for tests.
 *
 * Acts as the backend the device emulator connects to. For each inbound OCPP
 * CALL frame ([2, msgId, action, payload]) a per-action handler decides how to
 * answer (CALLRESULT, CALLERROR, malformed text, nothing, or drop the socket).
 * All received frames are recorded so tests can assert on the device's traffic.
 *
 * It can also initiate CALLs to the device with {@link #sendCall} and wait for
 * the matching CALLRESULT with {@link #awaitResult}, so tests can drive
 * CS-initiated requests (SetChargingProfile, Reset, ...).
 */
package se.toel.ocpp.deviceEmulator.support;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AtomicInteger callCounter = new AtomicInteger();
    private volatile WebSocket connection;

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

    /**
     * Assert a CALL is NOT received within the window. Returns as soon as the window elapses
     * (absence confirmed), but fails fast the instant a matching CALL arrives.
     */
    public void assertNotReceivedWithin(String action, long windowMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + windowMs;
        synchronized (received) {
            while (true) {
                if (findCall(action) != null) throw new AssertionError("Unexpected CALL '" + action + "' was received");
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return;
                received.wait(remaining);
            }
        }
    }

    /** Send a CS-initiated CALL ([2, msgId, action, payload]) to the connected device; returns the msgId. */
    public String sendCall(String action, JSONObject payload) {
        WebSocket conn = connection;
        if (conn == null) throw new IllegalStateException("No device connected to TestCentralSystem on port " + port);
        String msgId = "cs-" + callCounter.incrementAndGet();
        conn.send(new JSONArray().put(2).put(msgId).put(action).put(payload).toString());
        return msgId;
    }

    /** Wait for the CALLRESULT ([3, msgId, payload]) the device returns for a {@link #sendCall}, and return its payload. */
    public JSONObject awaitResult(String msgId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (received) {
            while (true) {
                JSONArray found = findResult(msgId);
                if (found != null) return found.getJSONObject(2);
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) throw new AssertionError("Timed out waiting for CALLRESULT '" + msgId + "' after " + timeoutMs + "ms");
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
        connection = conn;
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

    private JSONArray findResult(String msgId) {
        for (JSONArray frame : received) {
            if (frame.getInt(0) == 3 && msgId.equals(frame.getString(1))) return frame;
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
