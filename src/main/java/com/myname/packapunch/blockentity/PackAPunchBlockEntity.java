package com.myname.packapunch.blockentity;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.component.UpgradeLevelComponent;
import com.myname.packapunch.menu.PackAPunchMenu;
import com.myname.packapunch.registry.ModBlockEntityTypes;
import com.myname.packapunch.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║          PACKAPUNCHBLOCKENTITY — DATA STORAGE           ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This is the "brain" of each placed Pack-a-Punch machine.
 *
 * LIFECYCLE (order of method calls):
 * ──────────────────────────────────────────────────────────
 *  1. Block placed        → PackAPunchBlock.newBlockEntity() called
 *                           → new PackAPunchBlockEntity(pos, state) created
 *  2. Chunk saves         → saveAdditional() called → writes our data to NBT
 *  3. Chunk loads         → loadAdditional() called → reads our data from NBT
 *  4. Client joins/reloads→ getUpdatePacket() + getUpdateTag() called
 *                           → sends data to the client so it can render correctly
 *  5. Player right-clicks → createMenu() called → opens the GUI
 *  6. Block broken        → dropContents() called → items fall to the ground
 *
 * WHAT IS ItemStackHandler?
 * ──────────────────────────────────────────────────────────
 * NeoForge's inventory system. Stores a fixed number of ItemStack slots.
 * Advantages over vanilla SimpleContainer:
 *  - Integrates with the IItemHandler capability (needed for TaCZ later)
 *  - Has onContentsChanged() callback for change notifications
 *  - Clean NBT serialization built in
 *
 * WHAT IS ContainerData?
 * ──────────────────────────────────────────────────────────
 * A lightweight integer-array interface automatically synced by Minecraft
 * whenever a menu is open. We use it to send upgradeLevel to the client
 * so the Screen can display "Level: 2" without any manual networking.
 *
 * Limitations: ContainerData only syncs INTEGERS. For complex data
 * (strings, ItemStacks), you need custom network packets (Lesson 5).
 */
@SuppressWarnings("null")
public class PackAPunchBlockEntity extends BlockEntity implements MenuProvider {

    // ─────────────────────────────────────────────────────────
    //  CONSTANTS
    // ─────────────────────────────────────────────────────────

    /** Number of inventory slots: 0=gun */
    public static final int SLOT_COUNT    = 1;
    public static final int SLOT_GUN      = 0;

    /** Number of integers tracked by ContainerData */
    public static final int DATA_COUNT         = 1;
    public static final int DATA_UPGRADE_LEVEL = 0;

    // ─────────────────────────────────────────────────────────
    //  FIELDS
    // ─────────────────────────────────────────────────────────

    /**
     * The item inventory. ItemStackHandler(SLOT_COUNT) creates a 2-slot inventory.
     * We override onContentsChanged() to trigger saving and client sync
     * whenever a slot changes (player puts item in, takes item out, etc.)
     */
    private final ItemStackHandler itemHandler = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            // Mark this BlockEntity as "dirty" — Minecraft will include
            // it in the next chunk save cycle.
            setChanged();

            // Also notify nearby clients so their rendering updates.
            // isClientSide check: level is null during construction,
            // and we only need to notify on the server side.
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    /**
     * The item inventory. ItemStackHandler(SLOT_COUNT) creates a 2-slot inventory.
     * We override onContentsChanged() to trigger saving and client sync
     * whenever a slot changes (player puts item in, takes item out, etc.)
     */

