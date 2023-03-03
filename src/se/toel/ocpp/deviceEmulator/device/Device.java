package se.toel.ocpp.deviceEmulator.device;

import java.io.File;
import se.toel.ocpp.deviceEmulator.device.impl.DeviceData;
import se.toel.ocpp.deviceEmulator.device.impl.FirmwareUpdate;
import se.toel.ocpp.deviceEmulator.device.impl.Connector;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.toel.ocpp.deviceEmulator.communication.CallbackIF;
import se.toel.ocpp.deviceEmulator.device.impl.LocalAuthorization;
import se.toel.ocpp.deviceEmulator.device.impl.AuthorizationCache;
import se.toel.ocpp.deviceEmulator.device.impl.Configuration;
import se.toel.ocpp.deviceEmulator.device.impl.LocalAuthorizationList;
import se.toel.ocpp.deviceEmulator.communication.Ocpp16;
import se.toel.ocpp.deviceEmulator.communication.OcppIF;
import se.toel.ocpp.deviceEmulator.events.Event;
import se.toel.ocpp.deviceEmulator.events.EventHandler;
import se.toel.ocpp.deviceEmulator.events.EventIds;
import se.toel.ocpp.deviceEmulator.utils.DateTimeUtil;
import se.toel.ocpp.deviceEmulator.utils.FTP;
import se.toel.util.Dev;
import se.toel.util.FileUtils;
import se.toel.util.StringUtil;

/**
 *
 * @author toel
 */
public class Device {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private final static Logger log = LoggerFactory.getLogger(Device.class);
    private final CallbackIF callback = new Callback();
    private final EventHandler eventMgr = EventHandler.getInstance();
    private final String deviceId;
    
    private boolean isConnected = false;
    private final boolean echo = true;
    private boolean busy = false;      
    private boolean bootNotificationSent = false;
    private int heartbeatInverval = 3600;
    
    // Tags
    private static final String
            CONNECT = "connect",
            AUTHORIZE = "Authorize",
            BOOT_NOTIFICATION = "BootNotification",
            HEARTBEAT = "Heartbeat",
            START_TRANSACTION = "StartTransaction",
            STOP_TRANSACTION = "StopTransaction",
            STATUS_NOTIFICATION = "StatusNotification",
            FIRMWARE_STATUS_NOTIFICATION = "FirmwareStatusNotification",
            METER_VALUES = "MeterValues",
            DIAGNOSTICS_STATUS_NOTIFICATION = "DiagnosticsStatusNotification",
            READY = "Ready";
    
    private final JSONObject jsonStatusAccepted = new JSONObject("{\"status\": \"Accepted\"}"); 
    private final JSONObject jsonStatusRejected = new JSONObject("{\"status\": \"Rejected\"}");
    private final JSONObject jsonStatusNotSupported = new JSONObject("{\"status\": \"NotSupported\"}");
    private final JSONObject jsonStatusNotImplemented = new JSONObject("{\"status\": \"NotImplemented\"}");
    private final JSONObject jsonStatusUnknown = new JSONObject("{\"status\": \"Unknown\"}");
    // private final DataMap coreProfile = new DataMap();
    private int tickCount = 0;
    private final List<Connector> connectors = new ArrayList<>();
    private FirmwareUpdate ongoingFirmwareUpdate = null;
    private final DeviceData deviceData;
    private final LocalAuthorizationList localAuth;
    private final AuthorizationCache authCache;
    private final Configuration config;
    Heart heart;
    private final OcppIF ocpp;
    private final Map<Long, String> scheduled = new ConcurrentHashMap<>();
    protected final Map<String, JSONArray> answers = new ConcurrentHashMap<>();
    
    // Auhorize stuff
    private static final String userId = "1";
    
    public boolean autoMeterValues = true;
    
    
     /***************************************************************************
     * Constructor
     **************************************************************************/
    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    public Device(String id, String url, String ocppVersion) {
        
        this.deviceId = id;
        
        String storePath = "data/"+id;
        FileUtils.checkPathExists(storePath);
        
        config = new Configuration();                           // Will set the default values
        config.load(storePath+"/configuration.dat");            // Overidde default with with previously used/set values
        
        int numberOfConnectors = config.getInt("NumberOfConnectors");
        createConnectors(numberOfConnectors, storePath);
        deviceData = new DeviceData();
        deviceData.load(storePath+"/deviceData.dat");
                
        // Load the autorization list and the cache
        localAuth = new LocalAuthorizationList(config);
        localAuth.load(storePath+"/localAuthorizationList.dat");
        
        authCache = new AuthorizationCache();
        authCache.load(storePath+"/authorizationCache.dat");
                
        heart = new Heart();
        
        switch (ocppVersion) {
            case "ocpp1.6": ocpp = new Ocpp16(id, url, callback); break;
            default:
                throw new RuntimeException("Ocpp version '"+ocppVersion+"' not supported");
        }
        
        scheduled.put(System.currentTimeMillis()+100, CONNECT);
        
    }
    
     /***************************************************************************
     * Public methods
     **************************************************************************/
    public boolean connect() {
        
        String authorizationKey = config.get("AuthorizationKey");        
        boolean connected = ocpp.connect(authorizationKey);
        if (connected) scheduleRemove(CONNECT);
        scheduled.put(System.currentTimeMillis()+3000, READY);
        return connected;
        
    }
    
    public boolean disconnect() {
        
        return ocpp.disconnect();
    }
    
