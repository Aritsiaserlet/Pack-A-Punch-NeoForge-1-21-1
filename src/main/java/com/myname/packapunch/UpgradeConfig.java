package com.myname.packapunch;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║        UPGRADECONFIG — SINGLE SOURCE OF TRUTH           ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This class acts as the centralized configuration for all upgrade
 * progression logic, costs, and multipliers.
 *
 * It guarantees that GUI text, server validation, and tooltip rendering
 * always use the same values without duplicating magic numbers across
 * the codebase.
 */
public class UpgradeConfig {
    // ─────────────────────────────────────────────────────────
    //  HELPER METHODS (With Safe Fallbacks)
    // ─────────────────────────────────────────────────────────

    public static int getMaxLevel() {
        return com.myname.packapunch.config.ModConfig.MAX_LEVEL.get();
    }

    public static boolean isMaxLevel(int level) {
        return level >= getMaxLevel();
    }

    public static Item getItemForLevel(int nextLevel) {
        int max = getMaxLevel();
        if (nextLevel < 1 || nextLevel > max) {
            PackAPunchMod.LOGGER.error("[UpgradeConfig] Invalid nextLevel {} for getItemForLevel. Falling back to default.", nextLevel);
            return Items.DIAMOND_BLOCK; // Safe fallback
        }
        
        java.util.List<? extends String> items = com.myname.packapunch.config.ModConfig.UPGRADE_ITEMS.get();
        if (nextLevel - 1 < items.size()) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(items.get(nextLevel - 1));
            if (rl != null) {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                if (item != Items.AIR) {
                    return item;
                }
            }
        }
        PackAPunchMod.LOGGER.error("[UpgradeConfig] Could not parse item for level {}. Falling back to default.", nextLevel);
        return Items.DIAMOND_BLOCK;
    }

    public static int getCostForLevel(int nextLevel) {
        int max = getMaxLevel();
        if (nextLevel < 1 || nextLevel > max) {
            PackAPunchMod.LOGGER.error("[UpgradeConfig] Invalid nextLevel {} for getCostForLevel. Falling back to default.", nextLevel);
            return 999; // Safe fallback (unaffordable to prevent exploits)
        }
        
        java.util.List<? extends Integer> costs = com.myname.packapunch.config.ModConfig.UPGRADE_COSTS.get();
        if (nextLevel - 1 < costs.size()) {
            return costs.get(nextLevel - 1);
        }
        PackAPunchMod.LOGGER.error("[UpgradeConfig] Could not find cost for level {}. Falling back to 999.", nextLevel);
        return 999;
    }

    public static float getMultiplierForLevel(int level) {
        int max = getMaxLevel();
        if (level < 0 || level > max) {
            PackAPunchMod.LOGGER.error("[UpgradeConfig] Invalid level {} for getMultiplierForLevel. Falling back to 1.0f.", level);
            return 1.0f; // Safe fallback (no bonus multiplier)
        }
        
        java.util.List<? extends Double> multipliers = com.myname.packapunch.config.ModConfig.DAMAGE_MULTIPLIERS.get();
        if (level < multipliers.size()) {
            return multipliers.get(level).floatValue();
        }
        PackAPunchMod.LOGGER.error("[UpgradeConfig] Could not find multiplier for level {}. Falling back to 1.0f.", level);
        return 1.0f;
    }
}
