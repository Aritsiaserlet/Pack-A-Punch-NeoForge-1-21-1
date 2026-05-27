package com.myname.packapunch.registry;

import com.myname.packapunch.PackAPunchMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║         MODCREATIVETABS — CREATIVE TAB REGISTRY         ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * Creative Mode Tabs are themselves registered objects in 1.21.1.
 * This means we register them with a DeferredRegister just like blocks/items.
 *
 * Our "Pack a Punch" tab will:
 *  - Appear in the Creative inventory after the "Combat" tab
 *  - Use the Pack-a-Punch Machine as its icon
 *  - Display all our mod's items (blocks, materials, tokens, etc.)
 *
 * TRANSLATION KEYS:
 * ─────────────────────────────────────────────────────────────
 * Component.translatable("itemGroup.packapunch.main")
 *   → This refers to a key in our lang file (en_us.json):
 *     "itemGroup.packapunch.main": "Pack a Punch"
 *   → If the key is missing, Minecraft shows the raw key string.
 *   → This system allows multi-language support.
 */
@SuppressWarnings("null")
public class ModCreativeTabs {

    // ─────────────────────────────────────────────────────────
    //  THE DEFERRED REGISTER
    // ─────────────────────────────────────────────────────────

    /**
     * Creative Mode Tabs are in the CREATIVE_MODE_TAB registry.
     * We need to use the generic DeferredRegister<CreativeModeTab>
     * with Registries.CREATIVE_MODE_TAB as the key.
     */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PackAPunchMod.MOD_ID);

    // ─────────────────────────────────────────────────────────
    //  TAB REGISTRATIONS
    // ─────────────────────────────────────────────────────────

    /**
     * OUR MAIN CREATIVE TAB
     * ─────────────────────────────────────────────────────────
     *  DeferredHolder<CreativeModeTab, CreativeModeTab>
     *   → The outer type is the registry type (CreativeModeTab)
     *   → The inner type is the specific object type (CreativeModeTab)
     *   → For simple registrations like this they're the same.
     *
     *  CreativeModeTab.builder()
     *   → .title(...)    = the displayed name (uses translation key)
     *   → .withTabsBefore(CreativeModeTabs.COMBAT)
     *        = places our tab AFTER the Combat tab in the order
     *   → .icon(...)     = the item shown on the tab button
     *   → .displayItems(...)  = lambda that adds items to this tab
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PACK_A_PUNCH_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.packapunch.main"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.PACK_A_PUNCH_MACHINE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Add our machine block's item to the tab
                        output.accept(ModItems.PACK_A_PUNCH_MACHINE.get());

                        // Future items will be added here as we create them:
                        // output.accept(ModItems.UPGRADE_CORE.get());
                        // output.accept(ModItems.PUNCH_TOKEN.get());
                    })
                    .build()
            );
}
