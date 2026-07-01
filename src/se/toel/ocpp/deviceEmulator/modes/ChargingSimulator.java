/*
 * Charging simulation mode: loops two vehicles through realistic sessions on connector 1 against the
 * real backend, to exercise backend GUI, permissions and transactions. OCPP 1.6 only.
 */
package se.toel.ocpp.deviceEmulator.modes;

import java.util.function.DoubleSupplier;
import se.toel.ocpp.deviceEmulator.device.DeviceFactory;
import se.toel.ocpp.deviceEmulator.device.SimDeviceIF;
import se.toel.ocpp.deviceEmulator.events.EventHandler;
import se.toel.ocpp.deviceEmulator.events.EventIF;
import se.toel.ocpp.deviceEmulator.events.EventIds;
import se.toel.ocpp.deviceEmulator.events.EventListenerIF;
import se.toel.ocpp.deviceEmulator.modes.sim.ChargingSession;
import se.toel.ocpp.deviceEmulator.modes.sim.SimDurations;
import se.toel.ocpp.deviceEmulator.modes.sim.SystemSimClock;
import se.toel.ocpp.deviceEmulator.modes.sim.Vehicle;
import se.toel.util.Dev;

public class ChargingSimulator implements ApplicationModeIF, EventListenerIF {

    private static final int CONNECTOR = 1;
    private static final Object LOG_LOCK = new Object();    // serialize logging across the Heart/WebSocket/session threads

    private volatile boolean running = true;
    private final SimDeviceIF device;
    private final SimDurations durations = SimDurations.production();
    private final SystemSimClock clock = new SystemSimClock();
    private final DoubleSupplier rng = Math::random;

    private final Vehicle[] fleet = new Vehicle[] {
        new Vehicle("SE-EV-0001", 58, 11, 0.80, 0.15, 0.40),    // big battery: flat CC, ends on timer
        new Vehicle("SE-EV-0002", 40, 22, 0.80, 0.970, 0.978)   // tops off: reaches full -> SuspendedEV
    };

    public ChargingSimulator(String deviceId, String url) {
        Dev.debugEnabled(true);
        Dev.setWriteToFile(true);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() { shutdown(); }
        });
        device = DeviceFactory.createSimDevice(deviceId, url);
        device.setChargePointIdentity("lokato.se", "vendor", "1.0.0");
    }

    @Override
    public void start() {
        EventHandler.getInstance().subscribe(null, this);
        try {
            device.start();
            while (running) {
                for (Vehicle vehicle : fleet) {
                    if (!running) break;
                    if (!waitUntilReady()) break;               // not connected/booted yet (or stopping)
                    log("=== Session: vehicle " + vehicle.getId() + " ===");
                    try {
                        ChargingSession.Result result =
                            new ChargingSession(device, CONNECTOR, vehicle, durations, clock, rng).run();
                        log("=== Session " + vehicle.getId() + " ended: " + result + " ===");
                    } catch (RuntimeException e) {
                        log("   Session " + vehicle.getId() + " aborted (connection lost?): " + e);
                    }
                    if (running) clock.sleep(durations.pauseMs());
                }
            }
            device.shutdown();
        } catch (InterruptedException e) {
            running = false;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            running = false;
        }
    }

    /** Block until the device is connected to the backend and booted (or we stop). Returns isReady(). */
    private boolean waitUntilReady() throws InterruptedException {
        int waited = 0;
        while (running && !device.isReady()) {
            if (waited % 10000 == 0) log("   Waiting for backend connection + BootNotification...");
            clock.sleep(1000);
            waited += 1000;
        }
        return device.isReady();
    }

    public void shutdown() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** All mode logging goes through here so concurrent threads (Heart, WebSocket, session) don't interleave. */
    private void log(String line) {
        synchronized (LOG_LOCK) {
            Dev.info(line);
        }
    }

    @Override
    public void eventTriggered(EventIF event) {
        switch (event.getId()) {
            case EventIds.METER_VALUES_BEFORE:
            case EventIds.METER_VALUES_AFTER:
                break;                                          // hide the duplicate raw MeterValues dumps (10060/10061)
            case EventIds.OCPP_RECEIVED: log("R: " + event.getMessage()); break;
            case EventIds.OCPP_SENDING:  log("S: " + event.getMessage()); break;
            case EventIds.INFO:          log(event.getMessage()); break;
            default: log(event.getId() + " - " + event.getMessage());
        }
    }
}
