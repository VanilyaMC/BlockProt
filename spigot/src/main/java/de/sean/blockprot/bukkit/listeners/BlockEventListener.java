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
import de.sean.blockprot.bukkit.events.BlockLockOnPlaceEvent;
import de.sean.blockprot.bukkit.integrations.PluginIntegration;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import de.sean.blockprot.bukkit.nbt.PlayerSettingsHandler;
import de.sean.blockprot.bukkit.nbt.StatHandler;
import de.sean.blockprot.bukkit.util.BlockUtil;
import de.sean.blockprot.nbt.LockReturnValue;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BlockEventListener implements Listener {
    private final BlockProt blockProt;

    public BlockEventListener(@NotNull BlockProt blockProt) {
        this.blockProt = blockProt;
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (BlockProt.getDefaultConfig().isWorldExcluded(event.getBlock().getWorld())) return;
        if (!BlockProt.getDefaultConfig().isLockable(event.getBlock().getType())) return;
        // The event hasn't been cancelled already, we'll check if need
        // to cancel it manually.
        BlockNBTHandler handler = new BlockNBTHandler(event.getBlock());
        // If the block is protected by any user, prevent it from burning down.
        if (handler.isProtected()) {
            event.setCancelled(true);
        }
    }

    /**
     * Nobody but the owner breaks a locked block. This runs early (and only cancels) so that plugins
     * which look at the event afterwards, for example the spawner plugin, already see the veto.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreakCheck(BlockBreakEvent event) {
        if (BlockProt.getDefaultConfig().isWorldExcluded(event.getBlock().getWorld())) return;
        if (!BlockProt.getDefaultConfig().isLockable(event.getBlock().getType())) return; // We only want to check for lockable blocks.

        final Player player = event.getPlayer();
        BlockNBTHandler handler = new BlockNBTHandler(event.getBlock());
        if (handler.isProtected()
            && !handler.isOwner(player.getUniqueId())
            && !player.hasPermission(Permissions.BYPASS.key())
            && !PluginIntegration.ownerRightsForAll(player, event.getBlock())) {
            // Prevent unauthorized players from breaking locked blocks.
            event.setCancelled(true);
            Messages.actionBar(player, Translator.get(TranslationKey.MESSAGES__LOCKED));
        }
    }

    /**
     * The block is really gone (nobody cancelled the event): its lock goes too. Doors, gates and
     * the like keep their data in the chunk, which would otherwise stay behind.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakDone(BlockBreakEvent event) {
        if (BlockProt.getDefaultConfig().isWorldExcluded(event.getBlock().getWorld())) return;
        if (!BlockProt.getDefaultConfig().isLockable(event.getBlock().getType())) return;

        BlockNBTHandler handler = new BlockNBTHandler(event.getBlock());
        if (handler.isProtected() || !handler.getOwner().isEmpty()) {
            // Not applyToOtherContainer: the other half of a double chest stays locked.
            final String owner = handler.getOwner();
            if (!owner.isEmpty()) {
                try {
                    final UUID ownerId = UUID.fromString(owner);
                    StatHandler.removeContainer(ownerId, event.getBlock());
                    // The other half of a double chest stays locked, so it takes this block's place in the index.
                    final Material type = event.getBlock().getType();
                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                        final BlockState other = BlockUtil.getDoubleChest(event.getBlock());
                        if (other != null) {
                            StatHandler.addBlock(ownerId, other.getLocation());
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Not a player id.
                }
            }
            handler.clear();
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (BlockProt.getDefaultConfig().isWorldExcluded(event.getBlock().getWorld())) return;
        if (!event.getPlayer().hasPermission(Permissions.LOCK.key())) return;
        if (!BlockProt.getDefaultConfig().isLockable(event.getBlock().getType())) return;

        Block block = event.getBlockPlaced();
        String playerUuid = event.getPlayer().getUniqueId().toString();

        // A chest placed next to somebody else's locked chest joins it to a double chest, which would
        // open the locked half. It is refused right away, not one tick later.
        if ((block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)
            && BlockUtil.joinsLockedChest(block, event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            Messages.actionBar(event.getPlayer(), Translator.get(TranslationKey.MESSAGES__LOCKED));
            return;
        }
        BlockNBTHandler handler = new BlockNBTHandler(block);

        // We only try to lock the block if it isn't locked already.
        if (handler.isNotProtected()) {
            PlayerSettingsHandler settingsHandler = new PlayerSettingsHandler(event.getPlayer());

            // Lock the block instantly if the setting is enabled.
            if (settingsHandler.getLockOnPlace()) {
                BlockLockOnPlaceEvent lockOnPlaceEvent = new BlockLockOnPlaceEvent(event.getBlock(), event.getPlayer());

                Bukkit.getPluginManager().callEvent(lockOnPlaceEvent);
                if (!lockOnPlaceEvent.isCancelled()) {
                    LockReturnValue lock = handler.lockBlock(event.getPlayer());
                    if (!lock.success) {
                        event.setCancelled(true);
                        if (lock.reason != null) {
                            Messages.actionBar(event.getPlayer(), Translator.get(lock.reason));
                        }
                        return;
                    }

                    settingsHandler.getFriendsStream()
                        .filter(fh -> PluginIntegration.filterFriendByUuidForAll(UUID.fromString(fh.getName()), event.getPlayer(), block))
                        .forEach(handler::addFriend);
                }

                if (BlockProt.getDefaultConfig().disallowRedstoneOnPlace()) {
                    handler.getRedstoneHandler().setAll(false);
                }
            }

            // One tick later the chest knows whether it became half of a double chest. The block
            // is touched on the thread of the region that owns it.
            Bukkit.getRegionScheduler().runDelayed(
                blockProt, block.getLocation(), task -> {
                    if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                        // We cannot use BlockNBTHandler#applyToOtherContainer, because we want the
                        // data to be copied to this new chest, instead of the old chest being effectively
                        // cleared.
                        final BlockState doubleChestState = BlockUtil.getDoubleChest(block);
                        if (doubleChestState != null) {
                            final BlockNBTHandler doubleChestHandler = new BlockNBTHandler(doubleChestState.getBlock());
                            if (doubleChestHandler.isNotProtected() || doubleChestHandler.isOwner(playerUuid)) {
                                handler.mergeHandler(doubleChestHandler);
                            } else {
                                // The new half would open somebody else's locked chest. We can't cancel the
                                // event one tick later, so the new half is taken away and returned.
                                block.breakNaturally();
                                final Player placer = Bukkit.getPlayer(event.getPlayer().getUniqueId());
                                if (placer != null) Messages.actionBar(placer, Translator.get(TranslationKey.MESSAGES__NO_PERMISSION));
                            }
                        }
                    } else if (BlockProt.getDefaultConfig().isLockable(block.getType())) {
                        BlockNBTHandler later = new BlockNBTHandler(block);
                        later.setName(Translator.material(block.getType()));
                        later.applyToOtherContainer();
                    }
                },
                1
            );
        }
    }

    /**
     * A locked door, trapdoor or gate must not pop off when the block it hangs on is broken or
     * pushed away: that would break a locked block by breaking its support.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(@NotNull final BlockPhysicsEvent event) {
        final Material type = event.getBlock().getType();
        // The cheap checks come first: this event fires for every block update.
        if (!BlockProt.getDefaultConfig().isLockableBlock(type)) return;
        final String name = type.name();
        if (!(name.contains("DOOR") || name.contains("GATE") || name.contains("ANVIL"))) return;
        if (BlockProt.getDefaultConfig().isWorldExcluded(event.getBlock().getWorld())) return;
        final BlockNBTHandler handler = new BlockNBTHandler(event.getBlock());
        if (handler.isProtected()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignChanged(@NotNull final SignChangeEvent event) {
        if (BlockProt.getDefaultConfig().isLockableBlock(event.getBlock().getType())) {
            final var handler = new BlockNBTHandler(event.getBlock());
            if (handler.isProtected() && !handler.isOwner(event.getPlayer().getUniqueId()))
                event.setCancelled(true);
        }
    }
}
