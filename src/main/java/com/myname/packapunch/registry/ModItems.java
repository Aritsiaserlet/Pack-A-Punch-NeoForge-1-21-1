package com.myname.packapunch.registry;

import com.myname.packapunch.PackAPunchMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║              MODITEMS — ITEM REGISTRY                   ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * The single source of truth for all Items in our mod.
 *
 * TYPES OF ITEMS WE'LL REGISTER HERE:
 * ─────────────────────────────────────────────────────────────
 * 1. BlockItems — the inventory item for placing our blocks
 * 2. Standalone Items — materials, tokens, etc. (future lessons)
 *
 * WHY REGISTER BLOCKITEMS SEPARATELY FROM BLOCKS?
 * ─────────────────────────────────────────────────────────────
 * Because Minecraft's design is: Block ≠ Item.
 * - The Block handles WORLD state (breaking, physics, interaction)
 * - The BlockItem handles INVENTORY state (picking up, placing)
 * This separation is intentional — some blocks have NO item form
 * (like fire, liquid water, crops at intermediate stages).
 *
 * HOW BLOCKLIST → ITEM LINK WORKS:
 * ─────────────────────────────────────────────────────────────
 * new BlockItem(ModBlocks.PACK_A_PUNCH_MACHINE.get(), new Item.Properties())
 *   → .get() retrieves the actual Block object from the DeferredBlock
 *   → BlockItem stores a reference to that Block
 *   → When you right-click the item on the ground, it places the Block
 */
@SuppressWarnings("null")
public class ModItems {

    // ─────────────────────────────────────────────────────────
    //  THE DEFERRED REGISTER
    // ─────────────────────────────────────────────────────────

    /**
     * DeferredRegister.Items — typed version for items.
     * Provides .registerSimpleItem() for vanilla-style items,
     * and .register() for custom item classes.
     */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PackAPunchMod.MOD_ID);

    // ─────────────────────────────────────────────────────────
    //  BLOCK ITEMS (inventory forms of our blocks)
    // ─────────────────────────────────────────────────────────

    /**
     * PACK-A-PUNCH MACHINE — BlockItem
     * ─────────────────────────────────────────────────────────
     * This is the item that appears in your inventory and
     * lets you place the machine in the world.
     *
     * Item.Properties:
     *   (empty for now — default stack size of 64)
     *
     * Note: The item's registry name MUST match the block's registry name.
     * "pack_a_punch_machine" → packapunch:pack_a_punch_machine (item)
     * This is how Minecraft knows to use this item's model for the block's
     * inventory/held representation.
     *
     * .stacksTo(1) is not used here because it's a machine — but you could
     * add it if you want the block to be non-stackable (like a chest).
     */
    public static final DeferredItem<BlockItem> PACK_A_PUNCH_MACHINE =
            ITEMS.register("pack_a_punch_machine",
                    () -> new BlockItem(
                            ModBlocks.PACK_A_PUNCH_MACHINE.get(),
                            new Item.Properties()
                    )
            );

    // ─────────────────────────────────────────────────────────
    //  NOTE: Future standalone items go below
    // ─────────────────────────────────────────────────────────
    // These will be added in later lessons:
    //   public static final DeferredItem<Item> UPGRADE_CORE = ...
    //   public static final DeferredItem<Item> PUNCH_TOKEN = ...
}
