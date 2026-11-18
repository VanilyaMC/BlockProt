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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Removes locks from the world. Land events can fire on any thread, but a block may only be
 * touched on the thread of the region that owns it, so every removal is handed to that region
 * (loading the chunk first when it is not loaded).
 */
public final class LockCleaner {
    private LockCleaner() {
    }

    /** What happens to a lock. */
    private enum Action {
        /** The lock is removed. */
        UNLOCK,
        /** The lock stays, but it is marked as left behind by an owner who left the town. */
        ORPHAN,
        /** The mark is taken off again (the owner is back). */
        UNMARK
    }

    /**
     * Removes the given locks. Returns at once; the work happens on the region threads.
     *
     * @return How many removals were queued.
     */
    public static int clear(@NotNull final Collection<LockEntry> entries) {
        return clear(entries, null);
    }

    /**
     * Removes the given locks, but only on blocks of a type {@code typeFilter} accepts
     * (a plot clear only removes the locks of the block types it removes itself).
     */
    public static int clear(@NotNull final Collection<LockEntry> entries, @Nullable final Predicate<Material> typeFilter) {
        return run(entries, typeFilter, Action.UNLOCK);
    }

    /**
     * Marks the given locks as left behind: they stay locked, and run out after the grace period.
     */
    public static int markOrphaned(@NotNull final Collection<LockEntry> entries) {
        return run(entries, null, Action.ORPHAN);
    }

    /** Takes the "left behind" mark off the given locks. */
    public static int unmarkOrphaned(@NotNull final Collection<LockEntry> entries) {
        return run(entries, null, Action.UNMARK);
    }

    private static int run(final Collection<LockEntry> entries, @Nullable final Predicate<Material> typeFilter,
                           final Action action) {
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
            // The chunk is kept loaded until the region thread has done its work.
            world.addPluginChunkTicket(chunkX, chunkZ, BlockProt.getInstance());
            world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, error) -> {
                if (error != null) {
                    world.removePluginChunkTicket(chunkX, chunkZ, BlockProt.getInstance());
                    BlockProt.getInstance().getLogger().log(Level.WARNING, "Could not load a chunk to change a lock", error);
                    return;
                }
                if (chunk == null) {
                    // The chunk does not exist (never generated or deleted): nothing to unlock.
                    world.removePluginChunkTicket(chunkX, chunkZ, BlockProt.getInstance());
                    StatHandler.removeContainer(entry.owner(), entry.world(), entry.x(), entry.y(), entry.z());
                    return;
                }
                Bukkit.getRegionScheduler().execute(BlockProt.getInstance(), world, chunkX, chunkZ, () -> {
                    try {
                        apply(world, entry, typeFilter, action);
                    } finally {
                        world.removePluginChunkTicket(chunkX, chunkZ, BlockProt.getInstance());
                    }
                });
            });
            queued++;
        }
        return queued;
    }

    /** Must run on the thread of the region that owns the block. */
    private static void apply(@NotNull final World world, @NotNull final LockEntry entry,
                              @Nullable final Predicate<Material> typeFilter, @NotNull final Action action) {
        try {
            final Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
            if (typeFilter != null && !typeFilter.test(block.getType())) {
                return; // The block stays, so does its lock.
            }
            if (!BlockProt.getDefaultConfig().isLockable(block.getType())) {
                // Whatever the block is now, the index must not keep the entry.
                StatHandler.removeContainer(entry.owner(), entry.world(), entry.x(), entry.y(), entry.z());
                return;
            }
            final BlockNBTHandler handler = new BlockNBTHandler(block);
            if (!handler.isOwner(entry.owner())) {
                // A stale entry: somebody else's lock (or none) stands here now. Only the index is fixed.
                StatHandler.removeContainer(entry.owner(), entry.world(), entry.x(), entry.y(), entry.z());
                return;
            }
            if (action == Action.UNLOCK) {
                handler.unlock();
                StatHandler.removeContainer(entry.owner(), entry.world(), entry.x(), entry.y(), entry.z());
            } else if (action == Action.ORPHAN) {
                if (handler.getOrphanedAt() == 0L) {
                    handler.setOrphanedAt(System.currentTimeMillis());
                    handler.applyToOtherContainer();
                }
            } else if (handler.getOrphanedAt() != 0L) {
                handler.setOrphanedAt(0L);
                handler.applyToOtherContainer();
            }
        } catch (RuntimeException e) {
            BlockProt.getInstance().getLogger().log(Level.WARNING,
                "Could not change the lock at " + entry.world() + " " + entry.x() + " " + entry.y() + " " + entry.z(), e);
        }
    }
}
