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
    //  CENTRALIZED CONSTANTS
    // ─────────────────────────────────────────────────────────

    public static final int MAX_LEVEL = 5;

    // The index corresponds to (nextLevel - 1).
    // Level 1: index 0. Level 5: index 4.
    private static final Item[] UPGRADE_ITEMS = {
        Items.DIAMOND_BLOCK,    // Level 1
        Items.DIAMOND_BLOCK,    // Level 2
        Items.DIAMOND_BLOCK,    // Level 3
        Items.NETHERITE_BLOCK,  // Level 4
        Items.NETHERITE_BLOCK   // Level 5
    };

    private static final int[] UPGRADE_COSTS = {
        16, // Level 1
        32, // Level 2
        64, // Level 3
        4,  // Level 4
        8   // Level 5
    };

    // The index corresponds directly to the current level.
    // Level 0: index 0. Level 5: index 5.
    private static final float[] DAMAGE_MULTIPLIERS = {
        1.0f, // Level 0 (Base)
        1.2f, // Level 1
        1.5f, // Level 2
        2.0f, // Level 3
        2.5f, // Level 4
        3.0f  // Level 5
    };

    // ─────────────────────────────────────────────────────────
    //  HELPER METHODS (With Safe Fallbacks)
    // ─────────────────────────────────────────────────────────

    public static boolean isMaxLevel(int level) {
        return level >= MAX_LEVEL;
    }

    public static Item getItemForLevel(int nextLevel) {
        if (nextLevel < 1 || nextLevel > MAX_LEVEL) {
            PackAPunchMod.LOGGER.error("[UpgradeConfig] Invalid nextLevel {} for getItemForLevel. Falling back to default.", nextLevel);
            return Items.DIAMOND_BLOCK; // Safe fallback
        }
        return UPGRADE_ITEMS[nextLevel - 1];
    }

    public static int getCostForLevel(int nextLevel) {
        if (nextLevel < 1 || nextLevel > MAX_LEVEL) {
            PackAPunchMod.LOGGER.error("[UpgradeConfig] Invalid nextLevel {} for getCostForLevel. Falling back to default.", nextLevel);
            return 999; // Safe fallback (unaffordable to prevent exploits)
        }
        return UPGRADE_COSTS[nextLevel - 1];
    }

    public static float getMultiplierForLevel(int level) {
        if (level < 0 || level > MAX_LEVEL) {
            PackAPunchMod.LOGGER.error("[UpgradeConfig] Invalid level {} for getMultiplierForLevel. Falling back to 1.0f.", level);
            return 1.0f; // Safe fallback (no bonus multiplier)
        }
        return DAMAGE_MULTIPLIERS[level];
    }
}
