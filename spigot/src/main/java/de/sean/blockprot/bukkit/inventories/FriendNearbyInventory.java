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

package de.sean.blockprot.bukkit.inventories;

import de.sean.blockprot.bukkit.TranslationKey;
import de.sean.blockprot.bukkit.Translator;
import de.sean.blockprot.bukkit.integrations.PluginIntegration;
import de.sean.blockprot.bukkit.integrations.TownyClaims;
import de.sean.blockprot.bukkit.nbt.FriendSupportingHandler;
import de.sean.blockprot.nbt.FriendModifyAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Players to tap instead of typing a name: whoever is online within a short walk of the block,
 * plus the online members of the player's own town. Works the same for Bedrock players.
 */
public class FriendNearbyInventory extends BlockProtInventory {
    private static final double RADIUS = 48.0;
    private final int maxSkulls = getSize() - 2;

    @Override
    int getSize() {
        return InventoryConstants.tripleLine;
    }

    @Override
    @NotNull String getTranslatedInventoryName() {
        return Translator.get(TranslationKey.INVENTORIES__FRIENDS__NEARBY);
    }

    @Override
    public void onClick(@NotNull InventoryClickEvent event, @NotNull InventoryState state) {
        final Player player = (Player) event.getWhoClicked();
        final ItemStack item = event.getCurrentItem();
        if (item == null) return;
        switch (item.getType()) {
            case BLACK_STAINED_GLASS_PANE -> closeAndOpen(player, new FriendManageInventory().fill(player));
            case PLAYER_HEAD, SKELETON_SKULL -> {
                final var meta = (SkullMeta) item.getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    final UUID id = meta.getOwningPlayer().getUniqueId();
                    modifyFriendsForAction(player, id, FriendModifyAction.ADD_FRIEND);
                    closeAndOpen(player, new FriendManageInventory().fill(player));
                }
            }
            default -> closeAndOpen(player, null);
        }
        event.setCancelled(true);
    }

    @Override
    public void onClose(@NotNull InventoryCloseEvent event, @NotNull InventoryState state) {
    }

    public Inventory fill(@NotNull Player player) {
        final InventoryState state = InventoryState.get(player.getUniqueId());
        if (state == null) return this.inventory;
        final FriendSupportingHandler<?> handler =
            getFriendSupportingHandler(state.friendSearchState, player, state.getBlock());
        final Set<String> already = new LinkedHashSet<>();
        if (handler != null) handler.getFriends().forEach(f -> already.add(f.getName()));

        final Location center = state.getBlock() != null ? state.getBlock().getLocation() : player.getLocation();
        final Set<Player> candidates = new LinkedHashSet<>();
        for (final Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(center.getWorld())) continue;
            if (other.getLocation().distanceSquared(center) <= RADIUS * RADIUS) candidates.add(other);
        }
        candidates.addAll(TownyClaims.townmatesOnline(player));
        candidates.remove(player);

        state.friendResultCache.clear();
        for (final Player other : candidates) {
            if (state.friendResultCache.size() >= maxSkulls) break;
            if (already.contains(other.getUniqueId().toString())) continue;
            if (state.friendSearchState == InventoryState.FriendSearchState.FRIEND_SEARCH && state.getBlock() != null
                && !PluginIntegration.filterFriendByUuidForAll(other.getUniqueId(), player, state.getBlock())) {
                continue;
            }
            state.friendResultCache.add(other.getUniqueId());
        }
        for (int i = 0; i < state.friendResultCache.size(); i++) {
            this.setItemStack(i, Material.SKELETON_SKULL, state.friendResultCache.get(i).toString());
        }
        setBackButton();
        for (int i = 0; i < state.friendResultCache.size(); i++) {
            setPlayerSkull(i, profileFor(state.friendResultCache.get(i)));
        }
        return this.inventory;
    }
}
