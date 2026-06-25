/*
 * Verifies the message-id generator never returns the same id twice in a row.
 */
package se.toel.ocpp.deviceEmulator.utils;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class MessageIdGeneratorTest {

    @Test
    public void test01_tightLoopProducesUniqueIds() {
        MessageIdGenerator gen = new MessageIdGenerator();
        Set<String> seen = new HashSet<>();
        // A tight loop hits the same millisecond repeatedly - the old
        // hex(currentTimeMillis()) scheme would collide here.
        for (int i = 0; i < 50; i++) {
            String id = gen.next();
            assertNotNull(id);
            assertTrue("duplicate message id returned: " + id, seen.add(id));
        }
    }
}