    public boolean doReady() {
     
        for (Connector connector : connectors) {
            if (connector.getStatus().equals(Connector.STATUS_UNAVAILABLE)) {
                if (connector.getTransactionId()>0 && connector.getChargingCurrent()>0) {
                    connector.setStatus(Connector.STATUS_CHARGING);
                } else if (connector.getTransactionId()>0) {
                    connector.setStatus(Connector.STATUS_SUSPENDEDEVSE);
                } else {
                    connector.setStatus(Connector.STATUS_AVAILABLE);
                }
            }
        }
        
        return true;
        
    }
    
    public boolean doBootNotification() {
     
        // [2, "deviceId-here", "BootNotification", {"chargePointModel": "model", "chargePointVendor": "vendor", "firmwareVersion": "0.0.1"}]
        // [3,"01234567899876543210",{"status":"Accepted","currentTime":"2021-11-08T07:38:52.081","interval":3600}]
        
        boolean accepted;
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("chargePointModel", deviceData.getModel());
        req.put("chargePointVendor", deviceData.getVendor());
        req.put("firmwareVersion", deviceData.getFirmwareVersion());
        
        String msgId = Long.toHexString(now);
        
        ocpp.sendReq(msgId, BOOT_NOTIFICATION, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            scheduled.put(now+ThreadLocalRandom.current().nextInt(10000, 30000), BOOT_NOTIFICATION);
            return false;
        }
        if (json.getInt(0)!=3) return false;
        
        JSONObject obj = json.getJSONObject(2);
        
        accepted = "Accepted".equalsIgnoreCase(obj.optString("status"));
        
        if (accepted) {
            heartbeatInverval = obj.getInt("interval");
            scheduled.put(now+1000l, AUTHORIZE);
            scheduled.put(now+heartbeatInverval*1000l, HEARTBEAT);
            bootNotificationSent = true;
        } else {
            scheduled.put(now+heartbeatInverval*1000l, BOOT_NOTIFICATION);
        }
        
        // TODO
        // handle currentTime and intervall
        
        return accepted;
        
    }
    
    public boolean doStatusNotification(Connector connector) {
        
        // [2,"17d22b363ce","StatusNotification",{"connectorId":1,"errorCode":"NoError","status":"Charging"}]
        // [3,"17d22b363ce",{}]
        
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        
        if (connector==null) {
            // This is about the Charge Point main controller
            req.put("connectorId", 0);
            req.put("errorCode", "NoError");
            req.put("status", Connector.STATUS_AVAILABLE);
        } else {
            // and this about a specific connector
            req.put("connectorId", connector.getId());                                                                                                          // Required. The deviceId of the connector for which the status is reported. Id '0' (zero) is used if the status is for the Charge Point main controller.
            req.put("errorCode", connector.getErrorCode());                                                                                                     // Required. This contains the error code reported by the Charge Point.
            // req.put("info", null);                                                                                                                           // Optional. Additional free format information related to the error.
            req.put("status", connector.getStatus());                                                                                                           // Required. This contains the current status of the Charge Point.

            req.put("timestamp", DateTimeUtil.toIso8601(now));                                                                                               // Optional. The time for which the status is reported. If absent time of receipt of the message will be assumed.
            // req.put("timestamp", "2022-06-09T06:40:11+05:30");
            // req.put("vendorId", null);                                                                                                                       // Optional. This identifies the vendor-specific implementation.
            if (!connector.getVendorErrorCode().isEmpty()) req.put("vendorErrorCode", connector.getVendorErrorCode());                                          // Optional. This contains the vendor-specific error code.
        }
            
        String msgId = Long.toHexString(now);
        ocpp.sendReq(msgId, STATUS_NOTIFICATION, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            return false;
        }
        
        boolean ok = json.getInt(0)==3;
        
        return ok;
        
    }
    
    
    public boolean doAuthorize() {
     
        // 
        
        eventMgr.trigger(EventIds.AUTORIZING, deviceId, userId);
        
        boolean accepted;
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("idTag", userId);
        
        String msgId = Long.toHexString(now);
        
        ocpp.sendReq(msgId , AUTHORIZE, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) return false;
        if (json.getInt(0)!=3) return false;
        
        JSONObject obj = json.getJSONObject(2);
        JSONObject idTagInfo = obj.getJSONObject("idTagInfo");
        
        if (idTagInfo!= null && config.getBoolean("AuthorizationCacheEnabled")) {
            authCache.update(userId, idTagInfo);
        }
        
        accepted = "Accepted".equalsIgnoreCase(idTagInfo.getString("status"));
        
        if (accepted) {
            eventMgr.trigger(EventIds.AUTORIZED, deviceId, "  user "+userId+" is authorized");
        } else {
            eventMgr.trigger(EventIds.AUTORIZATION_FAILED, deviceId, "  user "+userId+" is NOT authorized");
        }
        
        return accepted;
        
    }
    
    public void start() {
        if (!heart.isAlive()) heart.start();
    }
    
    public void shutdown() {
        stop();
        disconnect();
        if (heart.isAlive()) heart.shutdown();
    }
    
    /***************************************************************************
     * Private methods
     **************************************************************************/
    
