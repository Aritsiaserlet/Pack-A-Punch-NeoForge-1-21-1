package com.myname.packapunch;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║        UPGRADECONFIG — SINGLE SOURCE OF TRUTH           ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * Parses and serves upgrade data from ModConfig.UPGRADES.
 */
public class UpgradeConfig {

    private static List<UpgradeTier> cachedTiers = null;
    private static int lastConfigHash = -1;
    
    // Stores the list sent by the server. If null, we use the local config.
    private static List<String> serverSyncedUpgrades = null;

    public static void syncFromServer(List<String> upgrades) {
        serverSyncedUpgrades = upgrades;
        lastConfigHash = -1; // Force refresh
        refreshCacheIfNeeded();
        com.myname.packapunch.PackAPunchMod.LOGGER.info("[PackAPunch] Synced config from server!");
    }

    private static void refreshCacheIfNeeded() {
        List<? extends String> rawList = serverSyncedUpgrades != null ? serverSyncedUpgrades : com.myname.packapunch.config.ModConfig.UPGRADES.get();
        int currentHash = rawList.hashCode();
        
        if (cachedTiers == null || lastConfigHash != currentHash) {
            cachedTiers = new ArrayList<>();
            for (String entry : rawList) {
                try {
                    String[] parts = entry.split(";");
                    if (parts.length >= 4) {
                        UpgradeTier.CurrencyType type = UpgradeTier.CurrencyType.valueOf(parts[0].trim().toUpperCase());
                        String id = parts[1].trim();
                        int cost = Integer.parseInt(parts[2].trim());
                        float multiplier = Float.parseFloat(parts[3].trim());
                        cachedTiers.add(new UpgradeTier(type, id, cost, multiplier));
                    }
                } catch (Exception e) {
                    PackAPunchMod.LOGGER.error("[UpgradeConfig] Failed to parse upgrade entry: " + entry, e);
                }
            }
            lastConfigHash = currentHash;
        }
    }

    public static int getMaxLevel() {
        refreshCacheIfNeeded();
        return cachedTiers.size();
    }

    public static boolean isMaxLevel(int level) {
        return level >= getMaxLevel();
    }

    public static UpgradeTier getTier(int level) {
        refreshCacheIfNeeded();
        int max = getMaxLevel();
        if (level < 1 || level > max) {
            // Fallback for safety
            return new UpgradeTier(UpgradeTier.CurrencyType.ITEM, "minecraft:diamond_block", 999, 1.0f);
        }
        return cachedTiers.get(level - 1);
    }

    // Retained for backward compatibility with existing code where possible
    public static Item getItemForLevel(int nextLevel) {
        UpgradeTier tier = getTier(nextLevel);
        if (tier.getCurrencyType() == UpgradeTier.CurrencyType.ITEM) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(tier.getId());
            if (rl != null) {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                if (item != null && item != Items.AIR) {
                    return item;
                }
            }
        }
        return Items.DIAMOND_BLOCK; // Safe fallback
    }

    public static int getCostForLevel(int nextLevel) {
        return getTier(nextLevel).getCost();
    }

    public static float getMultiplierForLevel(int level) {
        if (level <= 0) return 1.0f; // Base level
        return getTier(level).getMultiplier();
    }
}
