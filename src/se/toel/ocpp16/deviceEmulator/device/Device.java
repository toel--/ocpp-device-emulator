package se.toel.ocpp16.deviceEmulator.device;

import se.toel.ocpp16.deviceEmulator.device.impl.DeviceData;
import se.toel.ocpp16.deviceEmulator.device.impl.FirmwareUpdate;
import se.toel.ocpp16.deviceEmulator.device.impl.Connector;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
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
import se.toel.ocpp16.deviceEmulator.device.impl.LocalAuthorization;
import se.toel.ocpp16.deviceEmulator.communication.CallbackIF;
import se.toel.ocpp16.deviceEmulator.communication.OCPP_16;
import se.toel.ocpp16.deviceEmulator.device.impl.AuthorizationCache;
import se.toel.ocpp16.deviceEmulator.device.impl.Configuration;
import se.toel.ocpp16.deviceEmulator.device.impl.LocalAuthorizationList;
import se.toel.ocpp16.deviceEmulator.utils.DateTimeUtil;
import se.toel.ocpp16.deviceEmulator.utils.FTP;
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
    private final String id;
    private String url;
    private OCPP_16 ocpp;
    private final CallbackIF callback = new Callback();
    private final Map<String, JSONArray> answers = new ConcurrentHashMap<>();
    private boolean isConnected = false;
    private boolean stdoutneednewline = false;
    private final boolean echo = true;
    private boolean busy = false;
    private int heartbeatInverval = 3600;
    private final Map<Long, String> scheduled = new ConcurrentHashMap<>();
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
    private boolean bootNotificationSent = false;
    Heart heart;
    
    // Auhorize stuff
    private static final String userId = "1";
    
    // Tags
    private static final String
            AUTHORIZE = "Authorize",
            BOOT_NOTIFICATION = "BootNotification",
            HEARTBEAT = "Heartbeat",
            START_TRANSACTION = "StartTransaction",
            STOP_TRANSACTION = "StopTransaction",
            STATUS_NOTIFICATION = "StatusNotification",
            FIRMWARE_STATUS_NOTIFICATION = "FirmwareStatusNotification",
            METER_VALUES = "MeterValues",
            DIAGNOSTICS_STATUS_NOTIFICATION = "DiagnosticsStatusNotification";
    
    
     /***************************************************************************
     * Constructor
     **************************************************************************/
    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    public Device(String id, String url) {
        
        this.id = id;
        this.url = url;
        
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
        
        scheduled.put(System.currentTimeMillis()+2000, "connect");
                
        heart = new Heart();
        
    }
    
     /***************************************************************************
     * Public methods
     **************************************************************************/
    public boolean connect() {
        
        busy = true;
        
        try {
            echo("connecting...");
            URI uri = getBackendUri();
            Dev.info("Connecting using " + uri);
            this.ocpp = new OCPP_16(uri);
            ocpp.registerCallback(callback);
            if (!ocpp.isOpen()) {
                ocpp.addHeader("Sec-WebSocket-Protocol", "ocpp1.6");
                ocpp.connect();
                int timeout = 100;
                while (--timeout > 0 && !isConnected) {
                    Dev.sleep(10);
                }
            }

            if (isConnected) {
                echo("  success");
                scheduleRemove("connect");
            } else {
                echo("  failure");
            }
        } finally {
            busy = false;
        }

        return ocpp.isOpen();
    }
    
    public boolean disconnect() {
        
        busy = true;
        
        try {
            ocpp.close();
            int timeout = 100;
            while (--timeout>0 && isConnected) Dev.sleep(10);
        } finally {
            busy = false;
        }
        
        return ocpp.isClosed();
    }
    
    public boolean doBootNotification() {
     
        // [2, "id-here", "BootNotification", {"chargePointModel": "model", "chargePointVendor": "vendor", "firmwareVersion": "0.0.1"}]
        // [3,"01234567899876543210",{"status":"Accepted","currentTime":"2021-11-08T07:38:52.081","interval":3600}]
        
        boolean accepted;
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("chargePointModel", deviceData.getModel());
        req.put("chargePointVendor", deviceData.getVendor());
        req.put("firmwareVersion", deviceData.getFirmwareVersion());
        
        String msgId = Long.toHexString(now);
        
        sendReq(msgId, BOOT_NOTIFICATION, req);
        
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
            scheduled.put(now+1000, AUTHORIZE);
            scheduled.put(now+heartbeatInverval*1000, HEARTBEAT);
            bootNotificationSent = true;
        } else {
            scheduled.put(now+heartbeatInverval*1000, BOOT_NOTIFICATION);
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
            req.put("status", "Available");
        } else {
            // and this about a specific connector
            req.put("connectorId", connector.getId());                                                                                                          // Required. The id of the connector for which the status is reported. Id '0' (zero) is used if the status is for the Charge Point main controller.
            req.put("errorCode", connector.getErrorCode());                                                                                                     // Required. This contains the error code reported by the Charge Point.
            // req.put("info", null);                                                                                                                           // Optional. Additional free format information related to the error.
            req.put("status", connector.getStatus());                                                                                                           // Required. This contains the current status of the Charge Point.

            // req.put("timestamp", DateTimeUtil.toIso8601(now));                                                                                               // Optional. The time for which the status is reported. If absent time of receipt of the message will be assumed.
            // req.put("vendorId", null);                                                                                                                       // Optional. This identifies the vendor-specific implementation.
            if (!connector.getVendorErrorCode().isEmpty()) req.put("vendorErrorCode", connector.getVendorErrorCode());                                          // Optional. This contains the vendor-specific error code.
        }
            
        String msgId = Long.toHexString(now);
        sendReq(msgId, STATUS_NOTIFICATION, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            return false;
        }
        
        boolean ok = json.getInt(0)==3;
        
        return ok;
        
    }
    
    
    public boolean doAuthorize() {
     
        // 
        
        boolean accepted;
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("idTag", userId);
        
        String msgId = Long.toHexString(now);
        
        sendReq(msgId , AUTHORIZE, req);
        
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
            echo("  user "+userId+" is authorized");
        } else {
            echo("  user "+userId+" is NOT authorized");
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
        System.out.print(".");
        stdoutneednewline = true;
        
        if (tickCount%80==0) {
            System.err.println("");
            stdoutneednewline = false;
        }
        
        // Performe the schedule actions
        if (!busy) {
            
            long now = System.currentTimeMillis();

            for (Map.Entry<Long, String> entry : scheduled.entrySet()) {
                if (entry.getKey()<now) {
                    scheduled.remove(entry.getKey());
                    String action = entry.getValue();
                    triggerAction(entry.getValue());
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
            {
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
        String storePath = "data/"+id;
        if (config.hasChanged()) config.store(storePath+"/configuration.dat");
        if (deviceData.hasChanged()) deviceData.store(storePath+"/deviceData.dat");
        if (localAuth.hasChanged()) localAuth.store(storePath+"/localAuthorizationList.dat");
        if (authCache.hasChanged()) authCache.store(storePath+"/authorizationCache.dat");
        for (Connector connector : connectors) {
            if (connector.hasChanged()) connector.store(storePath+"/connector_"+connector.getId()+".dat");
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
    
    private void triggerAction(String action) {
     
        String command = action;
        JSONObject json = null;
        
        if (action.contains(" ")) {
            command = StringUtil.getWord(action, 1);
            json = new JSONObject(StringUtil.getWord(action, 2));
        }
        
        if ("connect".equals(action)) {
            connect();
            return;
        }
        
        if (isConnected) {
            switch (command) {

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
    
    
    private void sendReq(String id, String message, JSONObject payload) {
                
        JSONArray msg = new JSONArray();
        msg.put(2);
        msg.put(id);
        msg.put(message);
        msg.put(payload);
        String s = msg.toString();
        
        echo("S: "+s);
        ocpp.send(s);
        
    }
    
    private void sendConf(String id, JSONObject payload) {
                
        // Dev.sleep((int)(Math.random()*1000));
        
        JSONArray msg = new JSONArray();
        msg.put(3);
        msg.put(id);
        msg.put(payload);
        String s = msg.toString();
        
        echo("S: "+s);
        ocpp.send(s);
        
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
    
    
    
    private class Callback implements CallbackIF {

        @Override
        public void onMessage(String message) {
            
            echo("R: "+message);
            
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
            echo("onOpen");
            isConnected = true;
            if (!bootNotificationSent) scheduled.put(System.currentTimeMillis(), BOOT_NOTIFICATION);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            echo("onClose code:"+code+" reason:"+reason+" remote:"+remote);
            isConnected = false;
            busy=false;
            scheduled.put(System.currentTimeMillis()+10000, "connect");            
        }

        @Override
        public void onError(Exception ex) {
            Dev.info("onError "+ex.getClass().getName()+": "+ex.getMessage());
            // ex.printStackTrace(System.out);
        }
        
        
    }
    
    
    private void echo(String s) {
        if (echo) {
            if (stdoutneednewline) System.out.println("");
            Dev.info(s);
            stdoutneednewline=false;
        }
    }
    
    
    private URI getBackendUri() {
        URI uri = null;
        try {
            uri = new URI(url+"/"+id); // 'ws://localhost:8443/ocpp/'
        } catch (URISyntaxException e) {
            log.error("While creating backend URI", e);
        }
        return uri;
    }
    
    
    
    
    
    private void doClearChargingProfile(String msgId, JSONObject json) {
     
        // The criteria should be added to each other, that is AND
        // TODO thiis is not implemented right, fix me when its usefull
        
        boolean success = true;
        int id = json.optInt("id");
        // if (id>0) Dev.info("   Need to clear the charging profile "+id);
        
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
            sendConf(msgId, jsonStatusAccepted);
        } else {
            sendConf(msgId, jsonStatusUnknown);
        }
        
        
    }
    
    private void doRemoteStartTransaction(String msgId, JSONObject json) {
     
        // [2,"658aae13-b824-4eb7-a532-8d8c90f538c9","RemoteStartTransaction",{"connectorId":1,"idTag":"1"}].
        // [3,"a0bf5910-f3a9-4205-8c18-af27983a016c",{"status":"Accepted"}]
        
        // Dev.sleep(1000); // will cause timeout
        
        int connectorId = json.optInt("connectorId", 1);
        int idTag = json.optInt("idTag");
        
        if (config.getBoolean("AuthorizeRemoteTxRequests")) {
            sendConf(msgId, jsonStatusNotSupported);
            return;
        }
        
        Connector connector = connectors.get(connectorId);
        if (connector.getTransactionId()>0) {
            // We have a transaction allready
            sendConf(msgId, jsonStatusRejected);
        } else {
                    
            connector.setIdTag(String.valueOf(idTag));
            
            // Send confirmation
            sendConf(msgId, jsonStatusAccepted);

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
            sendConf(msgId, jsonStatusRejected);
        } else {
            sendConf(msgId, jsonStatusAccepted);
            scheduled.put(System.currentTimeMillis(), STOP_TRANSACTION+" "+json.toString());
        }
        
    }
    
    
    private boolean doStartTransaction(JSONObject params) {
                
        // Get the params to use for the StartTransaction.req
        int connectorId = params.optInt("connectorId", 1);
        String idTag = params.getString("idTag");
        JSONObject chargingProfile = params.optJSONObject("chargingProfile");
        
        Connector connector = connectors.get(connectorId);
        
        // Send the request to the Central System
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("connectorId", connectorId);
        req.put("idTag", idTag);
        req.put("meterStart", connector.getMeterWh());
        if (connector.getReservationId()>0) req.put("reservationId", connector.getReservationId());
        req.put("timestamp", DateTimeUtil.toIso8601(now));
        
        String msgId = Long.toHexString(now);
        sendReq(msgId, START_TRANSACTION, req);
        
        // Get the conf and process it
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            scheduled.put(now+5000, START_TRANSACTION+" "+params.toString());
            return false;
        }
        if (json.getInt(0)!=3) return false;
        
        JSONObject conf = json.getJSONObject(2);
        
        connector.setTransactionId(conf.getInt("transactionId"));
        JSONObject idTagInfo = conf.getJSONObject("idTagInfo");
        String expiryDate = idTagInfo.optString("expiryDate");
        String parentIdTag = idTagInfo.optString("parentIdTag");
        String status = idTagInfo.getString("status");
        
        if (idTagInfo!=null) {
            authCache.update(idTag, idTagInfo);
        }
        
        if (expiryDate!=null) {
            LocalAuthorization.getInstance().setIdTagExpiryDate(idTag, expiryDate);
        }
        
        boolean accepted = "Accepted".equals(status);
        switch (status) {
            case "Accepted": break;
            case "Blocked": echo("   Identifier has been blocked. Not allowed for charging."); break;
            case "Expired": echo("   Identifier has expired. Not allowed for charging."); break;
            case "Invalid": echo("   Identifier is unknown. Not allowed for charging."); break;
            case "ConcurrentTx": echo("   Identifier is already involved in another transaction and multiple transactions are not allowed."); break;
        }
        
        if (accepted) {
            connector.setStatus(Connector.STATUS_CHARGING);
        }
                        
        return accepted;
        
    }
    
    
    private boolean doStopTransaction(JSONObject params) {
                
        // Get the params to use for the StopTransaction.req
        int transactionId = params.getInt("transactionId");
        Connector connector = getConnectorWithTransactionId(transactionId);
        
        if (connector==null) return false;      // No connector with this transaction
        
        return doStopTransaction(connector);
        
    }
    
    private boolean doStopTransaction(Connector connector) {
                        
        JSONArray transactionData = null;
        if (config.getMeterValueSampleInterval()>0) {
            transactionData = connector.getMeterValues();
        }
        
        // Send the request to the Central System
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();
        req.put("idTag", connector.getIdTag());                                 // Optional. This contains the identifier which requested to stop the charging. It is optional because a Charge Point may terminate charging without the presence of an idTag, e.g. in case of a reset. A Charge Point SHALL send the idTag if known.
        req.put("meterStop", connector.getMeterWh());                           // Required. This contains the meter value in Wh for the connector at end of the transaction.
        req.put("timestamp", DateTimeUtil.toIso8601(now));                      // Required. This contains the date and time on which the transaction is stopped.
        req.put("transactionId", connector.getTransactionId());                 // Required. This contains the transaction-id as received by the StartTransaction.conf.
        req.put("reason", connector.stopReason);                                // Optional. This contains the reason why the transaction was stopped. MAY only be omitted when the Reason is "Local".
        req.put("transactionData", transactionData);                            // Optional. This contains transaction usage details relevant for billing purposes.
        
        String msgId = Long.toHexString(now);
        sendReq(msgId, STOP_TRANSACTION, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) {
            scheduled.put(now+5000, STOP_TRANSACTION+" {\"transactionId\":"+connector.getTransactionId()+"}");
            return false;
        }
        
        boolean success = json.getInt(0)==3;
        
        if (success) {
            JSONObject conf = json.getJSONObject(2);
            JSONObject idTagInfo = conf.optJSONObject("idTagInfo");                 // Optional. This contains information about authorization status, expiry and parent id. It is optional, because a transaction may have been stopped without an identifier.
        
            if (idTagInfo!=null) {
                authCache.update(connector.getIdTag(), idTagInfo);
                String status = idTagInfo.getString("status");                      // Required. This contains whether the idTag has been accepted or not by the Central System.
                String parentIdTag = idTagInfo.optString("parentIdTag");            // Optional. This contains the parent-identifier.
                String expiryDate = idTagInfo.optString("expiryDate");              // Optional. This contains the date at which idTag should be removed from the Authorization Cache.
                success = "Accepted".equals(status);
            }
        }
        
        if (success) {
            connector.setReservationId(0);
            connector.setTransactionId(0);
            connector.setStatus(Connector.STATUS_AVAILABLE);
        }
        
        return success;
        
    }
    
    
    private void doReset(String msgId, JSONObject json) {
        
        String resetType = json.optString("type", "Soft");
        
        echo("Performing a "+resetType+" reset...");
        
        // Send confirmation
        sendConf(msgId, jsonStatusAccepted);
        
        Dev.sleep(500);
        
        stop();
        
        if ("Hard".equals(resetType)) {
            scheduled.clear();
        }
        
        ocpp.close();
        
        // Simulate reboot
        for (Connector connector : connectors) {
            connector.reset();
        }
        
        bootNotificationSent = false;
        scheduled.put(System.currentTimeMillis()+5000l, "connect");
        
    }
    
    private void doSetChargingProfile(String msgId, JSONObject json) {
     
        // [2,"ddd29cba-febc-4eaa-a0e8-4219e28ac387","SetChargingProfile",{"connectorId":1,"csChargingProfiles":{"chargingProfileId":1,"transactionId":29,"stackLevel":0,"chargingProfilePurpose":"TxProfile","chargingProfileKind":"Relative","chargingSchedule":{"chargingRateUnit":"A","chargingSchedulePeriod":[{"startPeriod":0,"limit":0.0}]}}}]
        
        int connectorId = json.optInt("connectorId", 1);
        JSONObject csChargingProfiles = json.getJSONObject("csChargingProfiles");
        
        Connector connector = connectors.get(connectorId);
        boolean success = connector.setChargingProfile(csChargingProfiles);
        
        if (success) {
            sendConf(msgId, jsonStatusAccepted);
        } else {    
            sendConf(msgId, jsonStatusRejected);
        }
        
        
    }
    
    private void doGetDiagnostics(String msgId, JSONObject json) {
     
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
        
        sendConf(msgId, payload);
        
        JSONObject notification = new JSONObject();
        notification.put("status", uploaded ? "Uploaded" : "UploadFailed");
        
        scheduled.put(System.currentTimeMillis()+1000, DIAGNOSTICS_STATUS_NOTIFICATION+" "+notification.toString());
        
        
    }
    
    
    private void doUpdateFirmware(String msgId, JSONObject json) {
     
        // Just dont answer or do anything
        if (false) {
        
        ongoingFirmwareUpdate = new FirmwareUpdate();
        
        ongoingFirmwareUpdate.setLocation(json.getString("location"));                   // Required. This contains a string containing a URI pointing to a location from which to retrieve the firmware.
        ongoingFirmwareUpdate.setRetries(json.optInt("retries"));                        // Optional. This specifies how many times Charge Point must try to upload the diagnostics before giving up. If this field is not present, it is left to Charge Point to decide how many times it wants to retry.
        ongoingFirmwareUpdate.setRetryInterval(json.optInt("retryInterval"));            // Optional. The interval in seconds after which a retry may be attempted. If this field is not present, it is left to Charge Point to decide how long to wait between attempts.
        ongoingFirmwareUpdate.setRetrieveDate(json.getString("retrieveDate"));           // Optional. This contains the date and time of the oldest logging information to include in the diagnostics.
        
        
        // Just acknowledge the request with empty payload, the status will be send using FirmwareStatusNotification in the backgound
        JSONObject payload = new JSONObject();
        sendConf(msgId, payload);
        
        }
        
    }
    
    
    private void doGetConfiguration(String msgId, JSONObject json) {
     
        JSONArray keys = json.optJSONArray("key");                             // Optional. List of keys for which the configuration value is requested.
        
        
        JSONObject payload = new JSONObject();
        JSONArray configurationKeys = new JSONArray();
        JSONArray unknownKey = new JSONArray();
        
        
        if (keys==null || keys.length()==0) {
            // Return all known
            for (Map.Entry<String, String> entry : config.entrySet()) {
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
        
        sendConf(msgId, payload);
        
        
    }
    
    
    private void doChangeConfiguration(String msgId, JSONObject json) {
        
        boolean success = true;
        String key = json.getString("key");
        String value = json.getString("value");
        
        // Special case for the echo, used for tests
        if (key.equals("echo")) {
            JSONObject ans = new JSONObject();
            ans.put("status", value);
            sendConf(msgId, ans);
            if ("Accepted,RebootRequired".contains(value)) config.set(key, value);
            return;
        }
        
        config.set(key, value);
        
        // Send confirmation (Accepted, Rejected, RebootRequired, NotSupported)
        if (success) {
            sendConf(msgId, jsonStatusAccepted);
        } else {
            sendConf(msgId, jsonStatusNotSupported);
        }
        
        
    }
    
    
    
    private void doTriggerMessage(String msgId, JSONObject json) {
        
        String requestedMessage = json.getString("requestedMessage");
        int connectorId = json.optInt("connectorId");
        boolean success = true;
        
        JSONObject params = new JSONObject();
        params.put("connectorId", connectorId);
        
        // [2,"381f275e-9ff8-4960-bdf8-e7abc5a98bbc","TriggerMessage",{"requestedMessage":"StatusNotification"}]
    
        switch (requestedMessage) {
            case "BootNotification": scheduled.put(System.currentTimeMillis(), BOOT_NOTIFICATION); break;
            case "DiagnosticsStatusNotification": scheduled.put(System.currentTimeMillis(), DIAGNOSTICS_STATUS_NOTIFICATION); break;
            case "FirmwareStatusNotification": scheduled.put(System.currentTimeMillis(), FIRMWARE_STATUS_NOTIFICATION); break;
            case "Heartbeat": scheduled.put(System.currentTimeMillis(), HEARTBEAT); break;
            case "MeterValues": scheduled.put(System.currentTimeMillis(), METER_VALUES); break;
            case "StatusNotification": scheduled.put(System.currentTimeMillis(), STATUS_NOTIFICATION+" "+params.toString()); break;
            default: 
                success = false;
                Dev.error("Unsupported requestedMessage: "+requestedMessage);
        }
        
        if (success) {
            sendConf(msgId, jsonStatusAccepted);
        } else {
            sendConf(msgId, jsonStatusNotImplemented);
        }
        
        
    }
    
    private void doMeterValues(Connector connector) {
     
        JSONObject json = new JSONObject();
        json.put("connectorId", connector.getId());
        json.put("transactionId", connector.getTransactionId());
        json.put("meterValue", connector.getMeterValues());
        
        scheduled.put(System.currentTimeMillis()+10000, METER_VALUES+" "+json.toString());
        
    }
    
    private void doMeterValues(JSONObject json) {
     
        doSimpleRequest(METER_VALUES, json); 
        
    }
    
    private void doDiagnosticsStatusNotification(JSONObject json) {
        
        doSimpleRequest(DIAGNOSTICS_STATUS_NOTIFICATION, json);        
        
    }
    
    private JSONArray doSimpleRequest(String type, JSONObject json) {
        
        long now = System.currentTimeMillis(); 
        String msgId = Long.toHexString(now);
        
        sendReq(msgId, type, json);
        
        return waitForAnswer(msgId);
        
    }
    
    
    private void doHeartBeat() {
        
        long now = System.currentTimeMillis();
        JSONObject req = new JSONObject();        
        String msgId = Long.toHexString(now);
        
        sendReq(msgId, HEARTBEAT, req);
        
        JSONArray json = waitForAnswer(msgId);
        if (json==null) return;
        if (json.getInt(0)!=3) return;
        
        JSONObject obj = json.getJSONObject(2);
        String currentTime = obj.getString("currentTime");
        
        long timestamp = DateTimeUtil.fromIso8601(currentTime);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String datetime = df.format(new Date(timestamp));
        
        echo("   Central system current time is "+datetime);
        
        // And reshedule
        scheduled.put(now+heartbeatInverval*1000, HEARTBEAT);
        
    }
    
    
    private void stop() {
                
        for (Connector connector : connectors) {
            if (!connector.getStatus().equals(Connector.STATUS_AVAILABLE)) doStopTransaction(connector);
        }
        
    }
    
    
    
    private void scheduleRemove(String command) {
        
        /*
        scheduled.entrySet().stream().filter(entry -> (entry.getValue().equals(command))).forEachOrdered(entry -> {
            scheduled.remove(entry.getKey());
        });
        */
        
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
                    // ongoingFirmwareUpdate.setStatus(FirmwareUpdate.FIRMWARE_STATUS_INSTALLED);
                    // break;
                // case FirmwareUpdate.FIRMWARE_STATUS_INSTALLED:
                    deviceData.setFirmwareVersion(ongoingFirmwareUpdate.getVersion());
                    bootNotificationSent = false;
                    stop();
                    disconnect();
                    scheduled.put(System.currentTimeMillis()+5000l, "connect"); 
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
                sendReq(msgId, FIRMWARE_STATUS_NOTIFICATION, req);

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
        }

        public void shutdown() {
            running = false;
        }
        
        
    }

}
