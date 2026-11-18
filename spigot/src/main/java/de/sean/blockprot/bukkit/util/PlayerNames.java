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

package de.sean.blockprot.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Finds a player by a typed name. Bedrock players carry a dot in front of their name (".Name"). */
public final class PlayerNames {
    private PlayerNames() {
    }

    /**
     * The player whose name is exactly {@code typed} (case does not matter), online or known to the
     * server. A name typed without the Bedrock dot also finds the Bedrock player.
     */
    public static @Nullable UUID resolve(@NotNull final String typed) {
        final String name = typed.strip();
        if (name.isEmpty()) return null;
        final UUID direct = lookup(name);
        if (direct != null) return direct;
        if (!name.startsWith(".")) return lookup("." + name);
        return null;
    }

    private static @Nullable UUID lookup(@NotNull final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (final Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p.getUniqueId();
        }
        final OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getName() != null && cached.getName().equalsIgnoreCase(name)) {
            return cached.getUniqueId();
        }
        return null;
    }
}