    // This is called every seconds
    private void tick() {
     
        tickCount++;
        
        // Performe the schedule actions
        if (!busy) {
            
            long now = System.currentTimeMillis();

            for (Map.Entry<Long, String> entry : scheduled.entrySet()) {
                if (entry.getKey()<now) {
                    scheduled.remove(entry.getKey());
                    String action = entry.getValue();
                    triggerAction(action);
                    break;  // Only take one at a time
                }
            }
            
        }
            
        
        if (isConnected && !busy) {

            // If connector status has changed, send 
            if (bootNotificationSent) {
                for (Connector connector : connectors) {
                     if (!connector.getStatus().equals(connector.lastStatus)) {
                         doStatusNotification(connector);
                         // If we were charging, send a last MeterValues
                         if (Connector.STATUS_CHARGING.equals(connector.lastStatus)) {
                             if (config.getMeterValueSampleInterval()>0) {
                                 doMeterValues(connector);
                             }
                         }
                         connector.lastStatus=connector.getStatus();
                    }
                }
            }
            
            // if there is an ongoing firmware update, handle it
            if (ongoingFirmwareUpdate!=null) {
                if (tickCount%5==0) emulateFirmwareUpdate();
            }

            // if charging send a MeterValues at the requested interval
            if (autoMeterValues) {
                int interval = config.getMeterValueSampleInterval();
                if (interval>0) {
                    if (tickCount%interval==0) {
                        for (Connector connector : connectors) {
                            if (Connector.STATUS_CHARGING.equals(connector.getStatus())) {
                                doMeterValues(connector);
                            }
                        }
                    }
                }
            }
            
            
            // TESTING!!!
            // for (int i=0; i<2; i++) {
            //     doStopTransaction(connectors.get(0));
            // }


        }
           
        // If a connector is charging, increase the meter
        for (Connector connector : connectors) {
            connector.tick();
        }
        
        // tick the AuthorizationCache once every 5s
        if (tickCount%5==0) authCache.tick();
        
        // If something has to be stored, do it now
        String storePath = "data/"+deviceId;
        if (config.hasChanged()) config.store(storePath+"/configuration.dat");
        if (deviceData.hasChanged()) deviceData.store(storePath+"/deviceData.dat");
        if (localAuth.hasChanged()) localAuth.store(storePath+"/localAuthorizationList.dat");
        if (authCache.hasChanged()) authCache.store(storePath+"/authorizationCache.dat");
        for (Connector connector : connectors) {
            if (connector.hasChanged()) connector.store(storePath+"/connector_"+connector.getId()+".dat");
        }
        
    }
    
    
    
    private void triggerAction(String action) {
     
        String command = action;
        JSONObject json = null;
        
        if (action.contains(" ")) {
            command = StringUtil.getWord(action, 1);
            json = new JSONObject(StringUtil.getWord(action, 2));
        }
        
        if (CONNECT.equals(action)) {
            connect();
            return;
        }
        
        if (isConnected) {
            switch (command) {

                case READY: doReady(); break;
                case BOOT_NOTIFICATION: doBootNotification(); break;
                case AUTHORIZE: doAuthorize(); break;
                case START_TRANSACTION: doStartTransaction(json); break;
                case STOP_TRANSACTION: doStopTransaction(json); break;
                case HEARTBEAT: doHeartBeat(); break;
                case METER_VALUES: doMeterValues(json); break;
                case DIAGNOSTICS_STATUS_NOTIFICATION: doDiagnosticsStatusNotification(json); break;
                case STATUS_NOTIFICATION: {
                        int connectorId = json.optInt("connectorId");
                        Connector connector = null;
                        if (connectorId>0) connector = connectors.get(connectorId);
                        doStatusNotification(connector);
                    }
                    break;

                default:
                    Dev.warn("triggerAction unsupported action: "+action);

            }
        }
        
    }
    
    
    public void doPlugIn(int connector) {
    
        Connector c = getConnector(connector);
        if (c!=null) {
            c.setPluggedIn(true);
            switch (c.getStatus()) {
                case Connector.STATUS_AVAILABLE: doStartTransaction(connector, userId); break;
            }
        }
        
    }
    
    public void doUnplug(int connector) {
     
        Connector c = getConnector(connector);
        if (c!=null) {
            c.setPluggedIn(false);
            if (doStopTransaction(c)) {
                c.setStatus(Connector.STATUS_AVAILABLE);
            }
        
        }
        
    }
    
    public void doPause(int connector) {
        
        Connector c = getConnector(connector);
        if (c!=null) {    
            c.setChargingCurrent(0);
            c.setStatus(Connector.STATUS_SUSPENDEDEVSE);
        }
        
    }
    
    public void doResume(int connector) {
        
        Connector c = getConnector(connector);
        if (c!=null) {    
            c.setChargingCurrent(config.getCurrentMaxAssignment());
            c.setStatus(Connector.STATUS_CHARGING);
        }
        
    }
    
    
    public void doReset(String msgId, JSONObject json) {
        
        String resetType = json.optString("type", "Soft");
        
        eventMgr.trigger(EventIds.RESETING, deviceId, "  performing a "+resetType+" reset...");
        
        // Send confirmation
        ocpp.sendConf(msgId, jsonStatusAccepted);
        
        Dev.sleep(500);
        
        stop();
        
        if ("Hard".equals(resetType)) {
            scheduled.clear();
        }
        
        ocpp.disconnect();
        
        // Simulate reboot
        for (Connector connector : connectors) {
            connector.reset();
        }
        
        bootNotificationSent = false;
        scheduled.put(System.currentTimeMillis()+5000l, CONNECT);
        
        eventMgr.trigger(EventIds.RESETED, deviceId, "  "+resetType+" reset done");
        
    }
    
