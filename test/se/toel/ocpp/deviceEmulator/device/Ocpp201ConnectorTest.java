/*
 * Verifies the ocpp201 Connector exposes the shared ConnectorIF.
 */
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.ocpp201.Connector;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class Ocpp201ConnectorTest {

    @Test
    public void test01_connectorImplementsConnectorIF() {
        assertTrue(ConnectorIF.class.isAssignableFrom(Connector.class));
    }
}
