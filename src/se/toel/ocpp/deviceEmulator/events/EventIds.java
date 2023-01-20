/*
 * Event ids definition
 */

package se.toel.ocpp.deviceEmulator.events;

/**
 *
 * @author toel
 */
public class EventIds {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    public static final int
            CONNECTING                  = 10000,
            CONNECTED                   = 10001,
            CONNECTION_FAILED           = 10002,
            AUTORIZING                  = 10010,
            AUTORIZED                   = 10011,
            AUTORIZATION_FAILED         = 10012,
            RESETING                    = 10020,
            RESETED                     = 10021,
            RESET_FAILED                = 10022,
            TRANSACTION_STARTING        = 10030,
            TRANSACTION_STARTED         = 10031,
            TRANSACTION_START_FAILED    = 10032,
            HEARTBEAT_BEFORE            = 10040,
            HEARTBEAT_AFTER             = 10041,
            HEARTBEAT_FAILED            = 10042,
            
            INFO                        = 10050,
            
            
            OCPP_SENDING                = 10100,
            // OCPP_SENT                   = 10101,
            // OCPP_SEND_FAILURE           = 10102,
            
            // OCPP_RECEIVING              = 10110,
            OCPP_RECEIVED               = 10111,
            // OCPP_REC_FAILURE            = 10112,
            
            WS_ON_OPEN                  = 19200,
            WS_ON_CLOSE                 = 19201,
            WS_ON_ERROR                 = 19202;
            
    

}
