/*
 * Verifies the max-min fair split of a charge-point wide current budget.
 */
package se.toel.ocpp.deviceEmulator.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author toel
 */
public class ChargePointBudgetTest {

    @Test
    public void singleConnectorIsCappedToBudget() {
        assertArrayEquals(new double[]{10}, ChargePointBudget.split(new double[]{16}, 10), 0.001);
    }

    @Test
    public void demandBelowBudgetIsGrantedInFull() {
        assertArrayEquals(new double[]{6}, ChargePointBudget.split(new double[]{6}, 10), 0.001);
    }

    @Test
    public void twoEqualDemandsSplitEvenly() {
        assertArrayEquals(new double[]{10, 10}, ChargePointBudget.split(new double[]{16, 16}, 20), 0.001);
    }

    @Test
    public void budgetIsNeverExceeded() {
        double[] grant = ChargePointBudget.split(new double[]{16, 16, 16}, 18);
        assertEquals(18, grant[0] + grant[1] + grant[2], 0.001);
        assertArrayEquals(new double[]{6, 6, 6}, grant, 0.001);
    }

    @Test
    public void leftoverFromaLowDemandGoesToOthers() {
        // 6 A connector keeps its 6 A; the remaining 6 A goes to the hungry one.
        assertArrayEquals(new double[]{6, 6}, ChargePointBudget.split(new double[]{6, 16}, 12), 0.001);
    }

    @Test
    public void everyoneSatisfiedWhenBudgetIsAmple() {
        assertArrayEquals(new double[]{6, 16}, ChargePointBudget.split(new double[]{6, 16}, 30), 0.001);
    }

    @Test
    public void zeroBudgetGrantsNothing() {
        assertArrayEquals(new double[]{0, 0}, ChargePointBudget.split(new double[]{16, 16}, 0), 0.001);
    }

    @Test
    public void noConnectorsReturnsEmpty() {
        assertEquals(0, ChargePointBudget.split(new double[]{}, 10).length);
    }

    @Test
    public void wattLimitConvertsToAmps() {
        assertEquals(10, ChargePointBudget.limitToAmps(2300, "W", 230), 0.001);
        assertEquals(16, ChargePointBudget.limitToAmps(16, "A", 230), 0.001);
    }
}
