/*
 * Verifies the ocpp201 protocol class plugs into the shared OcppIF.
 */
package se.toel.ocpp.deviceEmulator.communication;

import org.json.JSONObject;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.communication.ocpp201.Ocpp201;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class Ocpp201ProtocolTest {

    @Test
    public void test01_ocpp201ImplementsOcppIF() {
        assertTrue(OcppIF.class.isAssignableFrom(Ocpp201.class));
    }

    @Test
    public void test02_ocpp201HasSendResponse() throws Exception {
        assertNotNull(Ocpp201.class.getMethod("sendResponse", String.class, JSONObject.class));
    }
}
