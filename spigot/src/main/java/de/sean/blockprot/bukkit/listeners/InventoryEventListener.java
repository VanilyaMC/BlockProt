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
import de.sean.blockprot.bukkit.Messages;
import de.sean.blockprot.bukkit.Permissions;
import de.sean.blockprot.bukkit.TranslationKey;
import de.sean.blockprot.bukkit.Translator;
import de.sean.blockprot.bukkit.events.BlockAccessEvent;
import de.sean.blockprot.bukkit.inventories.BlockProtInventory;
import de.sean.blockprot.bukkit.inventories.InventoryState;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import de.sean.blockprot.bukkit.nbt.FriendHandler;
import de.sean.blockprot.bukkit.util.BlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class InventoryEventListener implements Listener {
    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();
        final InventoryState state = InventoryState.get(player.getUniqueId());
        if (state != null) {
            // We have some sort of inventory state, so we'll assume the player is currently in
            // some of our inventories. We'll check from which inventory the clicked item actually is,
            // so that the onClick method is only called for menu clicks, but we'll still cancel all
            // clicks in the players inventory.
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder instanceof BlockProtInventory) {
                // While getInventory always returns the top inventory, getClickedInventory returns the
                // inventory in which this event occurred.
                final var clickedInventory = event.getClickedInventory();
                if (clickedInventory != null && clickedInventory.getHolder() instanceof BlockProtInventory bpInventory) {
                    bpInventory.onClick(event, state);
                } else {
                    event.setCancelled(true); // Don't allow interaction in a menu.
                }
            }
        } else {
            // No state, let's check if they're in some block inventory.
            final Boolean denied = denial(player, event.getInventory().getHolder(), true);
            if (denied != null) {
                event.setCancelled(true);
                if (denied) player.closeInventory();
            }
        }
    }

    /**
     * A friend who may only read must not be able to drag items into the container either.
     */
    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        final InventoryState state = InventoryState.get(player.getUniqueId());
        if (state != null && event.getInventory().getHolder() instanceof BlockProtInventory) {
            event.setCancelled(true); // Never drag anything around in a menu.
            return;
        }
        if (denial(player, event.getInventory().getHolder(), true) != null) {
            event.setCancelled(true);
        }
    }

    /** The lockable blocks behind an inventory holder, both halves of a double chest. */
    private static java.util.List<Block> lockableBlocksOf(InventoryHolder holder) {
        return BlockUtil.blocksOf(holder).stream()
            .filter(b -> BlockProt.getDefaultConfig().isLockable(b.getType())
                && !BlockProt.getDefaultConfig().isWorldExcluded(b.getWorld()))
            .toList();
    }

    /**
     * Whether the player may take part in this container's inventory: true when no half is locked,
     * or the player owns, or is a friend of, every locked half.
     * @return null when everything is fine, else the reason to cancel: true = also close the window.
     */
    private static Boolean denial(Player player, InventoryHolder holder, boolean writing) {
        final String uuid = player.getUniqueId().toString();
        for (final Block block : lockableBlocksOf(holder)) {
            final BlockNBTHandler handler = new BlockNBTHandler(block);
            if (!handler.isProtected() || handler.isOwner(uuid) || player.hasPermission(Permissions.BYPASS.key())) continue;
            final var friend = handler.getFriend(uuid);
            if (friend.isEmpty() || !friend.get().canRead()) return true;
            if (writing && !friend.get().canWrite()) return false;
        }
        return null;
    }

    @EventHandler
    public void onLecternClick(@NotNull PlayerTakeLecternBookEvent event) {
        final var handler = new BlockNBTHandler(event.getLectern().getBlock());
        final var uuid = event.getPlayer().getUniqueId();

        if (handler.isProtected() && !handler.isOwner(uuid)) {
            // The player taking the book is not the owner.
            final var friend = handler.getFriend(uuid.toString());
            if (friend.isPresent()) {
                if (!friend.get().canWrite()) {
                    event.setCancelled(true);
                } else if (!friend.get().canRead()) {
                    // Not a friend who should be able to access the inventory
                    event.setCancelled(true);
                    event.getPlayer().closeInventory();
                }
            } else {
                // Not a friend; close the inventory
                event.setCancelled(true);
                event.getPlayer().closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        final InventoryState state = InventoryState.get(player.getUniqueId());
        if (state == null) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockProtInventory) {
            ((BlockProtInventory) holder).onClose(event, state);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        // Double-check any inventories and close them if permissions are not valid.
        String playerUuid = event.getPlayer().getUniqueId().toString();
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockProtInventory) {
            // We've got one of our inventories, check the inventory state if the player can access
            // the block. Also fixes issues when the block gets destroyed.
            InventoryState state = InventoryState.get(playerUuid);
            if (state == null || state.getBlock() == null) return;
            try {
                BlockNBTHandler handler = new BlockNBTHandler(state.getBlock());
                Optional<FriendHandler> friend = handler.getFriend(playerUuid);
                if (!(handler.isNotProtected()
                    || handler.isOwner(playerUuid)
                    || (friend.isPresent() && friend.get().isManager())
                    || event.getPlayer().hasPermission(Permissions.ADMIN.key())
                    || event.getPlayer().hasPermission(Permissions.INFO.key()))) {
                    event.setCancelled(true);
                    sendMessage(event.getPlayer(), Translator.get(TranslationKey.MESSAGES__NO_PERMISSION));
                }
            } catch (RuntimeException ignored) {
            }
        } else if ((holder instanceof Container || holder instanceof DoubleChest)
            && event.getPlayer() instanceof Player player) {
            for (final Block block : lockableBlocksOf(holder)) {
                BlockAccessEvent accessEvent = new BlockAccessEvent(block, player);
                Bukkit.getPluginManager().callEvent(accessEvent);
                if (accessEvent.isCancelled()) {
                    event.setCancelled(true);
                    sendMessage(player, Translator.get(TranslationKey.MESSAGES__NO_PERMISSION));
                    return;
                }
                BlockNBTHandler handler = new BlockNBTHandler(block);
                if (!accessEvent.shouldBypassProtections()
                    && !(handler.canAccess(player.getUniqueId().toString()) || player.hasPermission(Permissions.BYPASS.key()))) {
                    event.setCancelled(true);
                    sendMessage(player, Translator.get(TranslationKey.MESSAGES__LOCKED));
                    return;
                }
            }
        }
    }

    private void sendMessage(@NotNull HumanEntity player, @NotNull String component) {
        if (!(player instanceof Player online)) return;
        Messages.actionBar(online, component);
    }
}