    public void doSetChargingProfile(String msgId, JSONObject json) {
     
        // [2,"ddd29cba-febc-4eaa-a0e8-4219e28ac387","SetChargingProfile",{"connectorId":1,"csChargingProfiles":{"chargingProfileId":1,"transactionId":29,"stackLevel":0,"chargingProfilePurpose":"TxProfile","chargingProfileKind":"Relative","chargingSchedule":{"chargingRateUnit":"A","chargingSchedulePeriod":[{"startPeriod":0,"limit":0.0}]}}}]
        
        int connectorId = json.optInt("connectorId", 1);
        JSONObject csChargingProfiles = json.getJSONObject("csChargingProfiles");
        
        Connector connector = connectors.get(connectorId);
        boolean success = connector.setChargingProfile(csChargingProfiles);
        
        if (success) {
            ocpp.sendConf(msgId, jsonStatusAccepted);
        } else {    
            ocpp.sendConf(msgId, jsonStatusRejected);
        }
        
        
    }
    
    public void doGetDiagnostics(String msgId, JSONObject json) {
     
        // 
        
        String location = json.getString("location");                   // Required. This contains the location (directory) where the diagnostics file shall be uploaded to.
        int retries = json.optInt("retries");                           // Optional. This specifies how many times Charge Point must try to upload the diagnostics before giving up. If this field is not present, it is left to Charge Point to decide how many times it wants to retry.
        int retryInterval = json.optInt("retryInterval");               // Optional. The interval in seconds after which a retry may be attempted. If this field is not present, it is left to Charge Point to decide how long to wait between attempts.
        String startTime = json.optString("startTime");                 // Optional. This contains the date and time of the oldest logging information to include in the diagnostics.
        String stopTime = json.optString("stopTime");                   // Optional. This contains the date and time of the latest logging information to include in the diagnostics.
        
        JSONObject payload = new JSONObject();
        
        boolean uploaded = false;
        File file = new File("console.log");
        if (file.exists()) {
            File diagfile = new File("files/test_diagnostics.txt");
            FileUtils.copy(file, diagfile, true);
            if (FTP.sendFile(location, diagfile)) {
                payload.put("fileName", diagfile.getName());
                uploaded = true;
            }
            
        }
        
        ocpp.sendConf(msgId, payload);
        
        JSONObject notification = new JSONObject();
        notification.put("status", uploaded ? "Uploaded" : "UploadFailed");
        
        scheduled.put(System.currentTimeMillis()+1000, DIAGNOSTICS_STATUS_NOTIFICATION+" "+notification.toString());
        
        
    }
    
    
    public void doUpdateFirmware(String msgId, JSONObject json) {
             
        ongoingFirmwareUpdate = new FirmwareUpdate();
        
        ongoingFirmwareUpdate.setLocation(json.getString("location"));                   // Required. This contains a string containing a URI pointing to a location from which to retrieve the firmware.
        ongoingFirmwareUpdate.setRetries(json.optInt("retries"));                        // Optional. This specifies how many times Charge Point must try to upload the diagnostics before giving up. If this field is not present, it is left to Charge Point to decide how many times it wants to retry.
        ongoingFirmwareUpdate.setRetryInterval(json.optInt("retryInterval"));            // Optional. The interval in seconds after which a retry may be attempted. If this field is not present, it is left to Charge Point to decide how long to wait between attempts.
        ongoingFirmwareUpdate.setRetrieveDate(json.getString("retrieveDate"));           // Optional. This contains the date and time of the oldest logging information to include in the diagnostics.
        
        
        // Just acknowledge the request with empty payload, the status will be send using FirmwareStatusNotification in the backgound
        JSONObject payload = new JSONObject();
        ocpp.sendConf(msgId, payload);
        
    }
    
    
    public void doGetConfiguration(String msgId, JSONObject json) {
     
        JSONArray keys = json.optJSONArray("key");                             // Optional. List of keys for which the configuration value is requested.
                
        JSONObject payload = new JSONObject();
        JSONArray configurationKeys = new JSONArray();
        JSONArray unknownKey = new JSONArray();
                
        if (keys==null || keys.length()==0) {
            // Return all known
            for (Map.Entry<String, String> entry : config.entrySet()) {
                if ("AuthorizationKey".equals(entry.getKey())) continue;            // Hide this one
                JSONObject o = new JSONObject();
                o.put("key", entry.getKey());
                o.put("readonly", false);           // TODO: make this configurable
                o.put("value", entry.getValue());
                configurationKeys.put(o);
            }
        } else {
            // return the requested ones
            for (int i=0; i<keys.length(); i++) {
                String key = keys.getString(i);
                String value = config.get(key);
                if (value==null) {
                    unknownKey.put(key);
                } else {
                    JSONObject entry = new JSONObject();
                    entry.put("key", key);
                    entry.put("readonly", false);           // TODO: make this configurable
                    entry.put("value", value);
                    configurationKeys.put(entry);
                }
            }
        }
                
        if (configurationKeys.length()>0) payload.put("configurationKey", configurationKeys);
        if (unknownKey.length()>0) payload.put("unknownKey", unknownKey);
        
        ocpp.sendConf(msgId, payload);
        
        
    }
    
    
    public void doChangeConfiguration(String msgId, JSONObject json) {
        
        boolean success = true;
        String key = json.getString("key");
        String value = json.getString("value");
        
        // Special case for the echo, used for tests
        if (key.equals("echo")) {
            JSONObject ans = new JSONObject();
            ans.put("status", value);
            ocpp.sendConf(msgId, ans);
            if ("Accepted,RebootRequired".contains(value)) config.set(key, value);
            return;
        }
        
        // Special cases
        if (key.equals("AuthorizationKey") && !deviceData.getBasicAuthEnabled()) {
            ocpp.sendConf(msgId, jsonStatusNotSupported);
            return;
        }
        
        config.set(key, value);
        
        // Send confirmation (Accepted, Rejected, RebootRequired, NotSupported)
        if (success) {
            ocpp.sendConf(msgId, jsonStatusAccepted);
        } else {
            ocpp.sendConf(msgId, jsonStatusNotSupported);
        }
        
        
        
    }
    
