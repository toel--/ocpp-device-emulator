/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package se.toel.ocpp.deviceEmulator.events;

/**
 *
 * @author Toel
 */
public interface EventIF {

    public Integer getId();
    public String getDeviceId();
    public String getMessage();
    public Object getObject();
    public String getAnswer();
    public void setAnswer(String answer);
    
}
