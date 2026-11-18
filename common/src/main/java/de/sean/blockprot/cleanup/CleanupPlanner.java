/*
 * Copyright (C) 2021 - 2025 spnda
 * This file is part of BlockProt <https://github.com/spnda/BlockProt>.
 *
 * BlockProt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BlockProt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BlockProt.  If not, see <http://www.gnu.org/licenses/>.
 */


package de.sean.blockprot.cleanup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Decides which locks have to go when land changes hands. It only works on the lock
 * index (see {@link LockEntry}); the caller removes the chosen locks on the right thread.
 */
public final class CleanupPlanner {
    private CleanupPlanner() {
    }

    /**
     * The locks inside one plot.
     *
     * @param plotX    Plot coordinate on the x axis, as the land plugin counts plots.
     * @param plotZ    Plot coordinate on the z axis.
     * @param plotSize Blocks per plot side (16 by default).
     * @param keep     Owners whose locks stay (for example the new plot owner), may be {@code null}.
     */
    public static List<LockEntry> inPlot(final Collection<LockEntry> all, final String world,
                                         final int plotX, final int plotZ, final int plotSize,
                                         final Predicate<UUID> keep) {
        return all.stream()
            .filter(e -> e.world().equals(world))
            .filter(e -> Math.floorDiv(e.x(), plotSize) == plotX && Math.floorDiv(e.z(), plotSize) == plotZ)
            .filter(e -> keep == null || !keep.test(e.owner()))
            .toList();
    }

    /** One plot as a land plugin counts them. */
    public record PlotKey(String world, int x, int z) {
    }

    /**
     * The locks inside any of several plots, found in one pass over the index (a ruined or deleted
     * town has many plots).
     */
    public static List<LockEntry> inPlots(final Collection<LockEntry> all, final java.util.Set<PlotKey> plots,
                                          final int plotSize) {
        return all.stream()
            .filter(e -> plots.contains(new PlotKey(e.world(), Math.floorDiv(e.x(), plotSize), Math.floorDiv(e.z(), plotSize))))
            .toList();
    }

    /**
     * The locks one player holds inside a town, for when the player leaves or is kicked.
     *
     * @param insideTown Tells whether a lock position belongs to the town right now.
     */
    public static List<LockEntry> ownedInside(final Collection<LockEntry> all, final UUID owner,
                                              final Predicate<LockEntry> insideTown) {
        return all.stream()
            .filter(e -> e.owner().equals(owner))
            .filter(insideTown)
            .toList();
    }
}