    public boolean doStopTransaction(Connector connector) {
                        
        JSONArray transactionData = null;
        if (config.getMeterValueSampleInterval()>0) {
            transactionData = connector.getMeterValues();
        }
        
        int transactionId = connector.getTransactionId();
        if (transactionId==0) {                                                 // Abort if there is not known transaction Id
            connector.setReservationId(0);
            connector.setStatus(Connector.STATUS_FINISHING);
            return true;
        }
        
        // Send the request to the Central System
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("transactionId", transactionId);                                // Required. This contains the transaction-deviceId as received by the StartTransaction.conf.
        req.put("idTag", connector.getIdTag());                                 // Optional. This contains the identifier which requested to stop the charging. It is optional because a Charge Point may terminate charging without the presence of an idTag, e.g. in case of a reset. A Charge Point SHALL send the idTag if known.
        req.put("meterStop", connector.getMeterWh());                           // Required. This contains the meter value in Wh for the connector at end of the transaction.
        req.put("timestamp", DateTimeUtil.toIso8601(now));                      // Required. This contains the date and time on which the transaction is stopped.
        req.put("reason", connector.stopReason);                                // Optional. This contains the reason why the transaction was stopped. MAY only be omitted when the Reason is "Local".
        req.put("transactionData", transactionData);                            // Optional. This contains transaction usage details relevant for billing purposes.
        
        eventMgr.trigger(EventIds.TRANSACTION_STOPPING, deviceId, req.toString());
        
        String msgId = Long.toHexString(now);
        ocpp.sendReq(msgId, STOP_TRANSACTION, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            // Retry later
            scheduled.put(now+10000, STOP_TRANSACTION+" {\"transactionId\":"+transactionId+"}");
            return false;
        }
        
        boolean success = json.getInt(0)==3;
        
        if (success) {
            eventMgr.trigger(EventIds.TRANSACTION_STOPPED, deviceId, req.toString());
            JSONObject conf = json.getJSONObject(2);
            JSONObject idTagInfo = conf.optJSONObject("idTagInfo");                 // Optional. This contains information about authorization status, expiry and parent deviceId. It is optional, because a transaction may have been stopped without an identifier.
        
            if (idTagInfo!=null) {
                authCache.update(connector.getIdTag(), idTagInfo);
                String status = idTagInfo.getString("status");                      // Required. This contains whether the idTag has been accepted or not by the Central System.
                String parentIdTag = idTagInfo.optString("parentIdTag");            // Optional. This contains the parent-identifier.
                String expiryDate = idTagInfo.optString("expiryDate");              // Optional. This contains the date at which idTag should be removed from the Authorization Cache.
                success = "Accepted".equals(status);
            }
        } else {
            eventMgr.trigger(EventIds.TRANSACTION_STOP_FAILED, deviceId, req.toString());
        }
        
        if (success) {
            connector.setReservationId(0);
            connector.setTransactionId(0);
            connector.setStatus(Connector.STATUS_FINISHING);
        }
        
        return success;
        
    }
    
    public void doTriggerMessage(String msgId, JSONObject json) {
        
        String requestedMessage = json.getString("requestedMessage");
        int connectorId = json.optInt("connectorId");
        boolean success = true;
        
        JSONObject params = new JSONObject();
        params.put("connectorId", connectorId);
        
        // [2,"381f275e-9ff8-4960-bdf8-e7abc5a98bbc","TriggerMessage",{"requestedMessage":"StatusNotification"}]
    
        switch (requestedMessage) {
            case BOOT_NOTIFICATION: scheduled.put(System.currentTimeMillis(), BOOT_NOTIFICATION); break;
            case DIAGNOSTICS_STATUS_NOTIFICATION: scheduled.put(System.currentTimeMillis(), DIAGNOSTICS_STATUS_NOTIFICATION); break;
            case FIRMWARE_STATUS_NOTIFICATION: scheduled.put(System.currentTimeMillis(), FIRMWARE_STATUS_NOTIFICATION); break;
            case HEARTBEAT: scheduled.put(System.currentTimeMillis(), HEARTBEAT); break;
            case METER_VALUES: doMeterValues(connectorId); break;
            case STATUS_NOTIFICATION: scheduled.put(System.currentTimeMillis(), STATUS_NOTIFICATION+" "+params.toString()); break;
            default: 
                success = false;
                Dev.error("Unsupported requestedMessage: "+requestedMessage);
        }
        
        if (success) {
            ocpp.sendConf(msgId, jsonStatusAccepted);
        } else {
            ocpp.sendConf(msgId, jsonStatusNotImplemented);
        }
        
        
    }
    
    
    
