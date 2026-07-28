package com.myname.packapunch.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;

public class ModConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_MODS;
    
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UPGRADE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> UPGRADE_COSTS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> DAMAGE_MULTIPLIERS;

    static {
        BUILDER.push("General");
        
        ALLOWED_MODS = BUILDER
                .comment("A list of Mod IDs that are allowed to be Pack-a-Punched.",
                         "If this list is empty, items from ALL mods are allowed.",
                         "Example: [\"minecraft\", \"tacz\"]")
                .defineListAllowEmpty("allowedMods", Collections.emptyList(), obj -> obj instanceof String);

        BUILDER.pop();
        
        BUILDER.push("Progression");
        
        MAX_LEVEL = BUILDER
                .comment("The maximum upgrade level a weapon can reach.")
                .defineInRange("maxLevel", 3, 1, 100);
                
        UPGRADE_ITEMS = BUILDER
                .comment("List of items required for each upgrade level (Index 0 = Level 1).",
                         "Must have at least 'maxLevel' elements.")
                .defineListAllowEmpty("upgradeItems", List.of(
                        "minecraft:diamond_block",
                        "minecraft:diamond_block",
                        "minecraft:netherite_block"
                ), obj -> obj instanceof String);
                
        UPGRADE_COSTS = BUILDER
                .comment("List of item costs for each upgrade level.")
                .defineListAllowEmpty("upgradeCosts", List.of(12, 24, 2), obj -> obj instanceof Integer);
                
        DAMAGE_MULTIPLIERS = BUILDER
                .comment("Damage multipliers for each level. Index 0 is base (Level 0), Index 1 is Level 1, etc.",
                         "Must have at least 'maxLevel + 1' elements.")
                .defineListAllowEmpty("damageMultipliers", List.of(1.0, 1.2, 1.5, 2.0), obj -> obj instanceof Double || obj instanceof Float || obj instanceof Integer);

        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
}
