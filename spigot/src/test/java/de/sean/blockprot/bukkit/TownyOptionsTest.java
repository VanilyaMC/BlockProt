package de.sean.blockprot.bukkit;

import de.sean.blockprot.bukkit.integrations.TownyOptions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The old code used "contains(key) || getBoolean(key)", which was true for every key that existed. */
class TownyOptionsTest {
    private static TownyOptions of(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new TownyOptions(config);
    }

    @Test
    void anExplicitFalseStaysFalse() throws Exception {
        var options = of("cleanup_plots_after_unclaim: false\nbypass_protection_in_ruined_towns: false\ncleanup_on_resident_leave: false\n");
        assertFalse(options.cleanupAfterUnclaim());
        assertFalse(options.bypassProtectionsInRuinedTowns());
        assertFalse(options.cleanupOnResidentLeave());
    }

    @Test
    void anExplicitTrueStaysTrue() throws Exception {
        var options = of("restrict_access_to_residents: true\ncleanup_plots_after_unclaim: true\n");
        assertTrue(options.restrictAccessToResidents());
        assertTrue(options.cleanupAfterUnclaim());
    }

    @Test
    void missingKeysTakeTheDefaults() throws Exception {
        var options = of("{}\n");
        assertTrue(options.cleanupAfterUnclaim());
        assertTrue(options.cleanupOnPlotOwnerChange());
        assertTrue(options.cleanupOnResidentLeave());
        assertTrue(options.bypassProtectionsInRuinedTowns());
        assertTrue(options.townAuthorityCanUnlockTownPlots());
        assertTrue(options.allowMayorToSeeBlockInfo());
        assertFalse(options.restrictAccessToResidents());
        assertEquals(java.util.List.of("assistant"), options.townAuthorityRanks());
    }

    @Test
    void theShippedFileHasEveryKey() throws Exception {
        var config = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/integrations/towny.yml")) {
            config.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        for (var key : new String[]{TownyOptions.CLEANUP_PLOTS_AFTER_UNCLAIM, TownyOptions.CLEANUP_ON_PLOT_OWNER_CHANGE,
            TownyOptions.CLEANUP_ON_RESIDENT_LEAVE, TownyOptions.RESTRICT_ACCESS_TO_RESIDENTS,
            TownyOptions.ALLOW_MAYOR_TO_SEE_BLOCK_INFO, TownyOptions.BYPASS_PROTECTIONS_IN_RUINED_TOWNS,
            TownyOptions.TOWN_AUTHORITY_CAN_UNLOCK, TownyOptions.TOWN_AUTHORITY_RANKS}) {
            assertTrue(config.contains(key), key);
        }
        assertTrue(new TownyOptions(config).cleanupAfterUnclaim());
    }
}
