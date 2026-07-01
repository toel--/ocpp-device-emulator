/*
 * Test double for SimDeviceIF: scripts the StartTransaction outcome and the meter (Wh) the session
 * reads, and records every drive call so tests can assert the session's behaviour.
 */
package se.toel.ocpp.deviceEmulator.modes.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import se.toel.ocpp.deviceEmulator.device.ConnectorIF;
import se.toel.ocpp.deviceEmulator.device.SimDeviceIF;
import se.toel.ocpp.deviceEmulator.device.StartTransactionOutcome;

public class FakeSimDevice implements SimDeviceIF {

    public StartTransactionOutcome startOutcome = new StartTransactionOutcome(true, "Accepted", 42);
    public double meterWh = 0;                          // fallback reading when the queue is empty
    public final Deque<Double> meterReadings = new ArrayDeque<>();   // scripted Wh: 1st read = meterStart, then per loop
    public final List<String> events = new ArrayList<>();
    public Double lastVehicleAmps = null;
    public String lastStopReason = null;
    public Double effectiveOverride = null;            // when set, getConnector().getChargingCurrent() returns this (simulate a backend cap)

    @Override public void setChargePointIdentity(String m, String v, String f) { events.add("identity"); }
    @Override public boolean isReady() { return true; }
    @Override public void plugIn(int c) { events.add("plugIn"); }
    @Override public void unplug(int c) { events.add("unplug"); }
    @Override public StartTransactionOutcome startTransaction(int c, String idTag) { events.add("start:"+idTag); return startOutcome; }
    @Override public void stopTransaction(int txId, String reason) { events.add("stop:"+reason); lastStopReason = reason; }
    @Override public void setVehicleChargingCurrent(int c, double amps) { lastVehicleAmps = amps; }
    @Override public void setConnectorStatus(int c, String status) { events.add("status:"+status); }
    @Override public double getMeterWh(int c) { return meterReadings.isEmpty() ? meterWh : meterReadings.poll(); }

    @Override public ConnectorIF getConnector(int c) {
        return new ConnectorIF() {
            public int getTransactionId() { return startOutcome.transactionId(); }
            public String getStatus() { return null; }
            public double getChargingCurrent() {
                if (effectiveOverride!=null) return effectiveOverride;
                return lastVehicleAmps==null ? 0 : lastVehicleAmps;
            }
        };
    }

    // Unused DeviceIF members for these tests:
    @Override public void start() {}
    @Override public void shutdown() {}
    @Override public void setAutoMeterValues(boolean b) {}
    @Override public boolean doStartTransaction(int c, String idTag) { return true; }
    @Override public boolean doStopTransaction(int txId) { return true; }
    @Override public void doMeterValues(int c) {}
}
