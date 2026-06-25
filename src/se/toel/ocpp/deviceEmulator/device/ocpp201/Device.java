package se.toel.ocpp.deviceEmulator.device.ocpp201;

import java.io.File;
import se.toel.ocpp.deviceEmulator.device.DeviceIF;
import se.toel.ocpp.deviceEmulator.device.impl.DeviceData;
import se.toel.ocpp.deviceEmulator.device.impl.FirmwareUpdate;
import se.toel.ocpp.deviceEmulator.device.ocpp201.Connector;
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
import se.toel.ocpp.deviceEmulator.device.ocpp201.Configuration;
import se.toel.ocpp.deviceEmulator.communication.ocpp201.Ocpp201;
import se.toel.ocpp.deviceEmulator.events.Event;
import se.toel.ocpp.deviceEmulator.events.EventHandler;
import se.toel.ocpp.deviceEmulator.events.EventIds;
import se.toel.ocpp.deviceEmulator.utils.DateTimeUtil;
import se.toel.ocpp.deviceEmulator.utils.FTP;
import se.toel.ocpp.deviceEmulator.utils.MessageIdGenerator;
import se.toel.util.Dev;
import se.toel.util.FileUtils;
import se.toel.util.StringUtil;
import se.toel.ocpp.deviceEmulator.communication.OcppIF;
import se.toel.ocpp.deviceEmulator.device.ocpp201.Variables;

/**
 *
 * @author toel
 */
public class Device implements DeviceIF {

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
    private boolean bootNotificationAccepted = false;
    private boolean startTransactionOnPluggin = true;
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
    
    private int tickCount = 0;
    private final List<Connector> connectors = new ArrayList<>();
    private FirmwareUpdate ongoingFirmwareUpdate = null;
    private final DeviceData deviceData;
    private final Configuration config;     // OCPP 1.6
    private final Variables variables;
    Heart heart;
    private final OcppIF protocol;
    private final Map<Long, String> scheduled = new ConcurrentHashMap<>();
    protected final Map<String, JSONArray> answers = new ConcurrentHashMap<>();
    private final MessageIdGenerator msgIds = new MessageIdGenerator();
    
    // Auhorize stuff
    private static final String userId = "1";
    
    public boolean autoMeterValues = true;
    
    // Bug emulation
    boolean doNotAnswerToChangeConfigurationLightIntensity = false;
    boolean doTakeTimeToAnswerChangeConfigurationLightIntensity = true;
    
    
    
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
        
        variables = new Variables();
        variables.load(storePath+"/variables.dat");
        
        variables.set("SecurityCtrlr", "Identity", "Actual", id);
        variables.set("SecurityCtrlr", "SecurityProfile", "Actual", "2");
        
        int numberOfConnectors = config.getInt("NumberOfConnectors");
        createConnectors(numberOfConnectors, storePath);
        deviceData = new DeviceData();
        deviceData.load(storePath+"/deviceData.dat");
                
        heart = new Heart();
        
        switch (ocppVersion) {
            case "ocpp2.0.1": this.protocol = new Ocpp201(id, url, callback); break;
            default:
                throw new RuntimeException("Ocpp version '"+ocppVersion+"' not supported");
        }
        
