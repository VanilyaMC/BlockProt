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

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.event.PlotClearEvent;
import com.palmergames.bukkit.towny.event.TownAddResidentEvent;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.event.plot.changeowner.PlotChangeOwnerEvent;
import com.palmergames.bukkit.towny.event.town.TownRuinedEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import de.sean.blockprot.bukkit.BlockProt;
import de.sean.blockprot.bukkit.events.BlockAccessEvent;
import de.sean.blockprot.bukkit.events.BlockAccessMenuEvent;
import de.sean.blockprot.bukkit.events.BlockLockOnPlaceEvent;
import de.sean.blockprot.bukkit.nbt.LockCleaner;
import de.sean.blockprot.bukkit.nbt.StatHandler;
import de.sean.blockprot.bukkit.nbt.BlockNBTHandler;
import de.sean.blockprot.cleanup.CleanupPlanner;
import de.sean.blockprot.cleanup.LockRules;
import de.sean.blockprot.cleanup.LockEntry;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Towny integration. Locks follow the land: when land is cleared, handed over, unclaimed or
 * ruined, or when a resident leaves, the locks that no longer make sense are removed. Mayors and
 * their assistants can take locks off blocks on town-owned plots.
 */
public final class TownyIntegration extends PluginIntegration implements Listener {
    /**
     * Our Towny plugin instance. Might be null if towny was not found.
     */
    @Nullable
    private Towny towny;

    private boolean enabled = false;

