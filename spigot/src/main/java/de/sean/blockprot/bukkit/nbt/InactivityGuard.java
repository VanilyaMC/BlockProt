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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks of players who have not joined for a long time stop protecting anything.
 * The answer is cached for a few minutes, because the last-seen time comes from the
 * player's data file.
 */
public final class InactivityGuard {
    private static final long CACHE_MILLIS = 10 * 60 * 1000L;

    private record Answer(boolean expired, long checkedAt) {
    }

    private static final ConcurrentHashMap<UUID, Answer> cache = new ConcurrentHashMap<>();

    private InactivityGuard() {
    }

    /**
     * Whether a lock owned by {@code ownerUuid} no longer counts. Owners who are online,
     * who never joined (unknown), or whose id is not a UUID always count.
     */
    public static boolean isExpired(@NotNull final String ownerUuid) {
        final int days = BlockProt.getDefaultConfig().getLockExpiryDays();
        if (days <= 0 || ownerUuid.isEmpty()) return false;

        final UUID uuid;
        try {
            uuid = UUID.fromString(ownerUuid);
        } catch (IllegalArgumentException e) {
            return false;
        }

        final long now = System.currentTimeMillis();
        final Answer cached = cache.get(uuid);
        if (cached != null && now - cached.checkedAt() < CACHE_MILLIS) return cached.expired();

        boolean expired = false;
        final OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
        if (!owner.isOnline()) {
            final long lastSeen = owner.getLastSeen();
            expired = lastSeen > 0 && now - lastSeen > days * 86_400_000L;
        }
        cache.put(uuid, new Answer(expired, now));
        return expired;
    }

    /** Forget the cached answers, after a reload or when somebody joins. */
    public static void forget(@NotNull final UUID uuid) {
        cache.remove(uuid);
    }

    public static void clear() {
        cache.clear();
    }
}
