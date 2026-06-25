/*
 * Generates unique OCPP message ids.
 *
 * Ids are the current time in milliseconds (hex). To guarantee uniqueness it
 * remembers the last id returned; if a new id would collide with it (two calls
 * within the same millisecond) it waits 1ms and tries again, so the clock has
 * advanced. No sequence counter is needed.
 */
package se.toel.ocpp.deviceEmulator.utils;

import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class MessageIdGenerator {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private String lastId = null;

    /***************************************************************************
     * Public methods
     **************************************************************************/
    public synchronized String next() {

        String id = Long.toHexString(System.currentTimeMillis());
        while (id.equals(lastId)) {
            Dev.sleep(1);
            id = Long.toHexString(System.currentTimeMillis());
        }
        lastId = id;
        return id;

    }

}
