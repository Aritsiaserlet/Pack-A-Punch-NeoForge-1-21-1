package com.myname.packapunch.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;

public class ModConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_MODS;
    
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UPGRADES;

    static {
        BUILDER.push("General");
        
        ALLOWED_MODS = BUILDER
                .comment("A list of Mod IDs that are allowed to be Pack-a-Punched.",
                         "If this list is empty, items from ALL mods are allowed.",
                         "Example: [\"minecraft\", \"tacz\"]")
                .defineListAllowEmpty("allowedMods", Collections.emptyList(), obj -> obj instanceof String);

        BUILDER.pop();
        
        BUILDER.push("Progression");
        
        UPGRADES = BUILDER
                .comment(
                        "List of upgrades. You can add as many as you want.",
                        "The maximum level is determined automatically by the number of entries here.",
                        "",
                        "Format: <TYPE>;<ID_OR_NAME>;<COST>;<MULTIPLIER>",
                        "Supported Types:",
                        "  - ITEM: Requires a specific item. (e.g., ITEM;minecraft:diamond_block;12;1.2)",
                        "  - SCOREBOARD: Requires a scoreboard score. (e.g., SCOREBOARD;money;500;1.5)",
                        "  - XP: Requires experience levels. (e.g., XP;none;30;2.0)",
                        "  - ZOMBIEMOD: Requires ZombieMod points. (e.g., ZOMBIEMOD;points;5000;2.0)"
                )
                .defineListAllowEmpty("upgrades", List.of(
                        "ITEM;minecraft:diamond_block;12;1.2",
                        "ITEM;minecraft:diamond_block;24;1.5",
                        "ITEM;minecraft:netherite_block;2;2.0"
                ), obj -> obj instanceof String);

        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
}