    private void doClearChargingProfile(String msgId, JSONObject json) {
     
        // The criteria should be added to each other, that is AND
        // TODO thiis is not implemented right, fix me when its usefull
        
        boolean success = true;
        int id = json.optInt("id");
        // if (deviceId>0) Dev.info("   Need to clear the charging profile "+deviceId);
        
        int connectorId = json.optInt("connectorId");
        String chargingProfilePurpose = json.optString("chargingProfilePurpose", "TxProfile");
        
        int stackLevel = json.optInt("stackLevel");
        // if (stackLevel>0) Dev.info("   Need to clear the charging profiles with stack level "+stackLevel);
        
        if (connectorId>0) {
            Connector connector = connectors.get(connectorId);
            success = connector.clearChargingProfile(chargingProfilePurpose, id, stackLevel);
        } else {
            for (Connector connector : connectors) {
                success &= connector.clearChargingProfile(chargingProfilePurpose, id, stackLevel);
            }
        }
        
        // Send confirmation
        if (success) {
            ocpp.sendConf(msgId, jsonStatusAccepted);
        } else {
            ocpp.sendConf(msgId, jsonStatusUnknown);
        }
        
        
    }
    
    private void doRemoteStartTransaction(String msgId, JSONObject json) {
     
        // [2,"658aae13-b824-4eb7-a532-8d8c90f538c9","RemoteStartTransaction",{"connectorId":1,"idTag":"1"}].
        // [3,"a0bf5910-f3a9-4205-8c18-af27983a016c",{"status":"Accepted"}]
        
        // Dev.sleep(1000); // will cause timeout
        
        int connectorId = json.optInt("connectorId", 1);
        int idTag = json.optInt("idTag");
        
        if (config.getBoolean("AuthorizeRemoteTxRequests")) {
            ocpp.sendConf(msgId, jsonStatusNotSupported);
            return;
        }
        
        Connector connector = connectors.get(connectorId);
        if (connector.getTransactionId()>0) {
            // We have a transaction allready
            ocpp.sendConf(msgId, jsonStatusRejected);
        } else if (!connector.isPluggedIn()) {
            // Not plugged in!
            ocpp.sendConf(msgId, jsonStatusRejected);
        } else {
                    
            connector.setIdTag(String.valueOf(idTag));
            
            // Send confirmation
            ocpp.sendConf(msgId, jsonStatusAccepted);

            // trigger next action
            scheduled.put(System.currentTimeMillis(), START_TRANSACTION+" "+json.toString());
        }
        
    }
    
    private void doRemoteStopTransaction(String msgId, JSONObject json) {
     
        // [2,"9294431d-27e8-4aba-bc4d-5f443970cb98","RemoteStopTransaction",{"transactionId":67}]
        
        // sendConf(msgId, jsonStatusRejected);
        
        // This should be handled by the connectors
        int transactionId = json.getInt("transactionId");
        Connector connector = getConnectorWithTransactionId(transactionId);
        if (connector==null) {
            ocpp.sendConf(msgId, jsonStatusRejected);
        } else {
            ocpp.sendConf(msgId, jsonStatusAccepted);
            scheduled.put(System.currentTimeMillis(), STOP_TRANSACTION+" "+json.toString());
        }
        
    }
    
    
    public boolean doStartTransaction(int connectorId, String idTag) {
                
        Connector connector = connectors.get(connectorId);
        
        if (!connector.isPluggedIn()) {
            eventMgr.trigger(EventIds.INFO, deviceId, "   Not plugged in: denying StartTransaction "+connectorId);
            return false;
        }
        
        eventMgr.trigger(EventIds.TRANSACTION_STARTING, deviceId, "   Starting transaction for connector "+connectorId);
        
        // Send the request to the Central System
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("connectorId", connectorId);
        req.put("idTag", idTag);
        req.put("meterStart", connector.getMeterWh());
        if (connector.getReservationId()>0) req.put("reservationId", connector.getReservationId());
        req.put("timestamp", DateTimeUtil.toIso8601(now));
        
        String msgId = Long.toHexString(now);
        ocpp.sendReq(msgId, START_TRANSACTION, req);
        
        // Get the conf and process it
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            // No answer? wait 10s and try again
            // JSONObject params = new JSONObject();
            // params.put("connectorId", connectorId);
            // params.put("idTag", idTag);
            // scheduled.put(now+10000, START_TRANSACTION+" "+params.toString());
            return false;
        }
        if (json.getInt(0)!=3) {
            return false;
        }
        
        JSONObject conf = json.getJSONObject(2);
        
        connector.setTransactionId(conf.getInt("transactionId"));
        JSONObject idTagInfo = conf.getJSONObject("idTagInfo");
        String expiryDate = idTagInfo.optString("expiryDate");
        String parentIdTag = idTagInfo.optString("parentIdTag");
        String status = idTagInfo.getString("status");
        
        authCache.update(idTag, idTagInfo);
        
        if (expiryDate!=null) {
            LocalAuthorization.getInstance().setIdTagExpiryDate(idTag, expiryDate);
        }
        
        boolean accepted = "Accepted".equals(status);
        if (!accepted) {
            Dev.hereIam("");
        }
        
