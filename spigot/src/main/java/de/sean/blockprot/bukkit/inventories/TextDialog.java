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

import de.sean.blockprot.bukkit.BlockProt;
import de.sean.blockprot.bukkit.Messages;
import de.sean.blockprot.bukkit.TranslationKey;
import de.sean.blockprot.bukkit.Translator;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A small window with one text field. It replaces the anvil window that used to ask for text:
 * an anvil needs special handling on every Minecraft version and is awkward on Bedrock, while a
 * dialog is part of the game and Geyser turns it into a form for Bedrock players.
 */
public final class TextDialog {
    private static final String FIELD = "metin";

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
        .uses(1).lifetime(Duration.ofMinutes(5)).build();

    private TextDialog() {
    }

    /**
     * Asks {@code player} for a text.
     *
     * @param onSubmit Called on the player's thread with the text (never null, may be empty).
     * @param onCancel Called on the player's thread when the player chooses to go back, may be null.
     */
    public static void ask(@NotNull final Player player, @NotNull final String title, @NotNull final String label,
                           @NotNull final String initial, final int maxLength,
                           @NotNull final Consumer<String> onSubmit, @Nullable final Runnable onCancel) {
        final BlockProt plugin = BlockProt.getInstance();
        final UUID id = player.getUniqueId();
        final Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Messages.text(title))
                .inputs(List.of(DialogInput.text(FIELD, Messages.text(label))
                    .initial(initial).maxLength(maxLength).width(250).build()))
                .canCloseWithEscape(true)
                .build())
            .type(DialogType.confirmation(
                ActionButton.builder(Messages.text(Translator.get(TranslationKey.DIALOG__CONFIRM)))
                    .action(answer(plugin, id, view -> onSubmit.accept(text(view)))).build(),
                ActionButton.builder(Messages.text(Translator.get(TranslationKey.DIALOG__CANCEL)))
                    .action(answer(plugin, id, view -> {
                        if (onCancel != null) onCancel.run();
                    })).build())));
        player.getScheduler().execute(plugin, () -> player.showDialog(dialog), null, 1L);
    }

    private static DialogAction answer(final BlockProt plugin, final UUID id,
                                       final Consumer<DialogResponseView> onPlayerThread) {
        return DialogAction.customClick((view, audience) -> {
            // The answer may arrive on any thread; the player is looked up again and handled on its own thread.
            final Player target = Bukkit.getPlayer(id);
            if (target != null && plugin.isEnabled()) {
                target.getScheduler().execute(plugin, () -> onPlayerThread.accept(view), null, 1L);
            }
        }, ONCE);
    }

    private static String text(final DialogResponseView view) {
        final String value = view.getText(FIELD);
        return value == null ? "" : value.trim();
    }
}
