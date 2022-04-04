/*
 * 
 */
package se.toel.ocpp.deviceEmulator.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class DateTimeUtilTest {
    
    public DateTimeUtilTest() {
    }
    

    /**
     * Test of toIso8601 method, of class DateTimeUtil.
     */
    @Test
    public void testToIso8601() {
        System.out.println("toIso8601");
        long timestamp = 1640582955178l;
        String expResult = "2021-12-27T05:29:15.178";
        String result = DateTimeUtil.toIso8601(timestamp);
        assertEquals(expResult, result);
    }

    /**
     * Test of fromIso8601 method, of class DateTimeUtil.
     */
    @Test
    public void testFromIso8601() {
        System.out.println("fromIso8601");
        String s = "2021-12-27T05:29:15.178";
        long expResult = 1640582955178l;
        long result = DateTimeUtil.fromIso8601(s);
        assertEquals(expResult, result);
    }
    
}
