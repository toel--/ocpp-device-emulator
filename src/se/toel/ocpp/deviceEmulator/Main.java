/*
 * Using https://github.com/TooTallNate/Java-WebSocket
 */
package se.toel.ocpp.deviceEmulator;

import java.net.URISyntaxException;
import se.toel.ocpp.deviceEmulator.modes.ApplicationModeIF;
import se.toel.ocpp.deviceEmulator.modes.DeviceEmulator;
import se.toel.ocpp.deviceEmulator.modes.DeviceTester;
import se.toel.ocpp.deviceEmulator.modes.DeviceWatcher;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class Main {
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws URISyntaxException {
                
        ApplicationModeIF mode;
        
        if (args.length<1) showSyntaxAndExit();
        String deviceId = args[0];
        
        switch (deviceId) {
            case "watcher":
                mode = new DeviceWatcher();
                break;
            case "tester":
                mode = new DeviceTester();
                break;
            default:
                if (args.length<3) showSyntaxAndExit();
                String url = args[1];
                String ocppVersion = args[2];
                mode = new DeviceEmulator(deviceId, url, ocppVersion); 
            
        }
        
        mode.start();
                
        while (mode.isRunning()) Dev.sleep(1000);
        
        System.exit(0);
        
    }
    
    
    
    
    
    private static void showSyntaxAndExit() {
        
        System.out.println("OCPP Device emulator");
        System.out.println("Toel Hartmann 2022");
        System.out.println();
        System.out.println("  Syntax:");
        System.out.println("     java -jar OcppDeviceEmulator.jar [deviceId] [url] [ocppVersion]");
        System.out.println("  where");
        System.out.println("     [deviceId] is the device id to use");
        System.out.println("                'watcher' to enable the watcher mode, see documentation for configuration");
        System.out.println("     [url] is the url of the backend server endpoint");
        System.out.println("     [ocppVersion] the OCPP version to use (only ocpp1.6 is supported for now)");
        System.exit(1);
        
    }
    
    
    
}