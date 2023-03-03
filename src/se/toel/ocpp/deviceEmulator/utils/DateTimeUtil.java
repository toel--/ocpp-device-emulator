/*
 * DateTimeUtil class to convert to / from UTC date time
 */

package se.toel.ocpp.deviceEmulator.utils;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import se.toel.util.Dev;

/**
 *
 * @author toel
 */
public class DateTimeUtil {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/

     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public static String toIso8601(long timestamp) {
     
        Date date = new Date(timestamp);
        SimpleDateFormat sdf;
        
        boolean useUTC = true;
        
        if (useUTC) {
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"); // 2022-06-09T06:40:11.123
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        } else {
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"); // 2022-06-09T06:40:11.123+05:30
        }
                
        return sdf.format(date);
        
    }
    
    public static long fromIso8601(String s) {
        
        long time = 0l;
        
        // ZonedDateTime dateTime = ZonedDateTime.parse(s);
        
        SimpleDateFormat sdf;
        if (s.contains("Z")) {
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        } else {
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        }
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            Date date = sdf.parse(s);
            time = date.getTime();
        } catch (Exception e) {
            Dev.error("While parsing "+s, e);
        }
        return time;
    
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/

}