        switch (status) {
            case "Accepted": eventMgr.trigger(EventIds.TRANSACTION_STARTED, deviceId, "   Transaction "+connector.getTransactionId()+" started for connector "+connectorId); break;
            case "Blocked": eventMgr.trigger(EventIds.TRANSACTION_START_FAILED, deviceId, "   Identifier has been blocked. Not allowed for charging."); break;
            case "Expired": eventMgr.trigger(EventIds.TRANSACTION_START_FAILED, deviceId, "   Identifier has expired. Not allowed for charging."); break;
            case "Invalid": eventMgr.trigger(EventIds.TRANSACTION_START_FAILED, deviceId, "   Identifier is unknown. Not allowed for charging."); break;
            case "ConcurrentTx": eventMgr.trigger(EventIds.TRANSACTION_START_FAILED, deviceId, "   Identifier is already involved in another transaction and multiple transactions are not allowed."); break;
        }
        
        if (accepted) {
            if (connector.getChargingCurrent()>0) {
                connector.setStatus(Connector.STATUS_CHARGING);
            } else {
                connector.setStatus(Connector.STATUS_SUSPENDEDEVSE);
            }
        }
                        
        return accepted;
        
    }
    
    private boolean doStartTransaction(JSONObject params) {
                
        // Get the params to use for the StartTransaction.req
        int connectorId = params.optInt("connectorId", 1);
        String idTag = params.getString("idTag");
        
        return doStartTransaction(connectorId, idTag);
        
    }
    
    public boolean doStopTransaction(int transactionId) {
        
        Connector connector = getConnectorWithTransactionId(transactionId);
        
        if (connector==null) {
            return false;      // No connector with this transaction
        }
        
        return doStopTransaction(connector);
        
    }
        
    
    private boolean doStopTransaction(JSONObject params) {
                
        // Get the params to use for the StopTransaction.req
        int transactionId = params.getInt("transactionId");
        return doStopTransaction(transactionId);
        
    }
        
    
    
    public void doMeterValues(int connectorId) {
     
        Connector connector = connectors.get(connectorId);
        doMeterValues(connector);
        
    }
    
    
    
    private void doMeterValues(Connector connector) {
     
        JSONObject json = new JSONObject();
        json.put("connectorId", connector.getId());
        json.put("transactionId", connector.getTransactionId());
        json.put("meterValue", connector.getMeterValues());
        
        scheduled.put(System.currentTimeMillis()+1, METER_VALUES+" "+json.toString());
        
    }
    
    private void doMeterValues(JSONObject json) {
     
        eventMgr.trigger(EventIds.METER_VALUES_BEFORE, deviceId, json.toString());
        JSONArray ans = doSimpleRequest(METER_VALUES, json);
        if (ans.getInt(0)==3) {
            eventMgr.trigger(EventIds.METER_VALUES_AFTER, deviceId, json.toString());
        } else {
            eventMgr.trigger(EventIds.METER_VALUES_FAILED, deviceId, json.toString());
        }
        
    }
    
    private void doDiagnosticsStatusNotification(JSONObject json) {
        
        doSimpleRequest(DIAGNOSTICS_STATUS_NOTIFICATION, json);        
        
    }
    
    private JSONArray doSimpleRequest(String type, JSONObject json) {
        
        long now = System.currentTimeMillis(); 
        String msgId = Long.toHexString(now);
        
        ocpp.sendReq(msgId, type, json);
        
        return waitForAnswer(msgId);
        
    }
    
    
    private void doHeartBeat() {
        
        eventMgr.trigger(EventIds.HEARTBEAT_BEFORE, deviceId, "   Sending heartbeat");
        
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();        
        String msgId = Long.toHexString(now);
        
        ocpp.sendReq(msgId, HEARTBEAT, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null || json.getInt(0)!=3) {
            eventMgr.trigger(EventIds.HEARTBEAT_FAILED, deviceId, "");
            return;
        }
        
        JSONObject obj = json.getJSONObject(2);
        String currentTime = obj.getString("currentTime");
        
        long timestamp = DateTimeUtil.fromIso8601(currentTime);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String datetime = df.format(new Date(timestamp));
        
        eventMgr.trigger(EventIds.HEARTBEAT_AFTER, deviceId, "   Central system current time is "+datetime);
        
        // And reshedule
        scheduled.put(now+heartbeatInverval*1000l, HEARTBEAT);
        
    }
    
    
    public void stop() {
                
        for (Connector connector : connectors) {
            if (connector.getTransactionId()>0) doStopTransaction(connector);
        }
        
    }
    
    
    public Connector getConnector(int connectorId) {
     
        return connectors.get(connectorId);
        
    }
    
    
    private void scheduleRemove(String command) {
                
        for (Map.Entry<Long, String> entry : scheduled.entrySet()) {
            if (entry.getValue().equals(command)) {
                scheduled.remove(entry.getKey());
            }
        }
    }
    
    
    
    private Connector getConnectorWithTransactionId(int transactionId) {
        
        Connector connector = null;
        
        for (Connector c : connectors) {
            if (c.getTransactionId()==transactionId) {
                connector = c;
                break;
            }
        }
        
        return connector;
    }
    
    
    private void createConnectors(int count, String storePath) {
        
        for (int i=0; i<=count; i++) {
            connectors.add(new Connector(i, storePath));
        }
        
    }
        
    
    /** wait for the answer for max 5s */
    private JSONArray waitForAnswer(String msgId) {
        
        int timeout = 500;
        JSONArray answer = answers.get(msgId);
        
        while (--timeout>0 && answer==null) {
            Dev.sleep(10);
            answer = answers.get(msgId);
        }
        
        if (answer!=null) answers.remove(msgId);
        
        return answer;
        
    }
    
    private void emulateFirmwareUpdate() {
        
        if (ongoingFirmwareUpdate==null) return;
        
        busy = true;
        
        try {
        
            switch (ongoingFirmwareUpdate.getStatus()) {
                case FirmwareUpdate.FIRMWARE_STATUS_DOWNLOADING:
                    ongoingFirmwareUpdate.downloadFirmware();
                    break;
                case FirmwareUpdate.FIRMWARE_STATUS_DOWNLOADED:
                    ongoingFirmwareUpdate.startInstallFirmware();
                    break;
                case FirmwareUpdate.FIRMWARE_STATUS_INSTALLING:
                    ongoingFirmwareUpdate.setStatus(FirmwareUpdate.FIRMWARE_STATUS_INSTALLED);
                    break;
                case FirmwareUpdate.FIRMWARE_STATUS_INSTALLED:
                    deviceData.setFirmwareVersion(ongoingFirmwareUpdate.getVersion());
                    bootNotificationSent = false;
                    stop();
                    disconnect();
                    scheduled.put(System.currentTimeMillis()+5000l, CONNECT); 
                    ongoingFirmwareUpdate = null;
                    return;

            }

            if (ongoingFirmwareUpdate.getStatus().equals(FirmwareUpdate.FIRMWARE_STATUS_IDLE)) {
                String now = DateTimeUtil.toIso8601(System.currentTimeMillis());
                if (ongoingFirmwareUpdate.getRetrieveDate().compareTo(now)<0) {
                    ongoingFirmwareUpdate.setStatus(FirmwareUpdate.FIRMWARE_STATUS_DOWNLOADING);
                }
            }

            if (ongoingFirmwareUpdate.getStatusHasChanged()) {

                long now = System.currentTimeMillis();
                JSONObject req = new JSONObject();        
                String msgId = Long.toHexString(now);
                req.put("status", ongoingFirmwareUpdate.getStatus());
                ocpp.sendReq(msgId, FIRMWARE_STATUS_NOTIFICATION, req);

                JSONArray json = waitForAnswer(msgId);
                // Should get an empty answer here
            }
            
        } finally {
            busy = false;
        }
        
    }
    
    private class Heart extends Thread {
        
        private boolean running = true;

        @Override
        public void run() {
            long lastTick = System.currentTimeMillis();
            while (running) {
                Dev.sleep(10);
                if (lastTick+1000<System.currentTimeMillis()) {
                    tick();
                    lastTick = System.currentTimeMillis();
                }
            }
            System.out.println("+++++ exiting Heart.run() +++++");
        }

        public void shutdown() {
            running = false;
        }
        
        
    }
    
    private class Callback implements CallbackIF {

        @Override
        public void onMessage(String message) {
            
            eventMgr.trigger(new Event(EventIds.OCPP_RECEIVED, deviceId, message));
                        
            JSONArray ja = new JSONArray(message);
            int type = ja.getInt(0);
            
            switch (type) {
                case 2: 
                    processRequest(ja); 
                    break;
                case 3: 
                    String msgId = ja.getString(1);
                    answers.put(msgId, ja);
                    break;
                case 4:
                    msgId = ja.getString(1);
                    answers.put(msgId, ja);
                    // Dev.error(message);
                    break;
            }
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            Event e = new Event(EventIds.WS_ON_OPEN, deviceId, " onOpen ", null, handshake);
            eventMgr.trigger(e);
            isConnected = true;
            if (!bootNotificationSent) scheduled.put(System.currentTimeMillis(), BOOT_NOTIFICATION);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Event e = new Event(EventIds.WS_ON_CLOSE, deviceId, "onClose - code:"+code+" reason:"+reason+" remote:"+remote);
            eventMgr.trigger(e);
            isConnected = false;
            busy=false;
            scheduled.put(System.currentTimeMillis()+10000, CONNECT);            
        }

        @Override
        public void onError(Exception ex) {
            Event e = new Event(EventIds.WS_ON_ERROR, deviceId, "onError - "+ex.getClass().getName()+": "+ex.getMessage());
            eventMgr.trigger(e);
        }
        
        
    }
    
    
    /**
     * process the OCPP REQ received from the backend
     * @param ja 
     */
    private void processRequest(JSONArray ja) {
        
        busy = true;
        String msgId = ja.getString(1);
        String command = ja.getString(2);
        
        try {
        
            switch (command) {

                case "ChangeConfiguration": doChangeConfiguration(msgId, ja.getJSONObject(3)); break;
                case "ClearChargingProfile": doClearChargingProfile(msgId, ja.getJSONObject(3)); break;
                case "RemoteStartTransaction": doRemoteStartTransaction(msgId, ja.getJSONObject(3)); break;
                case "RemoteStopTransaction": doRemoteStopTransaction(msgId, ja.getJSONObject(3)); break;
                case "Reset": doReset(msgId, ja.getJSONObject(3)); break;
                case "SetChargingProfile": doSetChargingProfile(msgId, ja.getJSONObject(3)); break;
                case "GetDiagnostics": doGetDiagnostics(msgId, ja.getJSONObject(3)); break;
                case "UpdateFirmware": doUpdateFirmware(msgId, ja.getJSONObject(3)); break;
                case "GetConfiguration": doGetConfiguration(msgId, ja.getJSONObject(3)); break;
                case "TriggerMessage": doTriggerMessage(msgId, ja.getJSONObject(3)); break;

                default:
                    Dev.warn("processRequest unsupported command: "+command);

            }
            
        } finally {
            busy = false;
        }
        
    }

}
