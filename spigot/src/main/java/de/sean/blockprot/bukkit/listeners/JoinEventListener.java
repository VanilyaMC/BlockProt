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
import de.sean.blockprot.bukkit.inventories.InventoryState;
import de.sean.blockprot.bukkit.nbt.InactivityGuard;
import de.sean.blockprot.bukkit.nbt.PlayerSettingsHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinEventListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        InactivityGuard.forget(player.getUniqueId());
        if (BlockProt.getDefaultConfig().publicIsFriendByDefault() && !player.hasPlayedBefore()) {
            new PlayerSettingsHandler(player).addEveryoneAsFriend();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // The menu state is only useful while the player is here.
        InventoryState.remove(event.getPlayer().getUniqueId());
        InactivityGuard.forget(event.getPlayer().getUniqueId());
    }
}
