/*
 * Self-test for the TestCentralSystem test backend.
 */
package se.toel.ocpp.deviceEmulator.support;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.net.URI;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class TestCentralSystemSelfTest {

    @Test
    public void test01_recordsCallAndRepliesWithResult() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18170);
        cs.onCall("BootNotification", call -> Reply.result(new JSONObject().put("status", "Accepted")));
        cs.startAndWait();

        final StringBuilder got = new StringBuilder();
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:18170/CP1")) {
            @Override public void onOpen(ServerHandshake h) { send("[2,\"m1\",\"BootNotification\",{\"x\":1}]"); }
            @Override public void onMessage(String m) { got.append(m); }
            @Override public void onClose(int c, String r, boolean remote) {}
            @Override public void onError(Exception e) {}
        };
        client.connectBlocking();

        JSONArray received = cs.awaitReceived("BootNotification", 2000);
        assertEquals("m1", received.getString(1));

        long deadline = System.currentTimeMillis() + 2000;
        while (got.length() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        JSONArray reply = new JSONArray(got.toString());
        assertEquals(3, reply.getInt(0));
        assertEquals("m1", reply.getString(1));
        assertEquals("Accepted", reply.getJSONObject(2).getString("status"));

        client.closeBlocking();
        cs.stop();
    }

    @Test
    public void test02_repliesWithCallError() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18171);
        cs.onCall("Authorize", call -> Reply.error("InternalError", "boom"));
        cs.startAndWait();

        final StringBuilder got = new StringBuilder();
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:18171/CP1")) {
            @Override public void onOpen(ServerHandshake h) { send("[2,\"a1\",\"Authorize\",{}]"); }
            @Override public void onMessage(String m) { got.append(m); }
            @Override public void onClose(int c, String r, boolean remote) {}
            @Override public void onError(Exception e) {}
        };
        client.connectBlocking();

        long deadline = System.currentTimeMillis() + 2000;
        while (got.length() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        JSONArray reply = new JSONArray(got.toString());
        assertEquals(4, reply.getInt(0));
        assertEquals("InternalError", reply.getString(2));

        client.closeBlocking();
        cs.stop();
    }
}
