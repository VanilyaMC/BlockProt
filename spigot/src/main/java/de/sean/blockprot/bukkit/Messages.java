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

package de.sean.blockprot.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sending the translated texts, which use the legacy colour codes, to players.
 */
public final class Messages {
    private Messages() {
    }

    /** Turns a translated text with colour codes into a component. */
    public static @NotNull Component text(@NotNull final String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    /** Short messages go above the hotbar. */
    public static void actionBar(@NotNull final Player player, @NotNull final String legacy) {
        if (legacy.isEmpty()) return;
        player.sendActionBar(text(legacy));
    }

    public static void chat(@NotNull final CommandSender sender, @NotNull final String legacy) {
        if (legacy.isEmpty()) return;
        sender.sendMessage(text(legacy));
    }

    /** A chat line the player can click to run a command, with an optional hover text. */
    public static void clickable(@NotNull final Player player, @NotNull final String legacy,
                                 @NotNull final String command, @Nullable final String hover) {
        Component message = text(legacy).clickEvent(ClickEvent.runCommand(command));
        if (hover != null && !hover.isEmpty()) message = message.hoverEvent(HoverEvent.showText(text(hover)));
        player.sendMessage(message);
    }
}
