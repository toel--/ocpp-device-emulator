/*
 * Verifies Connector exposes the version-agnostic ConnectorIF.
 */
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.impl.Connector;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class ConnectorIFTest {

    @Test
    public void test01_connectorImplementsConnectorIF() {
        assertTrue("Connector should implement ConnectorIF",
                ConnectorIF.class.isAssignableFrom(Connector.class));
    }

    @Test
    public void test02_connectorIfExposesGetTransactionId() throws Exception {
        assertEquals(int.class, ConnectorIF.class.getMethod("getTransactionId").getReturnType());
    }
}
