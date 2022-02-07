/*
 * Using https://github.com/TooTallNate/Java-WebSocket
 */
package se.toel.ocpp16.deviceEmulator;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import se.toel.event.EventIF;
import se.toel.event.EventListenerIF;
import se.toel.ocpp16.deviceEmulator.device.Device;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class Main implements EventListenerIF {

    private boolean running = true;
    private Map<String, Device> devices = new HashMap<>();
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws URISyntaxException {
                
        if (args.length<2) showSyntaxAndExit();
        
        String deviceId = args[0];
        String url = args[1];
        
        Dev.debugEnabled(true);
        Dev.setWriteToFile(true);
        
        Main main = new Main(deviceId, url);
        System.exit(0);
        
    }
    
    
    public Main(String deviceId, String url) {
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook( new Thread (  )  {  
            @Override
            public void run() {
                shutdown();
            }
        }); 
        
        
        try {
        
            Device device = new Device(deviceId, url);
            devices.put(deviceId, device);
            device.start();
            
            while (running) {
                Thread.sleep(100);
            }
                        
            device.shutdown();            
        
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    
    }
    
    
    
    
    public void shutdown() {
        
        running = false;
        
    }
    

    @Override
    public void eventTriggered(EventIF event) {
        
        JSONObject json = new JSONObject(event.getMessage());
        String operation = json.getString("op");
        
        
        
    }
    
    
    private static void showSyntaxAndExit() {
        
        System.out.println("OCPP Device emulator");
        System.out.println("Toel Hartmann 2022");
        System.out.println();
        System.out.println("  Syntax:");
        System.out.println("     java -jar OcppDeviceEmulator.jar [deviceId] [url]");
        System.out.println("  where");
        System.out.println("     [deviceId] is the device id to use");
        System.out.println("     [url] is the url of the backend server endpoint");
        System.exit(1);
        
    }
    
    
    
}