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


package de.sean.blockprot.bukkit.commands;

import de.sean.blockprot.bukkit.BlockProt;
import de.sean.blockprot.bukkit.BlockProtAPI;
import de.sean.blockprot.bukkit.Messages;
import de.sean.blockprot.bukkit.Permissions;
import de.sean.blockprot.bukkit.TranslationKey;
import de.sean.blockprot.bukkit.Translator;
import de.sean.blockprot.bukkit.integrations.PluginIntegration;
import de.sean.blockprot.bukkit.inventories.InventoryState;
import de.sean.blockprot.bukkit.inventories.UserSettingsInventory;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import de.sean.blockprot.bukkit.nbt.PlayerSettingsHandler;
import de.sean.blockprot.bukkit.util.PlayerNames;
import de.sean.blockprot.nbt.FriendModifyAction;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /kilit}: the whole lock menu without sneaking and right-clicking, for players
 * (Bedrock players above all) who cannot do that comfortably.
 * <ul>
 *   <li>{@code /kilit}: opens the lock menu of the block you look at.</li>
 *   <li>{@code /kilit ekle <isim>}: lets that player use the block you look at.</li>
 *   <li>{@code /kilit cikar <isim>}: takes that player off the block you look at.</li>
 *   <li>{@code /kilit ayar}: your own settings.</li>
 * </ul>
 */
public final class KilitCommand implements TabExecutor {
    private static final int REACH = 6;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, Translator.get(TranslationKey.MESSAGES__PLAYERS_ONLY));
            return true;
        }
        if (!player.hasPermission(Permissions.LOCK.key())) {
            Messages.chat(player, Translator.get(TranslationKey.MESSAGES__NO_PERMISSION));
            return true;
        }

        final String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ENGLISH);
        switch (sub) {
            case "ayar", "ayarlar", "settings" -> {
                InventoryState state = new InventoryState(null);
                state.friendSearchState = InventoryState.FriendSearchState.DEFAULT_FRIEND_SEARCH;
                InventoryState.set(player.getUniqueId(), state);
                player.openInventory(new UserSettingsInventory().fill(player));
                new PlayerSettingsHandler(player).setHasPlayerInteractedWithMenu(true);
            }
            case "ekle", "arkadas", "arkadaş", "friend", "add" -> {
                if (args.length < 2) {
                    Messages.chat(player, Translator.get(TranslationKey.MESSAGES__KILIT_USAGE));
                    return true;
                }
                changeFriend(player, args[1], true);
            }
            case "cikar", "çıkar", "remove" -> {
                if (args.length < 2) {
                    Messages.chat(player, Translator.get(TranslationKey.MESSAGES__KILIT_USAGE));
                    return true;
                }
                changeFriend(player, args[1], false);
            }
            default -> openMenu(player);
        }
        return true;
    }

    private @Nullable Block lookedAtBlock(@NotNull Player player) {
        final Block block = player.getTargetBlockExact(REACH);
        if (block == null || !BlockProt.getDefaultConfig().isLockable(block.getType())
            || BlockProt.getDefaultConfig().isWorldExcluded(block.getWorld())) {
            Messages.chat(player, Translator.get(TranslationKey.MESSAGES__LOOK_AT_BLOCK));
            return null;
        }
        return block;
    }

    private void openMenu(@NotNull Player player) {
        final Block block = lookedAtBlock(player);
        if (block == null) return;
        final Inventory inv = BlockProtAPI.getInstance().getLockInventoryForBlock(block, player);
        if (inv == null) {
            Messages.chat(player, Translator.get(TranslationKey.MESSAGES__NO_PERMISSION));
            return;
        }
        new PlayerSettingsHandler(player).setHasPlayerInteractedWithMenu(true);
        player.openInventory(inv);
    }

    private void changeFriend(@NotNull Player player, @NotNull String name, final boolean add) {
        final Block block = lookedAtBlock(player);
        if (block == null) return;

        final UUID found = PlayerNames.resolve(name);
        if (found == null || found.equals(player.getUniqueId())) {
            Messages.chat(player, Translator.get(TranslationKey.MESSAGES__PLAYER_NOT_FOUND));
            return;
        }
        if (add && !PluginIntegration.filterFriendByUuidForAll(found, player, block)) {
            Messages.chat(player, Translator.get(TranslationKey.MESSAGES__NO_PERMISSION));
            return;
        }

        final BlockNBTHandler handler = new BlockNBTHandler(block);
        if (!handler.isOwner(player.getUniqueId())) {
            Messages.chat(player, Translator.get(TranslationKey.MESSAGES__NO_PERMISSION));
            return;
        }
        final var result = handler.modifyFriends(player.getUniqueId().toString(), found.toString(),
            add ? FriendModifyAction.ADD_FRIEND : FriendModifyAction.REMOVE_FRIEND);
        if (result.success) {
            handler.applyToOtherContainer();
            if (add) new PlayerSettingsHandler(player).addPlayerToSearchHistory(found);
            Messages.chat(player, Translator.get(add ? TranslationKey.MESSAGES__FRIEND_ADDED : TranslationKey.MESSAGES__FRIEND_REMOVED));
        } else {
            Messages.chat(player, Translator.get(add ? TranslationKey.MESSAGES__FRIEND_ALREADY_ADDED : TranslationKey.MESSAGES__FRIEND_NOT_IN_LIST));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        final List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("ekle");
            out.add("cikar");
            out.add("ayar");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("ekle") || args[0].equalsIgnoreCase("cikar"))) {
            for (final Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(args[1].toLowerCase(Locale.ENGLISH))
                    && !p.equals(sender)) out.add(p.getName());
            }
        }
        return out;
    }
}
