/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.toel.ocpp.deviceEmulator.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Toel
 */
public class EventHandler {

    /**
     * ************************************************************************
     * Variables
    *************************************************************************
     */
    private static EventHandler instance = null;
    private Map<Integer, List<EventListenerIF>> eventListeners = null;
    private List<EventListenerIF> allEventsListeners = null;

    /**
     * ************************************************************************
     * Constructor
    *************************************************************************
     */
    private EventHandler() {
        eventListeners = new HashMap<>();
        allEventsListeners = new ArrayList<>();
    }

    /**************************************************************************
     * Public static function
    **************************************************************************/
    /**
     * 
     * @return 
     */
    public static EventHandler getInstance() {
        if (instance == null) {
            instance = new EventHandler();
        }
        return instance;
    }

    /**************************************************************************
     * Public function
    **************************************************************************/
    /**
     * subscribe to an event
     * @param eventId
     * @param listener
     * @return 
     */
    public boolean subscribe(Integer eventId, EventListenerIF listener) {

        boolean added = false;

        if (eventId==null) {
            allEventsListeners.add(listener);
            added = true;
        } else {        
            List<EventListenerIF> list = eventListeners.get(eventId);
            if (list == null) {
                list = new ArrayList<>();
                eventListeners.put(eventId, list);
            }
            if (!list.contains(listener)) {
                list.add(listener);
                added = true;
            }
        }

        return added;

    }

    /**
     * Trigger an event
     * @param event
     * @return 
     */
    public String trigger(EventIF event) {

        Set<EventListenerIF> list = new HashSet<>();
        list.addAll(allEventsListeners);
        List<EventListenerIF> listners = eventListeners.get(event.getId());
        if (listners!=null) list.addAll(listners);
        String ret = null;
        for (EventListenerIF listener : list) {
            listener.eventTriggered(event);
            String ans = event.getAnswer();                                 // TODO: remove this: it will break the asynchrone 
            if (ans != null) {
                if (ret == null) {
                    ret = ans;
                } else {
                    ret += " \n" + ans;
                }
            } else {
                if (event.getObject() instanceof String) {
                    if (ret == null) {
                        ret = (String) event.getObject();
                    } else {
                        ret += " \n" + event.getObject();
                    }
                }
            }
        }

        return ret;

    }

        
    public void trigger(Integer id, String deviceId, String message) {

        Event event = new Event(id, deviceId, message);
        Set<EventListenerIF> list = new HashSet<>();
        list.addAll(allEventsListeners);
        List<EventListenerIF> listners = eventListeners.get(event.getId());
        if (listners!=null) list.addAll(listners);
        for (EventListenerIF listener : list) {
            listener.eventTriggered(event);
        }


    }
    


    /**************************************************************************
     * Private function
    **************************************************************************/
    
}
