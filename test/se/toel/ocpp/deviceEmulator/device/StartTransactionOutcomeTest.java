/*
 * Verifies the StartTransaction result value type.
 */
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import static org.junit.Assert.*;

public class StartTransactionOutcomeTest {

    @Test
    public void acceptedBlockedAndFailed() {
        StartTransactionOutcome ok = new StartTransactionOutcome(true, "Accepted", 42);
        assertTrue(ok.callSucceeded());
        assertTrue(ok.isAccepted());
        assertEquals(42, ok.transactionId());

        StartTransactionOutcome blocked = new StartTransactionOutcome(true, "Blocked", 43);
        assertTrue(blocked.callSucceeded());
        assertFalse(blocked.isAccepted());

        StartTransactionOutcome failed = StartTransactionOutcome.failed();
        assertFalse(failed.callSucceeded());
        assertFalse(failed.isAccepted());
        assertEquals(0, failed.transactionId());
    }
}
