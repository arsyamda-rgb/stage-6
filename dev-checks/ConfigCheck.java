package com.veyrion.character.config;

import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/** Standalone check of PluginSettings economy defaults and validation (stub FileConfiguration, see README.txt). */
public final class ConfigCheck {
    static int checks = 0, failures = 0;
    static void eq(String w, Object e, Object a) { checks++; if (!java.util.Objects.equals(e, a)) { failures++; System.out.println("FAIL " + w + ": expected <" + e + "> but was <" + a + ">"); } }

    static final class Cfg extends FileConfiguration {
        final java.util.Map<String, Object> v = new java.util.HashMap<>();
        @Override public int getInt(String p, int d) { return v.containsKey(p) ? ((Number) v.get(p)).intValue() : d; }
        @Override public double getDouble(String p, double d) { return v.containsKey(p) ? ((Number) v.get(p)).doubleValue() : d; }
    }

    public static void main(String[] args) {
        Logger log = Logger.getLogger("check");
        log.setLevel(java.util.logging.Level.OFF);
        Cfg cfg = new Cfg();
        eq("default cooldown is 3000", 3000, PluginSettings.from(cfg, log).economy().payCooldownMs());
        cfg.v.put("economy.pay-cooldown-ms", 5000);
        eq("custom cooldown", 5000, PluginSettings.from(cfg, log).economy().payCooldownMs());
        cfg.v.put("economy.pay-cooldown-ms", -1);
        eq("negative cooldown falls back to 3000", 3000, PluginSettings.from(cfg, log).economy().payCooldownMs());
        cfg.v.put("economy.pay-cooldown-ms", 999999);
        eq("huge cooldown falls back to 3000", 3000, PluginSettings.from(cfg, log).economy().payCooldownMs());
        cfg.v.put("economy.pay-cooldown-ms", 0);
        eq("0 disables cooldown", 0, PluginSettings.from(cfg, log).economy().payCooldownMs());
        cfg.v.put("economy.minimum-payment", Double.NaN);
        eq("NaN minimum falls back to 1", 1.0, PluginSettings.from(cfg, log).economy().minimumPayment());
        cfg.v.put("economy.baltop-cache-seconds", 1);
        eq("baltop cache too low falls back", 120, PluginSettings.from(cfg, log).economy().baltopCacheSeconds());
        System.out.println(checks + " checks, " + failures + " failures");
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : "FAILED");
        if (failures != 0) System.exit(1);
    }
}
