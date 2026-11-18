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

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * The few Towny questions other integrations ask. Kept in one class so that nothing else has to
 * load Towny classes: every method is safe to call when Towny is not installed.
 */
public final class TownyClaims {
    private TownyClaims() {
    }

    private static boolean townyThere() {
        return Bukkit.getPluginManager().isPluginEnabled("Towny");
    }

    /**
     * True when the block lies in a claimed town plot and Towny lets this player break it
     * (the same check Towny does when the player breaks a block there).
     */
    static boolean mayBreakInTown(@NotNull final Player player, @NotNull final Block block) {
        if (!townyThere()) return false;
        try {
            final TownBlock townBlock = TownyAPI.getInstance().getTownBlock(block.getLocation());
            if (townBlock == null || !townBlock.hasTown()) return false;
            return PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getType(),
                TownyPermission.ActionType.DESTROY);
        } catch (NoClassDefFoundError | RuntimeException e) {
            return false;
        }
    }

    /** The online members of the player's own town (nobody when Towny is missing or he has no town). */
    public static @NotNull List<Player> townmatesOnline(@NotNull final Player player) {
        final List<Player> out = new ArrayList<>();
        if (!townyThere()) return out;
        try {
            final Resident resident = TownyAPI.getInstance().getResident(player);
            if (resident == null || !resident.hasTown()) return out;
            final Town town = resident.getTownOrNull();
            if (town == null) return out;
            for (final Resident other : town.getResidents()) {
                final Player online = other.getPlayer();
                if (online != null) out.add(online);
            }
        } catch (NoClassDefFoundError | RuntimeException e) {
            // Towny missing or changed: no town suggestions.
        }
        return out;
    }
}
