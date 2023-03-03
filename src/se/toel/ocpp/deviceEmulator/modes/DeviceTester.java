/*
 * Simple device emulator with output to the console
 */

package se.toel.ocpp.deviceEmulator.modes;

import se.toel.ocpp.deviceEmulator.device.Device;
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
public class DeviceTester implements ApplicationModeIF, EventListenerIF {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/    
    private boolean running = true;
    private Device device = null;
    IniFile ini = new IniFile("data/deviceTester.ini");
    private int successCount, failureCount;
    int transactionId;
    String nextAction = null;
    
     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public DeviceTester() {
        
        Dev.debugEnabled(true);
        Dev.setWriteToFile(true);
                
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook( new Thread (  )  {  
            @Override
            public void run() {
                shutdown();
            }
        }); 
        
        String scenario = ini.getValue("tester", "scenario", "test01");
        String url = ini.getValue("tester", "url", "ws://127.0.0.1:8080/ocpp");
        String deviceId = ini.getValue(scenario, "device-id", "01234567899876543210");
        String ocppVersion = ini.getValue(scenario, "ocpp-version", "ocpp1.6");
        
        device = new Device(deviceId, url, ocppVersion);
        device.autoMeterValues = false;
        
        ini.save();
        
    }
    
    @Override
    public void start() {
        // listen to all events
        EventHandler.getInstance().subscribe(null, this);
        
        int connector = 1;
        
        try {
            
            device.start();
            
            int n=60;
            
            Thread.sleep(5000);     // Give 5s to the device to start
            device.doStartTransaction(1, "1");
            
            while (running) {
                for (int i=0; i<1000; i++) {
                    Thread.sleep(1);
                    if (nextAction!=null) {
                        String action = nextAction;
                        nextAction = null;
                        switch (action) {
                            case "doStartTransaction":
                                // Dev.sleep(10);
                                System.out.println("");
                                Dev.info("Starting transaction...");                                
                                device.doStartTransaction(connector, "1");
                                break;
                            case "doStopTransaction":
                                Dev.info("Stopping transaction "+transactionId);    
                                if (!device.doStopTransaction(transactionId)) ;
                                break;
                            case "doMeterValues":
                                Dev.info("Sending metervalues...");
                                device.doMeterValues(connector);
                                break;
                            default:
                                Dev.moreToDoIn("");
                            
                        }
                    }
                    
                }
                running = --n>=1;
                
                if (n%5==0) showStats();
                
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
        
        // System.out.println("transactionId: "+transactionId);
        
        switch (event.getId()) {
            case EventIds.INFO:
            case EventIds.CONNECTING:
            case EventIds.CONNECTED:
                Dev.info(event.getId()+" - "+event.getMessage());
                break;
            case EventIds.CONNECTION_FAILED:
                Dev.info(event.getId()+" - "+event.getMessage());
                shutdown();
            case EventIds.OCPP_SENDING:
            case EventIds.OCPP_RECEIVED:
                // Dev.info(event.getId()+" - "+event.getMessage());
                break;
            case EventIds.TRANSACTION_STARTED:                
                transactionId = device.getConnector(1).getTransactionId();
                Dev.info("Transaction "+transactionId+" started");
                if (transactionId>0) successCount++; else failureCount++;
                nextAction = "doMeterValues";
                break;
            case EventIds.TRANSACTION_START_FAILED:
                Dev.warn("Transaction could not be started: "+event.getMessage());
                nextAction = "doStartTransaction";
                break;
            case EventIds.METER_VALUES_AFTER:
                // Dev.info("meter values sent");
                successCount++;
                nextAction = "doStopTransaction"; // to test the db transaction bug, nextAction = "doStartTransaction";
                break;
            case EventIds.METER_VALUES_FAILED:
                Dev.warn("MeterValues FAILED");
                failureCount++;
                nextAction = "doStopTransaction";
                break;
            case EventIds.TRANSACTION_STOPPED:
                nextAction = "doStartTransaction";
                break;
             
                
        }
        
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/
    
    
    private void showStats() {
     
        System.out.println("[STATS] - success: "+successCount+", failure: "+failureCount);
        
        
    }
    

}
