/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.toel.ocpp.deviceEmulator.device.impl;

import se.toel.collection.DataMap;

/**
 *
 * @author toel
 */
public class LocalAuthorizationList extends DataMap {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    public static final String
            VALID = "valid",
            EXPIRED = "expired",
            BLOCKED = "blocked",    // temporarily) blocked
            BLACKLISTED = "blacklisted";

    private final Configuration conf;
    
     /***************************************************************************
     * Constructor
     **************************************************************************/
    public LocalAuthorizationList(Configuration conf) {
        this.conf = conf;
    }

     /***************************************************************************
     * Public methods
     **************************************************************************/
    

    /***************************************************************************
     * Private methods
     **************************************************************************/

}
