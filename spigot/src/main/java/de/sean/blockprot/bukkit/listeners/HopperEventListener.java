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

package de.sean.blockprot.bukkit.listeners;

import de.sean.blockprot.bukkit.BlockProt;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import de.sean.blockprot.bukkit.util.BlockUtil;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Items moving between containers by themselves: hoppers, hopper minecarts, droppers.
 * <ul>
 *   <li>A locked container gives items only to a container locked by the same player. A stranger's
 *       hopper (or any unlocked one), a hopper minecart and the like get nothing out of it.</li>
 *   <li>Items going INTO a locked container are let in unless the one pushing is locked by somebody
 *       else: an unlocked hopper tells us nothing about who built it, and the owner's own farm
 *       hoppers must keep working.</li>
 *   <li>The owner can switch the first rule off for one lock (the hopper protection setting).</li>
 * </ul>
 * Everything here only reads: changing a container while an item is half moved loses items.
 */
public class HopperEventListener implements Listener {
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        final InventoryHolder sourceHolder = event.getSource().getHolder(false);
        final InventoryHolder destinationHolder = event.getDestination().getHolder(false);
        if (sourceHolder == null && destinationHolder == null) return;
        if (sourceHolder != null && BlockProt.getDefaultConfig().isWorldExcluded(sourceHolder)) return;

        final BlockNBTHandler source = lockOf(sourceHolder);
        if (source == null) {
            // Nothing is locked on the giving side. Whoever receives is the receiver's business.
            return;
        }
        if (!source.getRedstoneHandler().getHopperProtection()) return; // the owner opened it up

        final BlockNBTHandler destination = lockOf(destinationHolder);
        if (destination != null && destination.getOwner().equals(source.getOwner())) return; // one owner, both ends
        event.setCancelled(true);
    }

    /** The lock data of the locked half of the holder's block(s), or null when none of it is locked. */
    private static @Nullable BlockNBTHandler lockOf(@Nullable final InventoryHolder holder) {
        if (holder == null) return null;
        for (final Block block : BlockUtil.blocksOf(holder)) {
            if (!BlockProt.getDefaultConfig().isLockable(block.getType())) continue;
            final BlockNBTHandler handler = BlockNBTHandler.view(block);
            if (handler.isProtected()) return handler;
        }
        return null;
    }
}