        scheduleAdd(100, CONNECT);
        
    }
    
     /***************************************************************************
     * Public methods
     **************************************************************************/
    public void setAutoMeterValues(boolean autoMeterValues) {
        this.autoMeterValues = autoMeterValues;
    }

    public boolean connect() {
        
        String basicAuthPassword = variables.getBasicAuthPassword();
        boolean connected = protocol.connect(basicAuthPassword);
        if (connected) scheduleRemove(CONNECT);
        scheduleAdd(3000, READY);
        return connected;
        
    }
    
    public boolean disconnect() {
        
        return protocol.disconnect();
    }
    
    public boolean doReady() {
     
        /*
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
        }*/
        
        return true;
        
    }
    
    public boolean doBootNotification() {
     
        // [2, "18c25722295", "BootNotification", { "reason":"PowerUp", "chargingStation":{"model":"Njord Go","vendorName":"vendor","serialNumber":"gridit000001","firmwareVersion":"0.0.1"}} ]
        // [3, "18c25722295", {"status":"Accepted", "currentTime":"2023-12-01T12:54:46.170Z", "interval":3600}]
        
        boolean accepted;
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("reason", "PowerUp");
        
        JSONObject cs = new JSONObject();
        cs.put("model", deviceData.getModel());
        cs.put("vendorName", deviceData.getVendor());
        cs.put("firmwareVersion", deviceData.getFirmwareVersion());
        req.put("chargingStation", cs);
        
        String msgId = msgIds.next();
        
        protocol.sendReq(msgId, BOOT_NOTIFICATION, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            scheduled.put(now+ThreadLocalRandom.current().nextInt(10000, 30000), BOOT_NOTIFICATION);
            return false;
        }
        if (json.getInt(0)!=3) return false;
        
        JSONObject obj = json.getJSONObject(2);
        
        accepted = "Accepted".equalsIgnoreCase(obj.optString("status"));
        heartbeatInverval = obj.optInt("interval", 60);
        
        if (accepted) {
            // scheduled.put(now+1000l, AUTHORIZE);
            scheduled.put(now+heartbeatInverval*1000l, HEARTBEAT);
            bootNotificationAccepted = true;
        } else {
            scheduled.put(now+heartbeatInverval*1000l, BOOT_NOTIFICATION);
        }
                
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
            
        String msgId = msgIds.next();
        protocol.sendReq(msgId, STATUS_NOTIFICATION, req);
        
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
        
        String msgId = msgIds.next();
        
        protocol.sendReq(msgId , AUTHORIZE, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) return false;
        if (json.getInt(0)!=3) return false;
        
        JSONObject obj = json.getJSONObject(2);
        JSONObject idTagInfo = obj.getJSONObject("idTagInfo");
        
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

            /* If connector status has changed, send StatusNotification
            if (bootNotificationAccepted) {
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
            */
            
            // if there is an ongoing firmware update, handle it
            if (ongoingFirmwareUpdate!=null) {
                if (tickCount%5==0) emulateFirmwareUpdate();
            }

            // if charging send a MeterValues at the requested interval
            if (autoMeterValues && bootNotificationAccepted) {
                int interval = config.getMeterValueSampleInterval();
                if (interval>0) {
                    if (tickCount%interval==0) {
                        for (Connector connector : connectors) {
                            // if (Connector.STATUS_CHARGING.equals(connector.getStatus())) {
                                doMeterValues(connector);
                            // }
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
        
        // If something has to be stored, do it now
        String storePath = "data/"+deviceId;
        if (config.hasChanged()) config.store(storePath+"/configuration.dat");
        if (variables.hasChanged()) variables.store(storePath+"/variables.dat");
        if (deviceData.hasChanged()) deviceData.store(storePath+"/deviceData.dat");
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
                case Connector.STATUS_AVAILABLE: if (startTransactionOnPluggin) doStartTransaction(connector, "open_tag"); break;
            }
        }
        
    }
    
    public boolean doUnplug(int connector) {
     
        boolean success = false;
        Connector c = getConnector(connector);
        if (c!=null) {
            if (doStopTransaction(c)) {
                c.setStatus(Connector.STATUS_AVAILABLE);                
            }
            c.setPluggedIn(false);
            success = true;
        }
        return success;
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
        protocol.sendResponse(msgId, jsonStatusAccepted);
        
        Dev.sleep(500);
        
        stop();
        
        if ("Hard".equals(resetType)) {
            scheduled.clear();
        }
        
        protocol.disconnect();
        
        // Simulate reboot
        for (Connector connector : connectors) {
            connector.reset();
        }
        
        bootNotificationAccepted = false;
        scheduleAdd(5000l, CONNECT);
        
        eventMgr.trigger(EventIds.RESETED, deviceId, "  "+resetType+" reset done");
        
    }
    
    public void doSetChargingProfile(String msgId, JSONObject json) {
     
        // [2,"ddd29cba-febc-4eaa-a0e8-4219e28ac387","SetChargingProfile",{"connectorId":1,"chargingProfiles":{"chargingProfileId":1,"transactionId":29,"stackLevel":0,"chargingProfilePurpose":"TxProfile","chargingProfileKind":"Relative","chargingSchedule":{"chargingRateUnit":"A","chargingSchedulePeriod":[{"startPeriod":0,"limit":0.0}]}}}]
        
        int connectorId = json.optInt("evseId", 0);
        JSONObject chargingProfiles = json.getJSONObject("chargingProfile");
        
        Connector connector = connectors.get(connectorId);
        boolean success = connector.setChargingProfile(chargingProfiles);
        
        if (success) {
            protocol.sendResponse(msgId, jsonStatusAccepted);
        } else {    
            protocol.sendResponse(msgId, jsonStatusRejected);
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
            File diagfile = new File("data/"+deviceId+"/test_diagnostics.txt");
            FileUtils.copy(file, diagfile, true);
            if (FTP.sendFile(location, diagfile)) {
                payload.put("fileName", diagfile.getName());
                uploaded = true;
            }
            
        }
        
        protocol.sendResponse(msgId, payload);
        
        JSONObject notification = new JSONObject();
        notification.put("status", uploaded ? "Uploaded" : "UploadFailed");
        
        scheduleAdd(1000l, DIAGNOSTICS_STATUS_NOTIFICATION+" "+notification.toString());
        
        
    }
    
    
    public void doUpdateFirmware(String msgId, JSONObject json) {
             
        ongoingFirmwareUpdate = new FirmwareUpdate();
        
        ongoingFirmwareUpdate.setLocation(json.getString("location"));                   // Required. This contains a string containing a URI pointing to a location from which to retrieve the firmware.
        ongoingFirmwareUpdate.setRetries(json.optInt("retries"));                        // Optional. This specifies how many times Charge Point must try to upload the diagnostics before giving up. If this field is not present, it is left to Charge Point to decide how many times it wants to retry.
        ongoingFirmwareUpdate.setRetryInterval(json.optInt("retryInterval"));            // Optional. The interval in seconds after which a retry may be attempted. If this field is not present, it is left to Charge Point to decide how long to wait between attempts.
        ongoingFirmwareUpdate.setRetrieveDate(json.getString("retrieveDate"));           // Optional. This contains the date and time of the oldest logging information to include in the diagnostics.
        
        
        // Just acknowledge the request with empty payload, the status will be send using FirmwareStatusNotification in the backgound
        JSONObject payload = new JSONObject();
        protocol.sendResponse(msgId, payload);
        
    }
    
    /** OCPP 1.6 */
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
        
        protocol.sendResponse(msgId, payload);
        
        
    }
    
    
    public void doChangeConfiguration(String msgId, JSONObject json) {
        
        boolean success = true;
        String key = json.getString("key");
        String value = json.getString("value");
        
        // Special case for bug emulation
        if (doNotAnswerToChangeConfigurationLightIntensity && key.endsWith("LightIntensity")) {
            return;
        }
        
        if (doTakeTimeToAnswerChangeConfigurationLightIntensity) {
            Dev.sleep((int)(Math.random()*2000));
        }
        
        // Special case for the echo, used for tests
        if (key.equals("echo")) {
            JSONObject ans = new JSONObject();
            ans.put("status", value);
            protocol.sendResponse(msgId, ans);
            if ("Accepted,RebootRequired".contains(value)) config.set(key, value);
            return;
        }
        
        // Special cases
        if (key.equals("AuthorizationKey") && !deviceData.getBasicAuthEnabled()) {
            protocol.sendResponse(msgId, jsonStatusNotSupported);
            return;
        }
        
        config.set(key, value);
        
        // Send confirmation (Accepted, Rejected, RebootRequired, NotSupported)
        if (success) {
            protocol.sendResponse(msgId, jsonStatusAccepted);
        } else {
            protocol.sendResponse(msgId, jsonStatusNotSupported);
        }
        
    }
    
    
    public void doGetVariables(String msgId, JSONObject json) {
        
        JSONObject result = new JSONObject();
        JSONArray rs = new JSONArray();
        result.put("getVariableResult", rs);
        
        JSONArray arr = json.getJSONArray("getVariableData");
        for (int i=0; i<arr.length(); i++) {
            
            JSONObject variableData = arr.getJSONObject(0);
            String component = variableData.getJSONObject("component").getString("name");
            String variable = variableData.getJSONObject("variable").getString("name");
            String type = variableData.optString("attributeType", "Actual");
            String value = variables.get(component, variable, type);
            
            JSONObject r = new JSONObject();
            r.put("attributeType", type);
            r.put("component", new JSONObject("{\"name\":\""+component+"\"}"));
            r.put("variable", new JSONObject("{\"name\":\""+variable+"\"}"));
            r.put("attributeStatus", "Accepted");
            r.put("attributeValue", value);
            rs.put(r);
            
        }
        
        protocol.sendResponse(msgId, result);
        
    }
    
    
    public void doSetVariables(String msgId, JSONObject json) {
        
        JSONObject result = new JSONObject();
        JSONArray rs = new JSONArray();
        result.put("setVariableResult", rs);
        
        JSONArray arr = json.getJSONArray("setVariableData");
        for (int i=0; i<arr.length(); i++) {
            
            JSONObject variableData = arr.getJSONObject(0);
            String component = variableData.getJSONObject("component").getString("name");
            String variable = variableData.getJSONObject("variable").getString("name");
            String type = variableData.optString("attributeType", "Actual");
            String value = variableData.getString("attributeValue");
            variables.set(component, variable, type, value);
            
            JSONObject r = new JSONObject();
            r.put("attributeType", type);
            r.put("component", new JSONObject("{\"name\":\""+component+"\"}"));
            r.put("variable", new JSONObject("{\"name\":\""+variable+"\"}"));
            // if (variable.equals("GriditMaxAcPow")) {
            //     r.put("attributeStatus", "Rejected");
            //    r.put("attributeStatusInfo", new JSONObject("{\"reasonCode\":\"UNSUPPORTED\",\"additionalInfo\":\"Yes, thats a problem indeeed\"}"));
            //} else {
                r.put("attributeStatus", "Accepted");
            //}
            rs.put(r);
            
        }
        
        protocol.sendResponse(msgId, result);
        
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
            if (config.autoPlugin()) {
                connector.setPluggedIn(false);
                connector.setStatus(Connector.STATUS_AVAILABLE);
            }
            return true;
        }
        
        // Send the request to the Central System
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("transactionId", transactionId);                                // Required. This contains the transaction-deviceId as received by the StartTransaction.conf.
        req.put("idTag", connector.getIdTag());                                 // Optional. This contains the identifier which requested to stop the charging. It is optional because a Charge Point may terminate charging without the presence of an idTag, e.g. in case of a reset. A Charge Point SHALL send the idTag if known.
        req.put("meterStop", Math.floor(connector.getMeterWh()));               // Required. This contains the meter value in Wh for the connector at end of the transaction. (integer)
        req.put("timestamp", DateTimeUtil.toIso8601(now));                      // Required. This contains the date and time on which the transaction is stopped.
        req.put("reason", connector.stopReason);                                // Optional. This contains the reason why the transaction was stopped. MAY only be omitted when the Reason is "Local".
        req.put("transactionData", transactionData);                            // Optional. This contains transaction usage details relevant for billing purposes.
        
        eventMgr.trigger(EventIds.TRANSACTION_STOPPING, deviceId, req.toString());
        
        String msgId = msgIds.next();
        protocol.sendReq(msgId, STOP_TRANSACTION, req);
        
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
            if (config.autoPlugin()) {
                Dev.sleep(1);
                connector.setPluggedIn(false);
                connector.setStatus(Connector.STATUS_AVAILABLE);
            }
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
            case BOOT_NOTIFICATION: scheduleAdd(0, BOOT_NOTIFICATION); break;
            case DIAGNOSTICS_STATUS_NOTIFICATION: scheduleAdd(0, DIAGNOSTICS_STATUS_NOTIFICATION); break;
            case FIRMWARE_STATUS_NOTIFICATION: scheduleAdd(0, FIRMWARE_STATUS_NOTIFICATION); break;
            case HEARTBEAT: scheduleAdd(0,  HEARTBEAT); break;
            case METER_VALUES: doMeterValues(connectorId); break;
            case STATUS_NOTIFICATION: scheduleAdd(0, STATUS_NOTIFICATION+" "+params.toString()); break;
            default: 
                success = false;
                Dev.error("Unsupported requestedMessage: "+requestedMessage);
        }
        
        if (success) {
            protocol.sendResponse(msgId, jsonStatusAccepted);
        } else {
            protocol.sendResponse(msgId, jsonStatusNotImplemented);
        }
        
        
    }
    
    public void doUnlockConnector(String msgId, JSONObject json) {
        
        int connectorId = json.optInt("connectorId");
        String status;
        
        Connector c = getConnector(connectorId);
        if (c==null) {
            status = "NotSupported";
        } else {
            switch (c.getStatus()) {
                case Connector.STATUS_FAULTED:
                case Connector.STATUS_AVAILABLE: 
                    status = "Unlocked"; 
                    break;
                case Connector.STATUS_SUSPENDEDEV:
                case Connector.STATUS_SUSPENDEDEVSE:
                case Connector.STATUS_CHARGING:
                case Connector.STATUS_RESERVED:
                case Connector.STATUS_PREPARING:
                case Connector.STATUS_FINISHING:
                    if (doUnplug(connectorId)) {
                        status = "Unlocked";
                    } else {
                        status = "UnlockFailed";
                    }
                    break;
                default:
                    status = "NotSupported";
            }
        }
        
        JSONObject payload = new JSONObject();
        payload.put("status", status);
        protocol.sendResponse(msgId,  payload );        
        
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
            protocol.sendResponse(msgId, jsonStatusAccepted);
        } else {
            protocol.sendResponse(msgId, jsonStatusUnknown);
        }
        
        
    }
    
    private void doRemoteStartTransaction(String msgId, JSONObject json) {
     
        // [2,"658aae13-b824-4eb7-a532-8d8c90f538c9","RemoteStartTransaction",{"connectorId":1,"idTag":"1"}].
        // [3,"a0bf5910-f3a9-4205-8c18-af27983a016c",{"status":"Accepted"}]
        
        // Dev.sleep(1000); // will cause timeout
        
        int connectorId = json.optInt("connectorId", 1);
        int idTag = json.optInt("idTag");
        
        if (config.getBoolean("AuthorizeRemoteTxRequests")) {
            protocol.sendResponse(msgId, jsonStatusNotSupported);
            return;
        }
        
        Connector connector = connectors.get(connectorId);
        if (connector.getTransactionId()>0) {
            // We have a transaction allready
            protocol.sendResponse(msgId, jsonStatusRejected);
            return;
        }
        
        if (!connector.isPluggedIn()) {
            if (config.autoPlugin()) {
                connector.setIdTag(String.valueOf(idTag));
                connector.setPluggedIn(true);
            } else {
                // Not plugged in!
                protocol.sendResponse(msgId, jsonStatusRejected);
                return;
            }
        }
                    
        connector.setIdTag(String.valueOf(idTag));

        // Send confirmation
        protocol.sendResponse(msgId, jsonStatusAccepted);

        // trigger next action
        scheduleAdd(0, START_TRANSACTION+" "+json.toString());
        
        
    }
    
    private void doRemoteStopTransaction(String msgId, JSONObject json) {
     
        // [2,"9294431d-27e8-4aba-bc4d-5f443970cb98","RemoteStopTransaction",{"transactionId":67}]
        
        // sendResponse(msgId, jsonStatusRejected);
        
        // This should be handled by the connectors
        int transactionId = json.getInt("transactionId");
        Connector connector = getConnectorWithTransactionId(transactionId);
        if (connector==null) {
            protocol.sendResponse(msgId, jsonStatusRejected);
        } else {
            protocol.sendResponse(msgId, jsonStatusAccepted);
            scheduleAdd(0, STOP_TRANSACTION+" "+json.toString());
        }
        
    }
    
    
    public boolean doStartTransaction(int connectorId, String idTag) {
                
        Connector connector = connectors.get(connectorId);
        
        if (!connector.isPluggedIn()) {
            if (config.autoPlugin()) {
                doPlugIn(connectorId);
            } else {
                eventMgr.trigger(EventIds.INFO, deviceId, "   Not plugged in: denying StartTransaction "+connectorId);
                return false;
            }
        }
        
        eventMgr.trigger(EventIds.TRANSACTION_STARTING, deviceId, "   Starting transaction for connector "+connectorId);
        
        // Send the request to the Central System
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("connectorId", connectorId);
        req.put("idTag", idTag);
        req.put("meterStart", Math.round(connector.getMeterWh()));                                                                                              // meterStart should be integer
        if (connector.getReservationId()>0) req.put("reservationId", connector.getReservationId());
        req.put("timestamp", DateTimeUtil.toIso8601(now));
        
        String msgId = msgIds.next();
        protocol.sendReq(msgId, START_TRANSACTION, req);
        
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
                        
        switch (status) {
            case "Accepted": eventMgr.trigger(EventIds.TRANSACTION_STARTED, deviceId, "   Transaction "+connector.getTransactionId()+" started for connector "+connectorId); break;
            case "Blocked": eventMgr.trigger(EventIds.TRANSACTION_STARTED, deviceId, "   Transaction "+connector.getTransactionId()+" started for connector "+connectorId+" BUT Identifier has been blocked. Not allowed for charging."); break;
            case "Expired": eventMgr.trigger(EventIds.TRANSACTION_STARTED, deviceId, "   Transaction "+connector.getTransactionId()+" started for connector "+connectorId+" BUT Identifier has expired. Not allowed for charging."); break;
            case "Invalid": eventMgr.trigger(EventIds.TRANSACTION_STARTED, deviceId, "   Transaction "+connector.getTransactionId()+" started for connector "+connectorId+" BUT Identifier is unknown. Not allowed for charging."); break;
            case "ConcurrentTx": eventMgr.trigger(EventIds.TRANSACTION_STARTED, deviceId, "   Transaction "+connector.getTransactionId()+" started for connector "+connectorId+" BUT Identifier is already involved in another transaction and multiple transactions are not allowed."); break;
        }
        
        if (connector.getChargingCurrent()>0) {
            connector.setStatus(Connector.STATUS_CHARGING);
        } else {
            connector.setStatus(Connector.STATUS_SUSPENDEDEVSE);
        }
                        
        return true;
        
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
        json.put("evseId", connector.getId());
        
        int transId = connector.getTransactionId();
        if (transId>0) json.put("transactionId", connector.getTransactionId());
        json.put("meterValue", connector.getMeterValues());
        
        doMeterValues(json);
        
        // scheduleAdd(1, METER_VALUES+" "+json.toString());
        
    }
    
    private void doMeterValues(JSONObject json) {
     
        eventMgr.trigger(EventIds.METER_VALUES_BEFORE, deviceId, json.toString());
        JSONArray ans = doSimpleRequest(METER_VALUES, json);
        if (ans!=null && ans.getInt(0)==3) {
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
        String msgId = msgIds.next();
        
        protocol.sendReq(msgId, type, json);
        
        return waitForAnswer(msgId);
        
    }
    
    
    private void doHeartBeat() {
        
        eventMgr.trigger(EventIds.HEARTBEAT_BEFORE, deviceId, "   Sending heartbeat");
        
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();        
        String msgId = msgIds.next();
        
        protocol.sendReq(msgId, HEARTBEAT, req);
        
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
    
    
    private void scheduleAdd(long millis, String command) {
        scheduled.put(System.currentTimeMillis()+millis, command);
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
                    bootNotificationAccepted = false;
                    stop();
                    disconnect();
                    scheduleAdd(5000l, CONNECT); 
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
                String msgId = msgIds.next();
                req.put("status", ongoingFirmwareUpdate.getStatus());
                protocol.sendReq(msgId, FIRMWARE_STATUS_NOTIFICATION, req);

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
                Dev.sleep(1);
                long now = System.currentTimeMillis();
                if (lastTick+999<now) {
                    tick();
                    lastTick += 1000;
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
            if (!bootNotificationAccepted) scheduleAdd(0, BOOT_NOTIFICATION);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Event e = new Event(EventIds.WS_ON_CLOSE, deviceId, "onClose - code:"+code+" reason:"+reason+" remote:"+remote);
            eventMgr.trigger(e);
            isConnected = false;
            busy=false;
            scheduleAdd(10000l, CONNECT);            
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

                case "ChangeConfiguration": doChangeConfiguration(msgId, ja.getJSONObject(3)); break;       // OCPP 1.6
                case "GetVariables": doGetVariables(msgId, ja.getJSONObject(3)); break;
                case "SetVariables": doSetVariables(msgId, ja.getJSONObject(3)); break;
                case "ClearChargingProfile": doClearChargingProfile(msgId, ja.getJSONObject(3)); break;
                case "RemoteStartTransaction": doRemoteStartTransaction(msgId, ja.getJSONObject(3)); break;
                case "RemoteStopTransaction": doRemoteStopTransaction(msgId, ja.getJSONObject(3)); break;
                case "Reset": doReset(msgId, ja.getJSONObject(3)); break;
                case "SetChargingProfile": doSetChargingProfile(msgId, ja.getJSONObject(3)); break;
                case "GetDiagnostics": doGetDiagnostics(msgId, ja.getJSONObject(3)); break;
                case "UpdateFirmware": doUpdateFirmware(msgId, ja.getJSONObject(3)); break;
                case "GetConfiguration": doGetConfiguration(msgId, ja.getJSONObject(3)); break;
                case "TriggerMessage": doTriggerMessage(msgId, ja.getJSONObject(3)); break;
                case "UnlockConnector": doUnlockConnector(msgId, ja.getJSONObject(3)); break;

                default:
                    Dev.warn("processRequest unsupported command: "+command);

            }
            
        } finally {
            busy = false;
        }
        
    }

}
