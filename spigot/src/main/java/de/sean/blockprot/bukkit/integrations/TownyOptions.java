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

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The settings of the Towny integration. A key that is missing from the file takes the documented
 * default, and a key that is present decides: an explicit {@code false} stays {@code false}.
 */
public final class TownyOptions {
    public static final String CLEANUP_PLOTS_AFTER_UNCLAIM = "cleanup_plots_after_unclaim";
    public static final String CLEANUP_ON_PLOT_OWNER_CHANGE = "cleanup_on_plot_owner_change";
    public static final String CLEANUP_ON_RESIDENT_LEAVE = "cleanup_on_resident_leave";
    public static final String RESTRICT_ACCESS_TO_RESIDENTS = "restrict_access_to_residents";
    public static final String ALLOW_MAYOR_TO_SEE_BLOCK_INFO = "allow_mayor_to_see_block_info";
    public static final String BYPASS_PROTECTIONS_IN_RUINED_TOWNS = "bypass_protection_in_ruined_towns";
    public static final String TOWN_AUTHORITY_CAN_UNLOCK = "town_authority_can_unlock_town_plots";
    public static final String TOWN_AUTHORITY_RANKS = "town_authority_ranks";

    private final ConfigurationSection config;

    public TownyOptions(@NotNull final ConfigurationSection config) {
        this.config = config;
    }

    /** Restrict unlocked blocks to residents, and friends to residents. */
    public boolean restrictAccessToResidents() {
        return config.getBoolean(RESTRICT_ACCESS_TO_RESIDENTS, false);
    }

    /** Mayors may see who locked a block. */
    public boolean allowMayorToSeeBlockInfo() {
        return config.getBoolean(ALLOW_MAYOR_TO_SEE_BLOCK_INFO, true);
    }

    /** Clear the locks of a plot that is cleared or unclaimed, or of a ruined town. */
    public boolean cleanupAfterUnclaim() {
        return config.getBoolean(CLEANUP_PLOTS_AFTER_UNCLAIM, true);
    }

    public boolean cleanupOnPlotOwnerChange() {
        return config.getBoolean(CLEANUP_ON_PLOT_OWNER_CHANGE, true);
    }

    public boolean cleanupOnResidentLeave() {
        return config.getBoolean(CLEANUP_ON_RESIDENT_LEAVE, true);
    }

    /** Locks in ruined towns do not count. */
    public boolean bypassProtectionsInRuinedTowns() {
        return config.getBoolean(BYPASS_PROTECTIONS_IN_RUINED_TOWNS, true);
    }

    public boolean townAuthorityCanUnlockTownPlots() {
        return config.getBoolean(TOWN_AUTHORITY_CAN_UNLOCK, true);
    }

    public @NotNull List<String> townAuthorityRanks() {
        if (!config.isList(TOWN_AUTHORITY_RANKS)) return List.of("assistant");
        return config.getStringList(TOWN_AUTHORITY_RANKS);
    }
}
