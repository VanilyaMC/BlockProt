package de.sean.blockprot.cleanup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockRulesTest {
    private static final long DAY = 86_400_000L;

    @Test
    void aLockOfSomebodyWhoNeverLeftNeverRunsOut() {
        assertFalse(LockRules.orphanGraceOver(0, 100 * DAY, 7));
    }

    @Test
    void aLockStaysDuringTheGracePeriod() {
        assertFalse(LockRules.orphanGraceOver(10 * DAY, 16 * DAY, 7));
        assertFalse(LockRules.orphanGraceOver(10 * DAY, 17 * DAY, 7));
    }

    @Test
    void aLockRunsOutAfterTheGracePeriod() {
        assertTrue(LockRules.orphanGraceOver(10 * DAY, 17 * DAY + 1, 7));
    }

    @Test
    void zeroGraceDaysTurnsTheRuleOff() {
        assertFalse(LockRules.orphanGraceOver(10 * DAY, 500 * DAY, 0));
    }

    @Test
    void aTownAuthorityUnlocksOnTownPlots() {
        assertTrue(LockRules.townAuthorityMayUnlock(true, true, true));
    }

    @Test
    void aTownAuthorityNeverUnlocksAResidentsLockOnThatResidentsPlot() {
        assertFalse(LockRules.townAuthorityMayUnlock(true, false, true));
    }

    @Test
    void aTownAuthorityUnlocksTheLockOfSomebodyWhoLeft() {
        assertTrue(LockRules.townAuthorityMayUnlock(true, false, false));
    }

    @Test
    void anOrdinaryResidentNeverUnlocksAnything() {
        assertFalse(LockRules.townAuthorityMayUnlock(false, true, false));
        assertFalse(LockRules.townAuthorityMayUnlock(false, false, false));
    }
}
