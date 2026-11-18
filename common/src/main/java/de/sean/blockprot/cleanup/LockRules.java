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

/**
 * The decisions about who may lose a lock, kept free of any server class so they can be tested.
 *
 * <p>The rule behind all of them: a lock never opens because somebody else changed the land.
 * Claiming, buying, evicting or changing plot permissions leave locks alone. Locks only go when
 * the block itself goes (plot clear removes the block types it deletes), when the land leaves the
 * town (unclaim, delete, ruin), when the owner has been away for a long time, or, after the owner
 * left the town, when the grace period is over or the mayor removes them by hand.
 */
public final class LockRules {
    private static final long DAY_MILLIS = 86_400_000L;

    private LockRules() {
    }

    /**
     * Whether a lock whose owner left the town has run out.
     *
     * @param orphanedAt When the owner left the town (epoch millis), 0 if he did not.
     * @param graceDays  Days the lock stays after that; 0 or less means it never runs out by this rule.
     */
    public static boolean orphanGraceOver(final long orphanedAt, final long now, final int graceDays) {
        if (orphanedAt <= 0 || graceDays <= 0) return false;
        return now - orphanedAt > graceDays * DAY_MILLIS;
    }

    /**
     * Whether a mayor or assistant may take this lock off. A town authority never touches the lock
     * of a resident on that resident's own plot.
     *
     * @param isTownAuthority The player is the mayor or has an authority rank in the town of the block.
     * @param plotIsTownOwned The plot has no resident owner.
     * @param lockOwnerIsResident The player who locked the block is still a resident of that town.
     */
    public static boolean townAuthorityMayUnlock(final boolean isTownAuthority, final boolean plotIsTownOwned,
                                                 final boolean lockOwnerIsResident) {
        if (!isTownAuthority) return false;
        // Town land: the town decides. A lock of somebody who left: nobody is there to look after it.
        return plotIsTownOwned || !lockOwnerIsResident;
    }
}
