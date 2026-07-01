/*
 * Simple device emulator with output to the console
 */

package se.toel.ocpp.deviceEmulator.modes;

import java.util.Iterator;
import org.java_websocket.handshake.ServerHandshake;
import se.toel.ocpp.deviceEmulator.device.DeviceFactory;
import se.toel.ocpp.deviceEmulator.device.DeviceIF;
import se.toel.ocpp.deviceEmulator.events.EventHandler;
import se.toel.ocpp.deviceEmulator.events.EventIF;
import se.toel.ocpp.deviceEmulator.events.EventIds;
import se.toel.ocpp.deviceEmulator.events.EventListenerIF;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class DeviceEmulator implements ApplicationModeIF, EventListenerIF {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/    
    private boolean running = true;
    private DeviceIF device = null;

     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public DeviceEmulator(String deviceId, String url, String ocppVersion) {
        
        Dev.debugEnabled(true);
        Dev.setWriteToFile(true);
                
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook( new Thread (  )  {  
            @Override
            public void run() {
                shutdown();
            }
        }); 
        
        device = DeviceFactory.create(deviceId, url, ocppVersion);
        
    }
    
    @Override
    public void start() {
        // listen to all events
        EventHandler.getInstance().subscribe(null, this);
        
        try {
            
            device.start();
            
            while (running) {
                Thread.sleep(100);
            }
                        
            device.shutdown();            
        
        } catch (Exception e) {
            e.printStackTrace(System.err);
            running = false;
        }
    }
           
    
    public void shutdown() {
        
        running = false;
        
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    

    @Override
    public void eventTriggered(EventIF event) {
        
        switch (event.getId()) {
            case EventIds.METER_VALUES_BEFORE:
            case EventIds.METER_VALUES_AFTER:
                break;                                          // hide the duplicate raw MeterValues dumps (10060/10061)
            case EventIds.OCPP_RECEIVED: Dev.info("R: "+event.getMessage()); break;
            case EventIds.OCPP_SENDING:  Dev.info("S: "+event.getMessage()); break;
            case EventIds.INFO:          Dev.info(event.getMessage()); break;
            case EventIds.WS_ON_OPEN: 
                ServerHandshake handshake = (ServerHandshake)event.getObject();
                Iterator<String> it = handshake.iterateHttpFields();
                while (it.hasNext()) {
                    String key = it.next();
                    Dev.info("   "+key+" "+handshake.getFieldValue(key));
                }
                break;
            default: Dev.info(event.getId()+" - "+event.getMessage());
        }
        /*
        JSONObject json = new JSONObject(event.getMessage());
        String operation = json.getString("op");
        */
        
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/

}
