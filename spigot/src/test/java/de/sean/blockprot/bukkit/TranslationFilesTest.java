package de.sean.blockprot.bukkit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationFilesTest {
    private static YamlConfiguration load(String name) throws Exception {
        var config = new YamlConfiguration();
        try (var in = TranslationFilesTest.class.getResourceAsStream("/lang/" + name)) {
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return config;
    }

    @Test
    void everyKeyIsTranslatedInTurkishAndEnglish() throws Exception {
        var tr = load("translations_tr.yml");
        var en = load("translations_en.yml");
        for (var key : TranslationKey.values()) {
            assertTrue(en.isString(key.toString()), "en missing " + key);
            assertTrue(tr.isString(key.toString()), "tr missing " + key);
            assertFalse(tr.getString(key.toString()).isBlank(), "tr blank " + key);
        }
    }

    @Test
    void playerTextNamesNoPluginAndNoTechnicalWords() throws Exception {
        var tr = load("translations_tr.yml");
        for (var key : tr.getKeys(true)) {
            if (key.startsWith("materials.") || !tr.isString(key)) continue;
            var text = tr.getString(key).toLowerCase(java.util.Locale.ROOT);
            for (var bad : new String[]{"blockprot", "towny", "nbt", "uuid", "plugin", "eklenti", "config"}) {
                assertFalse(text.contains(bad), key + " contains " + bad);
            }
        }
    }

    @Test
    void everyLockableTypeInTheShippedConfigHasATurkishName() throws Exception {
        var tr = load("translations_tr.yml");
        var config = new YamlConfiguration();
        try (var in = TranslationFilesTest.class.getResourceAsStream("/config.yml")) {
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (var list : new String[]{"lockable_tile_entities", "lockable_shulker_boxes", "lockable_blocks", "lockable_doors"}) {
            for (var name : config.getStringList(list)) {
                assertTrue(tr.isString("materials." + name.toLowerCase(java.util.Locale.ROOT)), name);
            }
        }
    }

    @Test
    void shippedConfigDefaults() throws Exception {
        var config = new YamlConfiguration();
        try (var in = TranslationFilesTest.class.getResourceAsStream("/config.yml")) {
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertFalse(config.getBoolean("lock_on_place_by_default", true));
        assertTrue(config.getString("language_file").equals("translations_tr.yml"));
        assertTrue(config.getStringList("lockable_tile_entities").contains("SPAWNER"));
        assertFalse(config.getStringList("lockable_blocks").contains("ANVIL"));
    }
}