    /**
     * ContainerData implementation.
     *
     * This is an anonymous class (inline implementation) that:
     * - get(index) → returns an int value by index
     * - set(index, value) → sets an int value by index (called by client sync)
     * - getCount() → how many integers are in this data set
     *
     * Minecraft's AbstractContainerMenu calls broadcastChanges() every server
     * tick. If any ContainerData value changed, it sends a packet to the client.
     * The client's menu receives it via set() — keeping them in sync.
     *
     * IMPORTANT: The client can call set() on this data! This is FINE for
     * display-only data like upgradeLevel. Do NOT put security-sensitive
     * values here that the client could manipulate. Actual upgrade logic
     * is always validated server-side (Lesson 4).
     */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_UPGRADE_LEVEL -> {
                    ItemStack gunStack = itemHandler.getStackInSlot(SLOT_GUN);
                    if (gunStack.isEmpty()) yield 0;
                    UpgradeLevelComponent comp = gunStack.get(ModDataComponents.UPGRADE_LEVEL.get());
                    yield (comp != null) ? comp.level() : 0;
                }
                default -> throw new IllegalArgumentException("Invalid data index: " + index);
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_UPGRADE_LEVEL -> { /* Server dynamically reads from item, ignore setter */ }
                default -> throw new IllegalArgumentException("Invalid data index: " + index);
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    // ─────────────────────────────────────────────────────────
    //  CONSTRUCTOR
    // ─────────────────────────────────────────────────────────

