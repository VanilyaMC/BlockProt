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


package de.sean.blockprot.bukkit.nbt;

import de.sean.blockprot.bukkit.BlockProt;
import de.sean.blockprot.cleanup.LockEntry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.logging.Level;

/**
 * Removes locks from the world. Land events can fire on any thread, but a block may only be
 * touched on the thread of the region that owns it, so every removal is handed to that region
 * (loading the chunk first when it is not loaded).
 */
public final class LockCleaner {
    private LockCleaner() {
    }

    /**
     * Removes the given locks. Returns at once; the work happens on the region threads.
     *
     * @return How many removals were queued.
     */
    public static int clear(@NotNull final Collection<LockEntry> entries) {
        int queued = 0;
        for (final LockEntry entry : entries) {
            final World world = Bukkit.getWorld(entry.world());
            if (world == null) {
                // The world is gone, so the lock is gone with it.
                StatHandler.removeContainer(entry.owner(), entry.world(), entry.x(), entry.y(), entry.z());
                continue;
            }
            final int chunkX = entry.x() >> 4;
            final int chunkZ = entry.z() >> 4;
            world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, error) -> {
                if (error != null || chunk == null) {
                    // The chunk does not exist any more (never generated or deleted): nothing to unlock.
                    StatHandler.removeContainer(entry.owner(), entry.world(), entry.x(), entry.y(), entry.z());
                    return;
                }
                Bukkit.getRegionScheduler().execute(BlockProt.getInstance(), world, chunkX, chunkZ,
                    () -> clearNow(world, entry));
            });
            queued++;
        }
        return queued;
    }

    /** Must run on the thread of the region that owns the block. */
    private static void clearNow(@NotNull final World world, @NotNull final LockEntry entry) {
        try {
            final Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
            if (BlockProt.getDefaultConfig().isLockable(block.getType())) {
                new BlockNBTHandler(block).unlock();
            }
        } catch (RuntimeException e) {
            BlockProt.getInstance().getLogger().log(Level.WARNING,
                "Could not remove the lock at " + entry.world() + " " + entry.x() + " " + entry.y() + " " + entry.z(), e);
        }
        // Whatever the block is now, the index must not keep the entry.
        StatHandler.removeContainer(entry.owner(), entry.world(), entry.x(), entry.y(), entry.z());
    }
}
