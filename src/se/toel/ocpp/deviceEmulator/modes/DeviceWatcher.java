/*
 * Simple device emulator with output to the console
 */

package se.toel.ocpp.deviceEmulator.modes;

import java.util.HashMap;
import java.util.Map;
import se.toel.ocpp.deviceEmulator.device.Device;
import se.toel.ocpp.deviceEmulator.events.Event;
import se.toel.ocpp.deviceEmulator.events.EventHandler;
import se.toel.ocpp.deviceEmulator.events.EventIF;
import se.toel.ocpp.deviceEmulator.events.EventIds;
import se.toel.ocpp.deviceEmulator.events.EventListenerIF;
import se.toel.util.Dev;
import se.toel.util.IniFile;

/**
 *
 * @author toel
 */
public class DeviceWatcher implements ApplicationModeIF, EventListenerIF {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/    
    private boolean running = true;
    private final Map<String, Device> devices = new HashMap<>();
    IniFile ini = new IniFile("data/deviceWatcher.ini");

     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public DeviceWatcher() {
        
        Dev.debugEnabled(true);
        Dev.setWriteToFile(true);
                
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook( new Thread (  )  {  
            @Override
            public void run() {
                shutdown();
            }
        });
        
        // listen to events
        EventHandler.getInstance().subscribe(EventIds.WS_ON_OPEN, this);
        EventHandler.getInstance().subscribe(EventIds.WS_ON_CLOSE, this);
        EventHandler.getInstance().subscribe(EventIds.WS_ON_ERROR, this);
        EventHandler.getInstance().subscribe(EventIds.OCPP_SENDING, this);
        EventHandler.getInstance().subscribe(EventIds.OCPP_RECEIVED, this);
        
    }
           
    @Override
    public void start() {
        
        Dev.info("Starting the device watcher...");
        
        int n = 1;
        while (initDevice(n)) {
            n++;
            Dev.sleep(500);
        }
        
        ini.save();
        
        Dev.info("Done.");
        
        if (devices.isEmpty()) shutdown();
        
    }
    
    public void shutdown() {
        
        for (Device device : devices.values()) {
            device.shutdown();
        }
        
        running = false;
        
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    

    @Override
    public void eventTriggered(EventIF event) {
        
        Event e = (Event)event;
        
        switch (e.getId()) {
            case EventIds.OCPP_RECEIVED: Dev.log(e.getDeviceId()+" R: "+e.getMessage()); break;
            case EventIds.OCPP_SENDING:  Dev.log(e.getDeviceId()+" S: "+e.getMessage()); break;
            default: Dev.log(e.getDeviceId()+" "+e.getMessage());;
        }
        /*
        JSONObject json = new JSONObject(event.getMessage());
        String operation = json.getString("op");
        */
        
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/

    private boolean initDevice(int id) {
        
        String section = "watcher_"+id;
        if (!ini.sectionExists(section)) return false;
        
        String url = ini.getValue(section, "url", "");
        boolean enabled = "true".equalsIgnoreCase(ini.getValue(section, "enabled", "false"));
        
        if (enabled) {
            try {

                String deviceId = ini.getValue(section, "device-id", "");
                String ocppVersion = ini.getValue(section, "ocpp-version", "ocpp1.6");
                Device device = new Device(deviceId, url, ocppVersion);
                devices.put(deviceId, device);
                Dev.info("   starting device "+deviceId+" using "+url);
                device.start();

            } catch (Exception e) {
                e.printStackTrace(System.err);                
            }
        }
        
        return true;
                
    }
    
}