    /**
     * @param pos   The world position of this block. Stored by super().
     * @param state The BlockState at this position. Stored by super().
     *
     * We pass ModBlockEntityTypes.PACK_A_PUNCH_MACHINE.get() to super()
     * so Minecraft knows what TYPE of BlockEntity this is. This is how
     * the registry and the actual object are linked together at runtime.
     */
    public PackAPunchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PACK_A_PUNCH_MACHINE.get(), pos, state);
    }

    // ─────────────────────────────────────────────────────────
    //  PUBLIC ACCESSORS
    // ─────────────────────────────────────────────────────────

    /**
     * Expose the item handler for:
     *  - The Menu (to build Slot objects from it)
     *  - TaCZ compatibility layer (future lesson)
     *  - Capability registration (future lesson)
     */
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    // ─────────────────────────────────────────────────────────
    //  MenuProvider — Required to open a GUI
    // ─────────────────────────────────────────────────────────

    /**
     * The title shown at the top of the GUI window.
     * Uses the translation key we defined in en_us.json.
     */
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.packapunch.pack_a_punch_machine");
    }

    /**
     * Called SERVER-SIDE when player.openMenu(this) is called in the block.
     * Creates and returns the server-side menu object.
     *
     * @param containerId      Unique ID for this menu session, assigned by the server.
     * @param playerInventory  The player's own 36-slot inventory.
     * @param player           The player opening the menu.
     * @return A new PackAPunchMenu configured with this BlockEntity's data.
     */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // We pass `this` (the BlockEntity) so the Menu can access our
        // itemHandler and data directly — no copying needed.
        return new PackAPunchMenu(containerId, playerInventory, this, this.data);
    }

    // ─────────────────────────────────────────────────────────
    //  NBT SAVE / LOAD — Persistent data storage
    // ─────────────────────────────────────────────────────────

    /**
     * Called when Minecraft saves this chunk to disk.
     * We write all our data into the provided CompoundTag (think of it
     * as a nested key-value map, like JSON but binary).
     *
     * In 1.21.1, HolderLookup.Provider registries is required for
     * ItemStack serialization — Minecraft needs the registry to resolve
     * item IDs correctly across different data packs and mod versions.
     *
     * IMPORTANT: Always call super.saveAdditional() first!
     * The super call writes the BlockEntity's type ID and position —
     * without it, Minecraft won't know what kind of BlockEntity to create
     * when loading the chunk.
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Serialize the entire ItemStackHandler into a sub-tag.
        // This produces: {"inventory": {"Size": 2, "Items": [...]}}
        tag.put("inventory", itemHandler.serializeNBT(registries));
    }

    /**
     * Called when Minecraft loads this chunk from disk.
     * We read from the same CompoundTag structure we wrote in saveAdditional().
     *
     * IMPORTANT: Always call super.loadAdditional() first!
     * It reads the base BlockEntity data before we read our custom data.
     *
     * The "contains" check before reading is defensive programming —
     * if someone loads a world where this BlockEntity had older data
     * (missing keys), we don't crash; we just use default values.
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) {
            itemHandler.deserializeNBT(registries, tag.getCompound("inventory"));
        }
    }

    // ─────────────────────────────────────────────────────────
    //  CLIENT SYNC — Keeping clients up to date
    // ─────────────────────────────────────────────────────────

    /**
     * Called when a client loads the chunk (e.g., player joins or reloads).
     * Returns a packet that carries our data to the client.
     *
     * ClientboundBlockEntityDataPacket.create(this) automatically calls
     * getUpdateTag() to get the data, wraps it in a packet, and sends it.
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Provides the CompoundTag data sent inside getUpdatePacket().
     * We reuse saveWithoutMetadata() which calls saveAdditional() but
     * omits the block type ID and position (the client already knows those).
     *
     * This means when a client loads our chunk, they receive the full
     * inventory and upgrade level — identical to what's on disk.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    // ────────────────────────────────────────────────────────
    //  UPGRADE LOGIC — Server-side only
    // ────────────────────────────────────────────────────────

    /**
     * The ONE place where upgrades are validated and applied.
     * Called by ServerboundUpgradePacket.handle() after security guards pass.
     *
     * LESSON: WHY EVERYTHING HAPPENS HERE (SERVER-SIDE AUTHORITY)
     * ────────────────────────────────────────────────────────
     * The client ONLY sends a "please upgrade" request.
     * The server:
     *   1. Re-checks that an item is in the gun slot
     *   2. Re-checks that enough diamonds are in the payment slot
     *   3. Re-checks that the item isn't already at MAX_UPGRADE_LEVEL
     *   4. ATOMICALLY consumes diamonds AND applies the upgrade
     *
     * ATOMIC OPERATION GUARANTEE:
     *   We use ItemStackHandler.extractItem() which goes through the handler's
     *   change notification system. The gun's Data Component is then written
     *   in-place. Both changes happen in the same game tick on the server thread.
     *   There is NO window between "checking" and "consuming" where an exploit
     *   could sneak in.
     *
     * WHAT HAPPENS AFTER:
     *   - extractItem() triggers onContentsChanged() → setChanged() → chunk save
     *   - setChanged() + sendBlockUpdated() → clients get the updated item stacks
     *   - ContainerData sync automatically updates the displayed upgrade level
     *
     * @param player The ServerPlayer performing the upgrade (for chat feedback)
     */
    public void tryUpgrade(ServerPlayer player) {
        // ── GUARD: Must be on the server ───────────────────────────────
        // This is belt-and-suspenders: the packet handler already ensures this,
        // but we guard here too in case tryUpgrade() is called from other places.
        if (level == null || level.isClientSide()) {
            PackAPunchMod.LOGGER.warn("[PackAPunch] tryUpgrade() called on client side — ignoring!");
            return;
        }

        // ── READ current state from inventory ───────────────────────────
        // getStackInSlot() returns the LIVE reference to the ItemStack in memory.
        // Modifying it in-place is safe here because we call setChanged() after.
        ItemStack gunStack     = itemHandler.getStackInSlot(SLOT_GUN);

        // ── VALIDATION 1: Gun slot must have an item ────────────────────
        if (gunStack.isEmpty()) {
            sendHint(player, "× No item to upgrade!", ChatFormatting.RED);
            return;
        }

        // ── VALIDATION 2: Read current upgrade level from Data Component ──
        // stack.get() returns null if the component is absent (never been upgraded).
        // We treat null as level 0 (unupgraded).
        UpgradeLevelComponent currentComp = gunStack.get(ModDataComponents.UPGRADE_LEVEL.get());
        int currentLevel = (currentComp != null) ? currentComp.level() : 0;

        // ── VALIDATION 3: Level cap check ─────────────────────────────
        if (currentLevel >= com.myname.packapunch.UpgradeConfig.getMaxLevel()) {
            sendHint(player, "❖ Already at maximum level!", ChatFormatting.GOLD);
            return;
        }

        // ── VALIDATION 4: Payment — correct item and sufficient count ───
        int nextLevel = currentLevel + 1;
        net.minecraft.world.item.Item requiredItem = com.myname.packapunch.UpgradeConfig.getItemForLevel(nextLevel);
        int cost = com.myname.packapunch.UpgradeConfig.getCostForLevel(nextLevel);

        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (invStack.is(requiredItem)) count += invStack.getCount();
        }

        if (count < cost) {
            String itemName = requiredItem.getDescription().getString();
            sendHint(player,
                    "× Need " + cost + " " + itemName + " for Level " + nextLevel + "!",
                    ChatFormatting.RED);
            return;
        }

        // ────────────────────────────────────────────────────────
        // ALL VALIDATIONS PASSED — perform the upgrade atomically
        // ────────────────────────────────────────────────────────

        // STEP 1: Consume items from player inventory
        int remainingCost = cost;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (invStack.is(requiredItem)) {
                int shrinkBy = Math.min(remainingCost, invStack.getCount());
                invStack.shrink(shrinkBy);
                remainingCost -= shrinkBy;
                if (remainingCost <= 0) break;
            }
        }

        // STEP 2: Write new upgrade level onto the gun's Data Component.
        // stack.set() mutates the ItemStack in-place. Since we hold a live
        // reference to the slot's item, this updates the stored item directly.
        int newLevel = currentLevel + 1;
        gunStack.set(ModDataComponents.UPGRADE_LEVEL.get(), new UpgradeLevelComponent(newLevel));

        // STEP 4: Mark dirty + push block update.
        // extractItem already triggered onContentsChanged for the payment slot.
        // The gun slot was modified in-place, so we need a manual setChanged().
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        // STEP 5: Feedback to the player
        sendHint(player,
                "❖ Pack-a-Punch Level " + newLevel + "! ★".repeat(newLevel),
                ChatFormatting.GOLD);

        // STEP 6: Audio and Visual Feedback
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.0f);
        level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);

        if (level instanceof ServerLevel serverLevel) {
            double cx = worldPosition.getX() + 0.5;
            double cy = worldPosition.getY() + 1.0;
            double cz = worldPosition.getZ() + 0.5;
            serverLevel.sendParticles(ParticleTypes.ENCHANT, cx, cy, cz, 20, 0.5, 0.5, 0.5, 0.1);
            serverLevel.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 10, 0.2, 0.2, 0.2, 0.05);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 5, 0.3, 0.3, 0.3, 0.1);
        }

        PackAPunchMod.LOGGER.info("[PackAPunch] {} upgraded item '{}' to Level {}",
                player.getName().getString(),
                gunStack.getHoverName().getString(),
                newLevel);
    }

    /**
     * Helper: sends an action bar message to the player.
     * Action bar (true) = message appears above hotbar, not in chat.
     * This gives instant UI feedback without cluttering the chat log.
     */
    private void sendHint(ServerPlayer player, String text, ChatFormatting color) {
        player.displayClientMessage(
                Component.literal(text).withStyle(color),
                true // action bar = above hotbar
        );
    }

    // ─────────────────────────────────────────────────────────
    //  INVENTORY DROP — When the machine is broken
    // ─────────────────────────────────────────────────────────

    /**
     * Call this from PackAPunchBlock.onRemove() to drop inventory items
     * when the machine is broken. This prevents item duplication/deletion bugs.
     *
     * Containers.dropContents() scatters the items around the block position
     * with a slight random velocity, just like a chest being broken.
     */
    public void dropContents() {
        // Convert our ItemStackHandler into a SimpleContainer for the vanilla helper.
        SimpleContainer inv = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            inv.setItem(i, itemHandler.getStackInSlot(i));
        }
        // level is the world. worldPosition is this block's coordinates.
        if (level != null) {
            Containers.dropContents(level, worldPosition, inv);
        }
    }
}
