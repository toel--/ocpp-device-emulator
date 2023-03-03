/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package se.toel.ocpp.deviceEmulator.events;

/**
 *
 * @author Toel
 */
public interface EventListenerIF {

    public void eventTriggered(EventIF event);
    
}
