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


package de.sean.blockprot.bukkit.integrations;

import de.sean.blockprot.bukkit.BlockProt;
import de.sean.blockprot.bukkit.events.BlockAccessMenuEvent;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Spawners that the spawner plugin records an owner for follow that plugin's ownership:
 * only the owner of a spawner can lock it, the owner can always break it, and spawners
 * nobody placed (the ones the world made) cannot be locked at all, because those are
 * meant to be taken by whoever uses the breaker item.
 */
public final class SpawnerIntegration extends PluginIntegration implements Listener {
    /** Where the spawner plugin keeps the placer of a spawner, on the block itself. */
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("atlasspawner", "owner");

    private boolean enabled = false;

    public SpawnerIntegration() {
        super("spawner");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void enable() {
        final Plugin plugin = getPlugin();
        if (plugin == null || !plugin.isEnabled()) return;
        this.registerListener(this);
        enabled = true;
    }

    @Override
    public @Nullable Plugin getPlugin() {
        return BlockProt.getInstance().getPlugin("AtlasSpawner");
    }

    /** The recorded owner of the spawner, or null when nobody placed it (or it is not a spawner). */
    public static @Nullable UUID spawnerOwner(@NotNull final Block block) {
        if (block.getType() != Material.SPAWNER) return null;
        if (!(block.getState(false) instanceof TileState state)) return null;
        final String raw = state.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected boolean hasOwnerRights(@NotNull final Player player, @NotNull final Block block) {
        return player.getUniqueId().equals(spawnerOwner(block));
    }

    @EventHandler
    public void onMenu(@NotNull final BlockAccessMenuEvent event) {
        final Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        final BlockNBTHandler handler = new BlockNBTHandler(block);
        if (handler.isProtected()) {
            // The spawner owner takes the lock off a spawner that somebody else locked.
            if (hasOwnerRights(event.getPlayer(), block)) {
                event.addPermissions(BlockAccessMenuEvent.MenuPermission.LOCK,
                    BlockAccessMenuEvent.MenuPermission.INFO,
                    BlockAccessMenuEvent.MenuPermission.FORCE_UNLOCK);
            }
            return;
        }
        // Locking: only for the spawner owner.
        if (!hasOwnerRights(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }
}
