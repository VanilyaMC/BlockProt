package de.sean.blockprot.cleanup;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupPlannerTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static LockEntry lock(UUID owner, String world, int x, int y, int z) {
        return new LockEntry(owner, world, x, y, z);
    }

    @Test
    void plotClearTakesOnlyTheLocksInsideThePlot() {
        var inside = lock(ALICE, "world", 17, 64, 33);      // plot (1, 2)
        var edge = lock(BOB, "world", 31, 70, 47);          // plot (1, 2), last block
        var nextPlot = lock(ALICE, "world", 32, 64, 33);    // plot (2, 2)
        var otherWorld = lock(ALICE, "nether", 17, 64, 33);
        var chosen = CleanupPlanner.inPlot(List.of(inside, edge, nextPlot, otherWorld), "world", 1, 2, 16, null);
        assertEquals(Set.of(inside, edge), Set.copyOf(chosen));
    }

    @Test
    void negativeCoordinatesBelongToTheRightPlot() {
        var a = lock(ALICE, "world", -1, 64, -1);   // plot (-1, -1)
        var b = lock(ALICE, "world", -16, 64, -16); // plot (-1, -1)
        var c = lock(ALICE, "world", -17, 64, -16); // plot (-2, -1)
        var chosen = CleanupPlanner.inPlot(List.of(a, b, c), "world", -1, -1, 16, null);
        assertEquals(Set.of(a, b), Set.copyOf(chosen));
    }

    @Test
    void theNewOwnerKeepsTheirLocks() {
        var mine = lock(ALICE, "world", 1, 64, 1);
        var theirs = lock(BOB, "world", 2, 64, 2);
        var chosen = CleanupPlanner.inPlot(List.of(mine, theirs), "world", 0, 0, 16, ALICE::equals);
        assertEquals(List.of(theirs), chosen);
    }

    @Test
    void unclaimWithoutNewOwnerTakesEverything() {
        var one = lock(ALICE, "world", 1, 64, 1);
        var two = lock(BOB, "world", 2, 64, 2);
        assertEquals(2, CleanupPlanner.inPlot(List.of(one, two), "world", 0, 0, 16, null).size());
    }

    @Test
    void aDifferentPlotSizeIsRespected() {
        var inside = lock(ALICE, "world", 20, 64, 20);  // size 32: plot (0, 0)
        var outside = lock(ALICE, "world", 40, 64, 20); // plot (1, 0)
        assertEquals(List.of(inside), CleanupPlanner.inPlot(List.of(inside, outside), "world", 0, 0, 32, null));
    }

    @Test
    void aResidentWhoLeavesLosesOnlyTheirLocksInsideTheTown() {
        var inTown = lock(ALICE, "world", 5, 64, 5);
        var elsewhere = lock(ALICE, "world", 500, 64, 500);
        var others = lock(BOB, "world", 6, 64, 6);
        var chosen = CleanupPlanner.ownedInside(List.of(inTown, elsewhere, others), ALICE, e -> e.x() < 100);
        assertEquals(List.of(inTown), chosen);
    }

    @Test
    void severalPlotsAreFoundInOnePass() {
        var a = lock(ALICE, "world", 1, 64, 1);    // plot (0, 0)
        var b = lock(BOB, "world", 17, 64, 1);     // plot (1, 0)
        var c = lock(ALICE, "world", 33, 64, 1);   // plot (2, 0), not asked
        var plots = Set.of(new CleanupPlanner.PlotKey("world", 0, 0), new CleanupPlanner.PlotKey("world", 1, 0));
        assertEquals(Set.of(a, b), Set.copyOf(CleanupPlanner.inPlots(List.of(a, b, c), plots, 16)));
    }

    @Test
    void nothingToClean() {
        assertTrue(CleanupPlanner.inPlot(List.of(), "world", 0, 0, 16, null).isEmpty());
        assertTrue(CleanupPlanner.ownedInside(List.of(), ALICE, e -> true).isEmpty());
    }
}
