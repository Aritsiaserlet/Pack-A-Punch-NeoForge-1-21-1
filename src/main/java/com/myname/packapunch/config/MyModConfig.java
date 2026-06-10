package com.myname.packapunch.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class MyModConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec COMMON_SPEC;

    // Unified Upgrade List
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UPGRADES;

    static {
        BUILDER.push("UpgradeSettings");

        UPGRADES = BUILDER
                .comment(
                        "List of upgrades. You can add as many as you want (up to 100+).",
                        "Format: <multiplier>;<item_id>;<cost_amount>",
                        "Example: 1.2;minecraft:diamond_block;12"
                )
                .defineListAllowEmpty("upgrades", List.of(
                        "1.2;minecraft:diamond_block;12",
                        "1.5;minecraft:diamond_block;24",
                        "2.0;minecraft:netherite_block;2"
                ), obj -> obj instanceof String);

        BUILDER.pop();
        COMMON_SPEC = BUILDER.build();
    }
}
