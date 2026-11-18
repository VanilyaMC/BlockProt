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
import de.sean.blockprot.bukkit.Permissions;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Lets the shop plugin ask BlockProt whether a player may build a shop on, or break the sign of,
 * a container. Shops keep working like before: customers never open the chest (the shop plugin moves
 * the items itself), so a locked shop chest stays usable, and the lock decides who may set up or
 * remove a shop on it.
 */
public final class ChestShopIntegration extends PluginIntegration implements Listener {
    private static final String PROTECTION_EVENT = "com.Acrobot.ChestShop.Events.Protection.ProtectionCheckEvent";

    private boolean enabled = false;

    public ChestShopIntegration() {
        super("chestshop");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public @Nullable Plugin getPlugin() {
        return BlockProt.getInstance().getPlugin("ChestShop");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void enable() {
        final Plugin plugin = getPlugin();
        if (plugin == null || !plugin.isEnabled()) return;

        try {
            final Class<? extends Event> eventClass = (Class<? extends Event>)
                Class.forName(PROTECTION_EVENT, true, plugin.getClass().getClassLoader());
            final Method getBlock = eventClass.getMethod("getBlock");
            final Method getPlayer = eventClass.getMethod("getPlayer");
            final Method getResult = eventClass.getMethod("getResult");
            final Method setResult = eventClass.getMethod("setResult", Event.Result.class);
            final Method checkCanManage = eventClass.getMethod("checkCanManage");

            BlockProt.getInstance().getServer().getPluginManager().registerEvent(
                eventClass, this, EventPriority.NORMAL, (listener, event) -> {
                    try {
                        if (!eventClass.isInstance(event)) return;
                        if (getResult.invoke(event) == Event.Result.DENY) return;
                        final Block block = (Block) getBlock.invoke(event);
                        final Player player = (Player) getPlayer.invoke(event);
                        final boolean manage = (Boolean) checkCanManage.invoke(event);
                        if (!mayUse(player, block, manage)) {
                            setResult.invoke(event, Event.Result.DENY);
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        BlockProt.getInstance().getLogger().log(Level.WARNING, "ChestShop protection check failed", e);
                    }
                }, BlockProt.getInstance(), false);
            enabled = true;
        } catch (ReflectiveOperationException e) {
            BlockProt.getInstance().getLogger().warning(
                "ChestShop is installed, but its protection event was not found. Shops are not checked against locks.");
        }
    }

    /** Whether the player may set up or remove a shop on this container. */
    static boolean mayUse(@NotNull final Player player, @NotNull final Block block, final boolean manage) {
        if (!BlockProt.getDefaultConfig().isLockable(block.getType())) return true;
        if (BlockProt.getDefaultConfig().isWorldExcluded(block.getWorld())) return true;
        final BlockNBTHandler handler = new BlockNBTHandler(block);
        if (handler.isNotProtected()) return true;
        if (handler.isOwner(player.getUniqueId()) || player.hasPermission(Permissions.BYPASS.key())) return true;
        final var friend = handler.getFriend(player.getUniqueId().toString());
        if (friend.isEmpty()) return false;
        return manage ? friend.get().canWrite() : friend.get().canRead();
    }
}
