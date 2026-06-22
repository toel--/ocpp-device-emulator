/*
 * Scripted reply for the TestCentralSystem test backend.
 */
package se.toel.ocpp.deviceEmulator.support;

import org.json.JSONObject;

/**
 * Describes how TestCentralSystem should answer one inbound OCPP CALL.
 *
 * @author toel
 */
public class Reply {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    public enum Kind { RESULT, ERROR, MALFORMED, NONE, DROP }

    public final Kind kind;
    public final JSONObject payload;
    public final String code;
    public final String description;
    public final String raw;

    /***************************************************************************
     * Constructor
     **************************************************************************/
    private Reply(Kind kind, JSONObject payload, String code, String description, String raw) {
        this.kind = kind;
        this.payload = payload;
        this.code = code;
        this.description = description;
        this.raw = raw;
    }

    /***************************************************************************
     * Public methods
     **************************************************************************/
    public static Reply result(JSONObject payload) {
        return new Reply(Kind.RESULT, payload, null, null, null);
    }

    public static Reply error(String code, String description) {
        return new Reply(Kind.ERROR, null, code, description, null);
    }

    public static Reply malformed(String raw) {
        return new Reply(Kind.MALFORMED, null, null, null, raw);
    }

    public static Reply none() {
        return new Reply(Kind.NONE, null, null, null, null);
    }

    public static Reply drop() {
        return new Reply(Kind.DROP, null, null, null, null);
    }

}
