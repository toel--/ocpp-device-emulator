/*
 * Verifies the ocpp16 Connector emits only the configured, supported MeterValues
 * measurands (and reports which measurands it cannot produce).
 */
package se.toel.ocpp.deviceEmulator.device;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.ocpp16.Connector;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class Ocpp16MeterValuesTest {

    private static final String STORE = System.getProperty("java.io.tmpdir") + "/ocpp16-metervalues-test";

    @BeforeClass
    public static void prepareStore() {
        File dir = new File(STORE);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.mkdirs();
    }

    @Test
    public void energyOnlyProducesASingleSample() {
        List<String> m = measurands(new Connector(1, STORE).getMeterValues("Energy.Active.Import.Register"));
        assertEquals(1, m.size());
        assertEquals("Energy.Active.Import.Register", m.get(0));
    }

    @Test
    public void currentAndVoltageAreReportedPerPhase() {
        JSONArray samples = samples(new Connector(2, STORE).getMeterValues("Current.Import"));
        assertEquals(3, samples.length());
        assertEquals("L1", samples.getJSONObject(0).getString("phase"));
        assertEquals("L2", samples.getJSONObject(1).getString("phase"));
        assertEquals("L3", samples.getJSONObject(2).getString("phase"));
        assertEquals("A", samples.getJSONObject(0).getString("unit"));
    }

    @Test
    public void fullListEmitsEachRequestedMeasurandOnce() {
        // Energy(1) + Power(1) + Voltage(3 phases) + Current(3 phases) = 8 samples, in request order.
        List<String> m = measurands(new Connector(3, STORE).getMeterValues(
                "Energy.Active.Import.Register,Power.Active.Import,Voltage,Current.Import"));
        assertEquals(8, m.size());
        assertEquals(1, java.util.Collections.frequency(m, "Power.Active.Import"));
        assertEquals(3, java.util.Collections.frequency(m, "Voltage"));
        assertEquals(3, java.util.Collections.frequency(m, "Current.Import"));
        assertFalse("we never emulate SoC", m.contains("SoC"));
        assertEquals("Energy.Active.Import.Register", m.get(0));
    }

    @Test
    public void unsupportedMeasurandIsSilentlySkipped() {
        assertEquals(0, samples(new Connector(4, STORE).getMeterValues("SoC")).length());
    }

    @Test
    public void areMeasurandsSupportedAcceptsKnownAndRejectsUnknown() {
        assertTrue(Connector.areMeasurandsSupported("Energy.Active.Import.Register,Power.Active.Import,Voltage,Current.Import"));
        assertTrue("whitespace is tolerated", Connector.areMeasurandsSupported(" Voltage , Current.Import "));
        assertTrue("an empty list is trivially supported", Connector.areMeasurandsSupported(""));
        assertFalse("SoC is not supported", Connector.areMeasurandsSupported("Energy.Active.Import.Register,SoC"));
    }

    /***************************************************************************
     * Helpers
     **************************************************************************/

    private JSONArray samples(JSONArray meterValues) {
        return meterValues.getJSONObject(0).getJSONArray("sampledValue");
    }

    private List<String> measurands(JSONArray meterValues) {
        JSONArray samples = samples(meterValues);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < samples.length(); i++) {
            list.add(samples.getJSONObject(i).getString("measurand"));
        }
        return list;
    }
}
