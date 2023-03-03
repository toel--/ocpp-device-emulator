/*
 * Event used to propagate information
 */

package se.toel.ocpp.deviceEmulator.events;


/**
 *
 * @author toel
 */
public class Event implements EventIF {

    /**************************************************************************
    * Variables
    **************************************************************************/
    private Integer id = 0;
    private String deviceId = null;
    private String message = null;
    private Object o = null;
    private String answer = null;

    /**************************************************************************
    * Constructor
    **************************************************************************/
    public Event(Integer id, String deviceId, String message) {
        this.id = id;
        this.deviceId = deviceId;
        this.message = message;
    }

    public Event(Integer id, String deviceId, String message, String answer) {
        this.id = id;
        this.deviceId = deviceId;
        this.message = message;
        this.answer = answer;
    }
    
    public Event(Integer id, String deviceId, String message, String answer, Object o) {
        this.id = id;
        this.deviceId = deviceId;
        this.message = message;
        this.answer = answer;
        this.o = o;
    }

    /**************************************************************************
    * Public function
    **************************************************************************/
    @Override
    public String toString() {
        return id+" - "+deviceId+" - "+message;
    }
    

    /**************************************************************************
    * Getters and setters
    **************************************************************************/
    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }
    
    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Object getObject() {
        return o;
    }

    /**
     * @return the answer
     */
    @Override
    public String getAnswer() {
        return answer;
    }

    /**
     * @param answer the answer to set
     */
    @Override
    public void setAnswer(String answer) {
        this.answer = answer;
    }

    
}

