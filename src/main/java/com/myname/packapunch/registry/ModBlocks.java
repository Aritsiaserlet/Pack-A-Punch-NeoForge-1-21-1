package com.myname.packapunch.registry;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.block.PackAPunchBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║              MODBLOCKS — BLOCK REGISTRY                 ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This class is the SINGLE SOURCE OF TRUTH for every block in our mod.
 *
 * HOW REGISTRATION WORKS:
 * ─────────────────────────────────────────────────────────────
 * 1. We create a DeferredRegister.Blocks under our mod ID namespace.
 * 2. We call BLOCKS.register("name", supplier) for each block.
 *    - "name" becomes the path part of the ResourceLocation:
 *      "packapunch:pack_a_punch_machine"
 *    - The supplier is a lambda () -> new YourBlock(...) that NeoForge
 *      calls at the right moment during the loading sequence.
 * 3. In PackAPunchMod's constructor, we call BLOCKS.register(modEventBus)
 *    to activate the register and hook it into the loading lifecycle.
 *
 * NAMING CONVENTIONS:
 * ─────────────────────────────────────────────────────────────
 * - Java constant name:  PACK_A_PUNCH_MACHINE  (UPPER_SNAKE_CASE)
 * - Registry name:       "pack_a_punch_machine" (lower_snake_case)
 * - ResourceLocation ID: packapunch:pack_a_punch_machine
 * - Texture path:        assets/packapunch/textures/block/pack_a_punch_machine.png
 *
 * WHY IS THE BLOCK SEPARATE FROM ITS ITEM?
 * ─────────────────────────────────────────────────────────────
 * In Minecraft, a Block and its corresponding Item (the one you hold
 * in your inventory) are TWO DIFFERENT OBJECTS. The Block defines
 * world behavior; the BlockItem wraps the Block for inventory use.
 * We register BlockItems in ModItems.java.
 */
@SuppressWarnings("null")
public class ModBlocks {

    // ─────────────────────────────────────────────────────────
    //  THE DEFERRED REGISTER
    // ─────────────────────────────────────────────────────────

    /**
     * The DeferredRegister.Blocks is a typed register specifically for Blocks.
     * It provides the helper method .registerSimpleBlock() for basic blocks
     * and .register() for custom block classes.
     *
     * We make this public so PackAPunchMod can call BLOCKS.register(modEventBus).
     */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(PackAPunchMod.MOD_ID);

    // ─────────────────────────────────────────────────────────
    //  BLOCK REGISTRATIONS
    // ─────────────────────────────────────────────────────────

    /**
     * THE PACK-A-PUNCH MACHINE
     * ─────────────────────────────────────────────────────────
     * This is our centerpiece block. It will eventually have:
     *  - A custom GUI for upgrading guns
     *  - A BlockEntity for storing inventory and upgrade data
     *  - Animated textures
     *  - Sound effects
     *
     * For now, we register it as a basic block with physical properties
     * appropriate for a large metal machine.
     *
     * BlockBehaviour.Properties — defines how the block FEELS:
     * ─────────────────────────────────────────────────────────
     *  .mapColor(MapColor.COLOR_PURPLE)
     *    → The color shown on maps. We chose purple for the iconic PaP color.
     *
     *  .strength(5.0f, 1200.0f)
     *    → hardness=5.0 (takes ~5 seconds to mine with correct tool)
     *    → resistance=1200 (very resistant to explosions — like obsidian!)
     *
     *  .requiresCorrectToolForDrops()
     *    → You must use a pickaxe to get the item drop; otherwise it breaks
     *    → without dropping (like vanilla stone behavior)
     *
     *  .sound(SoundType.NETHERITE_BLOCK)
     *    → Heavy metallic clunk sounds when placing/breaking
     *
     * DeferredBlock<PackAPunchBlock>
     *   → Typed to PackAPunchBlock so we can call machine-specific methods later.
     *   → .get() returns the actual PackAPunchBlock instance (after loading).
     */
    public static final DeferredBlock<PackAPunchBlock> PACK_A_PUNCH_MACHINE =
            BLOCKS.register("pack_a_punch_machine", () -> new PackAPunchBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0f, 1200.0f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.NETHERITE_BLOCK)
            ));

    // ─────────────────────────────────────────────────────────
    //  NOTE: Add future blocks below this line
    // ─────────────────────────────────────────────────────────
    // Example future additions:
    //   public static final DeferredBlock<Block> PUNCH_ORE = BLOCKS.register(...);
    //   public static final DeferredBlock<Block> POWER_CORE = BLOCKS.register(...);
}
