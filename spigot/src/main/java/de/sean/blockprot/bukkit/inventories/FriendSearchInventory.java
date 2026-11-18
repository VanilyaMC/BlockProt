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

import de.sean.blockprot.bukkit.Messages;
import de.sean.blockprot.bukkit.TranslationKey;
import de.sean.blockprot.bukkit.Translator;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FriendSearchInventory {
    /**
     * Asks for a player name in a dialog. A name that matches a known player exactly adds that
     * player right away; anything else shows the list of similar names.
     */
    public static void openNameDialog(@NotNull final Player requestingPlayer) {
        TextDialog.ask(
            requestingPlayer,
            Translator.get(TranslationKey.INVENTORIES__FRIENDS__SEARCH),
            Translator.get(TranslationKey.DIALOG__PLAYER_NAME),
            "",
            32,
            text -> onSubmit(requestingPlayer, text),
            () -> reopenFriendMenu(requestingPlayer));
    }

    private static void onSubmit(@NotNull final Player player, @NotNull final String text) {
        final InventoryState state = InventoryState.get(player.getUniqueId());
        if (state == null || text.isEmpty()) {
            return;
        }
        final var inventory = new FriendSearchResultInventory();
        if (inventory.addExactMatch(player, text)) {
            return;
        }
        final var results = inventory.fill(player, text);
        if (results == null) {
            Messages.actionBar(player, Translator.get(TranslationKey.MESSAGES__PLAYER_NOT_FOUND));
            return;
        }
        player.openInventory(results);
    }

    private static void reopenFriendMenu(@NotNull final Player player) {
        if (InventoryState.get(player.getUniqueId()) == null) return;
        final var menu = new FriendManageInventory().fill(player);
        if (menu != null) player.openInventory(menu);
    }
}
