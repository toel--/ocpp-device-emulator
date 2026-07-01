/*
 * Result of a StartTransaction attempt: whether the call returned, the idTag auth status, and the
 * backend transactionId (which OCPP 1.6 returns even when the idTag is not Accepted).
 */
package se.toel.ocpp.deviceEmulator.device;

public final class StartTransactionOutcome {

    private final boolean callSucceeded;
    private final String idTagStatus;
    private final int transactionId;

    public StartTransactionOutcome(boolean callSucceeded, String idTagStatus, int transactionId) {
        this.callSucceeded = callSucceeded;
        this.idTagStatus = idTagStatus;
        this.transactionId = transactionId;
    }

    public static StartTransactionOutcome failed() {
        return new StartTransactionOutcome(false, null, 0);
    }

    public boolean callSucceeded() {
        return callSucceeded;
    }

    public String idTagStatus() {
        return idTagStatus;
    }

    public int transactionId() {
        return transactionId;
    }

    public boolean isAccepted() {
        return "Accepted".equals(idTagStatus);
    }
}