    public TownyIntegration() {
        super("towny");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void enable() {
        final Plugin plugin = getPlugin();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        towny = (Towny) plugin;

        this.registerListener(this);

        enabled = true;
    }

    /**
     * {@inheritDoc}
     */
    public Plugin getPlugin() {
        return BlockProt.getInstance().getPlugin("Towny");
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void filterFriendsInternal(@NotNull final ArrayList<OfflinePlayer> friends,
                                         @NotNull final Player player,
                                         @NotNull final Block block) {
        if (towny == null
            || TownyAPI.getInstance().isWilderness(block)
            || !options().restrictAccessToResidents()) {
            return;
        }

        Town town = TownyAPI.getInstance().getTown(block.getLocation());
        if (town == null) {
            // Shouldn't happen, as we already previously check
            // for the wilderness.
            return;
        }
        friends.removeIf(friend -> {
            // We remove all friends/players that are not part of this town.
            Resident resident = TownyAPI.getInstance().getResident(friend.getUniqueId());
            return resident == null || !town.hasResident(resident);
        });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean filterFriendByUuid(@NotNull final UUID friend,
                                         @NotNull final Player player,
                                         @NotNull final Block block) {
        if (towny == null
            || TownyAPI.getInstance().isWilderness(block)
            || !options().restrictAccessToResidents()) {
            return true;
        }

        var town = TownyAPI.getInstance().getTown(block.getLocation());
        if (town == null) {
            // Shouldn't happen, as we already previously check
            // for the wilderness.
            return true;
        }

        // We return true for all residents of this town.
        var resident = TownyAPI.getInstance().getResident(friend);
        return resident != null && town.hasResident(resident);
    }

    private TownyOptions options() {
        return new TownyOptions(configuration);
    }

    private boolean residentEqualsPlayer(@Nullable final Resident resident,
                                         @Nullable final Player player) {
        if (resident == null && player == null) {
            return true;
        }

        if (resident == null
            || resident.getPlayer() == null
            || player == null) {
            return false;
        }

        return resident.getPlayer().equals(player);
    }

    /**
     * Whether the player may remove locks on town-owned plots of {@code town}: the mayor, or a
     * resident with one of the configured ranks (assistants by default).
     */
    private boolean isTownAuthority(@NotNull final Town town, @NotNull final Player player) {
        final Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !town.hasResident(resident)) return false;
        if (town.isMayor(resident)) return true;
        for (final String rank : options().townAuthorityRanks()) {
            if (resident.hasTownRank(rank)) return true;
        }
        return false;
    }

    private enum Change { REMOVE, MARK }

    /**
     * Removes or marks the locks inside the plots, in one pass over the lock index.
     * Owners {@code keep} says yes to are left alone.
     */
    private void changeLocks(@NotNull final java.util.Collection<WorldCoord> coords, @NotNull final Change change,
                             @Nullable final Predicate<UUID> keep, @Nullable final Predicate<org.bukkit.Material> typeFilter) {
        if (coords.isEmpty()) return;
        final Set<CleanupPlanner.PlotKey> plots = new HashSet<>();
        for (final WorldCoord coord : coords) {
            plots.add(new CleanupPlanner.PlotKey(coord.getWorldName(), coord.getX(), coord.getZ()));
        }
        List<LockEntry> locks = CleanupPlanner.inPlots(StatHandler.allLocks(), plots, TownySettings.getTownBlockSize());
        if (keep != null) locks = locks.stream().filter(e -> !keep.test(e.owner())).toList();
        if (locks.isEmpty()) return;
        if (change == Change.REMOVE) LockCleaner.clear(locks, typeFilter);
        else LockCleaner.markOrphaned(locks);
    }

    @EventHandler
    public void onAccess(@NotNull final BlockAccessEvent event) {
        if (towny == null) {
            return;
        }

        Block block = event.getBlock();
        if (TownyAPI.getInstance().isWilderness(block)) {
            return; // We allow anyone to access blocks in the wilderness.
        }

        Town town = TownyAPI.getInstance().getTown(block.getLocation());
        if (town == null) {
            return;
        }

        if (town.isRuined() && options().bypassProtectionsInRuinedTowns()) {
            // Nobody is left to look after the locks of a ruined town.
            event.setBypassProtections(true);
            return;
        }

        if (options().restrictAccessToResidents()) {
            Resident resident = TownyAPI.getInstance().getResident(
                event.getPlayer().getUniqueId());
            // Only restrict the block if they're not a resident of this town.
            if (resident == null || !town.hasResident(resident)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onAccessEditMenu(
        @NotNull final BlockAccessMenuEvent event) {
        if (towny == null) {
            return;
        }

        Block block = event.getBlock();
        if (TownyAPI.getInstance().isWilderness(block)) {
            return; // Allow editing blocks that are in the wilderness.
        }

        Town town = TownyAPI.getInstance().getTown(block.getLocation());
        if (town == null) {
            return;
        }

        if (town.isRuined()) {
            // Nobody looks after a ruined town, anybody may clean up.
            if (options().bypassProtectionsInRuinedTowns()) {
                event.addPermissions(BlockAccessMenuEvent.MenuPermission.LOCK,
                    BlockAccessMenuEvent.MenuPermission.FORCE_UNLOCK);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        // The mayor and the assistants take locks off town-owned plots, and off the locks of somebody who
        // left the town (never off a resident's lock on that resident's own plot).
        final TownBlock townBlock = TownyAPI.getInstance().getTownBlock(block.getLocation());
        if (townBlock != null && isTownAuthority(town, event.getPlayer())) {
            final BlockNBTHandler handler = new BlockNBTHandler(block);
            final boolean plotIsTownOwned = !townBlock.hasResident();
            // Only the lock of somebody who left the town (marked when he left) counts as left behind.
            final boolean ownerIsResident = lockOwnerIsResident(town, handler) || handler.getOrphanedAt() == 0L;
            final boolean allowTownPlots = options().townAuthorityCanUnlockTownPlots();
            final boolean allowExResident = options().townAuthorityCanUnlockExResidentLocks();
            if (handler.isProtected()
                && LockRules.townAuthorityMayUnlock(true, plotIsTownOwned && allowTownPlots, ownerIsResident || !allowExResident)) {
                event.addPermissions(BlockAccessMenuEvent.MenuPermission.LOCK,
                    BlockAccessMenuEvent.MenuPermission.INFO,
                    BlockAccessMenuEvent.MenuPermission.FORCE_UNLOCK);
                return;
            }
        }

        if (!PlayerCacheUtil.getCachePermission(
            event.getPlayer(),
            block.getLocation(),
            block.getType(),
            TownyPermission.ActionType.DESTROY)) {
            // We do not want to allow players to edit this block if
            // they're not part of this town.
            event.setCancelled(true);
            return;
        }

        if (residentEqualsPlayer(town.getMayor(), event.getPlayer())
            && options().allowMayorToSeeBlockInfo()) {
            event.addPermission(BlockAccessMenuEvent.MenuPermission.INFO);
        }
    }

    @EventHandler
    public void onLockOnPlace(@NotNull final BlockLockOnPlaceEvent event) {
        if (towny == null) {
            return;
        }

        Block block = event.getBlock();
        if (TownyAPI.getInstance().isWilderness(block)) {
            return;
        }

        if (options().restrictAccessToResidents()) {
            Town town = TownyAPI.getInstance().getTown(block.getLocation());
            if (town != null) {
                Resident resident =
                    TownyAPI.getInstance().getResident(event.getPlayer().getUniqueId());
                // Only residents of the town lock blocks inside of it.
                if (resident == null || !town.hasResident(resident)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * {@code /plot clear}. On a town-owned plot the mayor or an assistant cleared it: every lock goes.
     * On a resident's plot only the locks on the block types Towny's plot clear removes itself go (signs
     * by default): the event does not say who ran the command, and a plot clear must never open the
     * chests of a resident that it leaves standing.
     */
    @EventHandler
    public void onPlotClear(@NotNull final PlotClearEvent event) {
        if (!options().cleanupAfterUnclaim() || event.getTownBlock() == null) {
            return;
        }
        final TownBlock townBlock = event.getTownBlock();
        if (!townBlock.hasResident()) {
            changeLocks(List.of(townBlock.getWorldCoord()), Change.REMOVE, null, null);
            return;
        }
        final Set<org.bukkit.Material> removed = java.util.EnumSet.noneOf(org.bukkit.Material.class);
        removed.addAll(townBlock.getWorld().getPlotManagementMayorDelete());
        changeLocks(List.of(townBlock.getWorldCoord()), Change.REMOVE, null, removed::contains);
    }

    /**
     * The town is ruined. A ruined town is public ground (Towny opens every permission), nobody is left
     * to look after it, so every lock inside goes.
     */
    @EventHandler
    public void onTownRuin(@NotNull final TownRuinedEvent event) {
        if (!options().cleanupAfterUnclaim()) {
            return;
        }
        final List<WorldCoord> coords = new ArrayList<>();
        for (TownBlock townBlock : new ArrayList<>(event.getTown().getTownBlocks())) {
            coords.add(townBlock.getWorldCoord());
        }
        changeLocks(coords, Change.REMOVE, null, null);
    }

    /**
     * A plot leaves a town: unclaimed by the mayor, overclaimed, or one by one while a town is
     * deleted. Towny fires this for every plot of a deleted town.
     */
    @EventHandler
    public void onTownUnclaim(@NotNull final TownUnclaimEvent event) {
        if (!options().cleanupAfterUnclaim()) {
            return;
        }
        // The land leaves the town (the mayor unclaims it, the town is deleted, another town overclaims).
        // The owners get the grace period to fetch their things; then the locks stop protecting.
        changeLocks(List.of(event.getWorldCoord()), Change.MARK, null, null);
    }

    /**
     * A plot changes owner (claimed, sold, handed back, evicted). Locks are never opened by this: the
     * old owner's locks are only marked (he gets the grace period, the mayor can remove them on the
     * town plot he left), and the locks of the new owner lose a mark they may have from earlier.
     */
    @EventHandler
    public void onPlotOwnerChange(@NotNull final PlotChangeOwnerEvent event) {
        if (!options().markLocksOfLeavingResidents() || event.getTownBlock() == null) {
            return;
        }
        final WorldCoord coord = event.getTownBlock().getWorldCoord();
        final Resident oldOwner = event.getOldResident();
        final Resident newOwner = event.getNewResident();
        if (oldOwner != null && (newOwner == null || !oldOwner.getUUID().equals(newOwner.getUUID()))) {
            final UUID old = oldOwner.getUUID();
            changeLocks(List.of(coord), Change.MARK, owner -> !owner.equals(old), null);
        }
        if (newOwner != null) {
            final UUID neu = newOwner.getUUID();
            final var plots = Set.of(new CleanupPlanner.PlotKey(coord.getWorldName(), coord.getX(), coord.getZ()));
            final var back = CleanupPlanner.inPlots(StatHandler.allLocks(), plots, TownySettings.getTownBlockSize()).stream()
                .filter(e -> e.owner().equals(neu)).toList();
            if (!back.isEmpty()) LockCleaner.unmarkOrphaned(back);
        }
    }

    /** A player (back) in a town: the locks he holds inside it are his again. */
    @EventHandler
    public void onResidentJoin(@NotNull final TownAddResidentEvent event) {
        if (!options().markLocksOfLeavingResidents()) {
            return;
        }
        final Town town = event.getTown();
        final UUID resident = event.getResident().getUUID();
        final List<LockEntry> locks = CleanupPlanner.ownedInside(
            StatHandler.allLocks(), resident, entry -> insideTown(town, entry));
        if (!locks.isEmpty()) {
            LockCleaner.unmarkOrphaned(locks);
        }
    }

    /**
     * A resident leaves a town or is kicked. The locks they hold inside it are not opened: that would
     * let a mayor (or an alt that joined the town) empty somebody's chests the moment they are kicked.
     * Instead the locks are marked; the mayor and assistants may take them off by hand, and when the
     * grace period (ex_resident_lock_grace_days) is over they stop protecting anything.
     */
    @EventHandler
    public void onResidentLeave(@NotNull final TownRemoveResidentEvent event) {
        if (!options().markLocksOfLeavingResidents()) {
            return;
        }
        final Town town = event.getTown();
        final UUID resident = event.getResident().getUUID();
        // Decided now, while the plots still belong to the town (a deleted town drops them next).
        final List<LockEntry> locks = CleanupPlanner.ownedInside(
            StatHandler.allLocks(), resident, entry -> insideTown(town, entry));
        if (!locks.isEmpty()) {
            LockCleaner.markOrphaned(locks);
        }
    }

    private boolean lockOwnerIsResident(@NotNull final Town town, @NotNull final BlockNBTHandler handler) {
        final String owner = handler.getOwner();
        if (owner.isEmpty()) return true;
        try {
            final Resident resident = TownyAPI.getInstance().getResident(UUID.fromString(owner));
            return resident != null && town.hasResident(resident);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private boolean insideTown(@NotNull final Town town, @NotNull final LockEntry entry) {
        final var world = org.bukkit.Bukkit.getWorld(entry.world());
        if (world == null) return false;
        final TownBlock townBlock = TownyAPI.getInstance().getTownBlock(new Location(world, entry.x(), entry.y(), entry.z()));
        return townBlock != null && town.equals(townBlock.getTownOrNull());
    }
}
