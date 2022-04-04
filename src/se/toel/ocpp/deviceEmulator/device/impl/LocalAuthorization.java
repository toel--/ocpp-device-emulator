/*
 * LocalAuthorization with local cache for offline behaviour
 ' see Unknown Offline Authorization in OCPP 1.6 standard

The Authorization Cache and Local Authorization List are distinct logical data structures. Identifiers known in the Local Authorization List SHALL NOT be added to the Authorization Cache.
Where both Authorization Cache and Local Authorization List are supported, a Charge Point SHALL treat Local Authorization List entries as having priority over Authorization Cache entries for the same identifiers.

 */
package se.toel.ocpp.deviceEmulator.device.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import se.toel.collection.DataMap;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class LocalAuthorization extends DataMap {
    
    private static final LocalAuthorization instance = new LocalAuthorization();
    private Map<String, Long> expiration = new ConcurrentHashMap<>();
    
    private LocalAuthorization() {
    
        
    
    }
    
    public static LocalAuthorization getInstance() {
        return instance;
    }
    
    public boolean hasIdTag(String idTag) {
        return data.keySet().contains(idTag);
    }
    
    public void setIdTagExpiryDate(String idTag, String expiryDate) {
        
        Dev.moreToDoIn("Implement setIdTagExpiryDate");
        
    }
    
    
}
