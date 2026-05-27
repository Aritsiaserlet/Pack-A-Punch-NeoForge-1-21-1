package com.myname.packapunch.registry;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.blockentity.PackAPunchBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MODBLOCKENTITYTYPES — BlockEntity Type Registry
 *
 * A BlockEntityType is a "factory descriptor" that tells Minecraft:
 *   1. How to CREATE a BlockEntity (via constructor reference)
 *   2. Which Block(s) are ALLOWED to have this BlockEntity
 *
 * Think of it like a blueprint stamp:
 *   "This BlockEntity can only exist on PACK_A_PUNCH_MACHINE blocks."
 *
 * If the block at a position doesn't match the registered blocks list,
 * Minecraft will refuse to attach the BlockEntity and log a warning.
 *
 * Registration pattern is identical to blocks/items — DeferredRegister
 * queues the registration and fires it at the correct loading stage.
 */
@SuppressWarnings("null")
public class ModBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PackAPunchMod.MOD_ID);

    /**
     * BlockEntityType for our Pack-a-Punch machine.
     *
     * BlockEntityType.Builder.of(constructor, validBlocks...)
     *   - constructor: PackAPunchBlockEntity::new
     *       A method reference to our BlockEntity constructor.
     *       Signature must be (BlockPos, BlockState) → BlockEntity.
     *   - validBlocks: ModBlocks.PACK_A_PUNCH_MACHINE.get()
     *       The block(s) this BlockEntity is allowed on.
     *       .get() fetches the actual Block from the DeferredBlock wrapper.
     *
     * .build(null) — the null is for the DataFixer type.
     *   DataFixers handle world upgrades between Minecraft versions.
     *   Mods pass null because we manage our own NBT migration.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PackAPunchBlockEntity>>
            PACK_A_PUNCH_MACHINE = BLOCK_ENTITY_TYPES.register(
                    "pack_a_punch_machine",
                    () -> BlockEntityType.Builder
                            .of(PackAPunchBlockEntity::new, ModBlocks.PACK_A_PUNCH_MACHINE.get())
                            .build(null)
            );
}
