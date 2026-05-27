package com.myname.packapunch.block;

import com.mojang.serialization.MapCodec;
import com.myname.packapunch.blockentity.PackAPunchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║            PACKAPUNCHBLOCK — UPDATED (LESSON 2)         ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * Changes from Lesson 1:
 *   - Now extends BaseEntityBlock instead of Block
 *   - Implements newBlockEntity() → spawns our PackAPunchBlockEntity
 *   - Overrides useWithoutItem() → opens the GUI on right-click
 *   - Overrides onRemove() → drops inventory when broken
 *   - Overrides getRenderShape() → needed for BaseEntityBlock subclasses
 *
 * WHY BaseEntityBlock INSTEAD OF Block + EntityBlock?
 * ──────────────────────────────────────────────────────────
 * BaseEntityBlock already implements the EntityBlock interface AND
 * sets getRenderShape() to INVISIBLE (so we MUST override it back to MODEL).
 * It also provides helpful utility methods for working with BlockEntities.
 *
 * The alternative is: extends Block implements EntityBlock
 * Both work, but BaseEntityBlock is Minecraft's conventional choice
 * for blocks with BlockEntities that use normal cube models.
 *
 * WHY DOES GUI OPENING HAPPEN SERVER-SIDE?
 * ──────────────────────────────────────────────────────────
 * The server is the AUTHORITY on game state. When we call:
 *   ((ServerPlayer) player).openMenu(blockEntity)
 *
 * 1. Server creates the real PackAPunchMenu (with live BlockEntity data)
 * 2. Server sends a "ClientboundOpenScreenPacket" to the player's client
 * 3. Client receives the packet → creates a dummy PackAPunchMenu
 * 4. Client opens the Screen (rendered GUI)
 * 5. Server begins syncing slot contents and ContainerData to client
 *
 * If the CLIENT opened the GUI directly:
 *   - The server wouldn't know the menu is open → no item sync
 *   - Exploiters could fake interactions → duplication/theft bugs
 *   - The two sides would desync immediately
 *
 * The !level.isClientSide() guard ensures the block code only executes
 * the openMenu call on the server. On the client, we return sidedSuccess
 * which plays the "swing arm" animation — purely cosmetic.
 */
@SuppressWarnings("null")
public class PackAPunchBlock extends BaseEntityBlock {

    public static final MapCodec<PackAPunchBlock> CODEC = simpleCodec(PackAPunchBlock::new);

    public PackAPunchBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // ─────────────────────────────────────────────────────────
    //  BlockEntity Wiring
    // ─────────────────────────────────────────────────────────

    /**
     * Called by Minecraft when it needs to create a BlockEntity for
     * a newly placed block at this position.
     *
     * Returns a NEW PackAPunchBlockEntity instance. Each placed machine
     * gets its own independent instance with its own inventory and
     * upgrade level — this is what makes per-block data possible.
     *
     * @param pos   The position where the block was just placed.
     * @param state The BlockState at that position.
     * @return A fresh BlockEntity for this position.
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PackAPunchBlockEntity(pos, state);
    }

    /**
     * CRITICAL: BaseEntityBlock overrides this to return INVISIBLE,
     * which would make our block invisible in the world!
     *
     * We override it back to MODEL so Minecraft renders our block using
     * the standard block model pipeline (our blockstate → model → texture).
     *
     * RenderShape.MODEL = render using the registered block model (normal)
     * RenderShape.ENTITYBLOCK_ANIMATED = render using a BlockEntityRenderer
     *   (used by chests, signs, etc. that have complex animations)
     * RenderShape.INVISIBLE = don't render at all (barriers, structure voids)
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ─────────────────────────────────────────────────────────
    //  Right-Click: Open GUI
    // ─────────────────────────────────────────────────────────

    /**
     * Called when a player right-clicks this block WITHOUT an item in hand.
     * (If they hold an item, useItemOn() fires first; if that returns PASS,
     *  this method is called next — so it works for most held items too.)
     *
     * InteractionResult options:
     *   SUCCESS       → consumed, plays swing animation on server only
     *   sidedSuccess  → consumed, plays swing animation on the correct side
     *   CONSUME       → consumed, no swing animation
     *   PASS          → not consumed, let the event bubble to other handlers
     *   FAIL          → not consumed, no further handling
     *
     * We use sidedSuccess(level.isClientSide()) which:
     *   - On CLIENT: returns SUCCESS → plays arm swing
     *   - On SERVER: returns CONSUME → prevents double-execution issues
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hitResult) {

        // isClientSide() returns true when this code runs on the player's computer.
        // We only want to open the menu on the SERVER — the server sends a
        // packet to the client which then opens the Screen.
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof PackAPunchBlockEntity machineEntity) {
                // LESSON: Writing BlockPos to FriendlyByteBuf
                // ──────────────────────────────────────────────────────────
                // NeoForge's openMenu overload accepts a Consumer<FriendlyByteBuf>.
                // This consumer runs on the SERVER just before the open-menu packet
                // is sent to the client. Whatever we write here is available
                // in the client-side menu constructor's FriendlyByteBuf buf.
                //
                // We write: buf.writeBlockPos(pos)
                // Client reads: buf.readBlockPos()
                //
                // This is how the client learns WHICH machine it's looking at —
                // so it can send the upgrade packet back to the right position,
                // and so stillValid() can check the correct block.
                ((ServerPlayer) player).openMenu(machineEntity, buf -> buf.writeBlockPos(pos));
            }
        }

        // Return sidedSuccess to consume the interaction on both sides.
        // Without this, the player might try to place a block instead.
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ─────────────────────────────────────────────────────────
    //  Block Removal: Drop Items
    // ─────────────────────────────────────────────────────────

    /**
     * Called when this block is replaced or removed from the world.
     * This fires for:
     *   - Player breaking the block
     *   - Pistons pushing the block
     *   - /setblock replacing it with air
     *   - An explosion destroying it (though items may not survive)
     *
     * We call dropContents() to scatter inventory items on the ground.
     * Without this, items put into the machine would disappear forever
     * when the block is broken — that's a terrible player experience!
     *
     * The newState != state check prevents item drops when the block
     * updates its own BlockState (e.g., when we add a "powered" state
     * in a later lesson — we don't want to drop items on every state change).
     *
     * IMPORTANT: Always call super.onRemove() — it removes the BlockEntity
     * from the level. If you forget, the BlockEntity stays in memory as a
     * ghost, leaking RAM and potentially causing subtle bugs.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
            BlockState newState, boolean movedByPiston) {

        // Only drop contents if the block is being replaced by a DIFFERENT block.
        // (Not just a BlockState change within the same block type)
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PackAPunchBlockEntity machineEntity) {
                machineEntity.dropContents();
            }
        }

        // MUST call super — removes the BlockEntity from the world.
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
