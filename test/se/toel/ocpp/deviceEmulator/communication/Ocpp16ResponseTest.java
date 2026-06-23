/*
 * Verifies the protocol response method is named sendResponse (not sendConf).
 */
package se.toel.ocpp.deviceEmulator.communication;

import org.json.JSONObject;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.communication.ocpp16.Ocpp16;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class Ocpp16ResponseTest {

    @Test
    public void test01_ocppIfExposesSendResponse() throws Exception {
        assertNotNull(OcppIF.class.getMethod("sendResponse", String.class, JSONObject.class));
    }

    @Test
    public void test02_ocppIfHasNoSendConf() {
        try {
            OcppIF.class.getMethod("sendConf", String.class, JSONObject.class);
            fail("sendConf should have been renamed to sendResponse");
        } catch (NoSuchMethodException expected) {
            // good
        }
    }

    @Test
    public void test03_ocpp16ImplementsSendResponse() throws Exception {
        assertNotNull(Ocpp16.class.getMethod("sendResponse", String.class, JSONObject.class));
    }
}
