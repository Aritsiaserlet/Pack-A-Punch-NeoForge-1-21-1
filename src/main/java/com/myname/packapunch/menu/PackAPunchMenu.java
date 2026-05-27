package com.myname.packapunch.menu;

import com.myname.packapunch.blockentity.PackAPunchBlockEntity;
import com.myname.packapunch.registry.ModBlocks;
import com.myname.packapunch.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║            PACKAPUNCHMENU — CONTAINER LOGIC             ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * The Menu handles the LOGIC layer of our GUI.
 * The Screen (Lesson 3) handles the VISUAL layer.
 *
 * SLOT LAYOUT (indices are used for shift-click routing):
 * ──────────────────────────────────────────────────────────
 *   Slot  0  → Gun slot (SLOT_GUN in BlockEntity)
 *   Slot  1  → Payment slot (SLOT_PAYMENT in BlockEntity)
 *   Slots 2–28  → Player inventory (3 rows × 9 columns)
 *   Slots 29–37 → Player hotbar (1 row × 9)
 *
 * PIXEL POSITIONS (origin = top-left of GUI background):
 * ──────────────────────────────────────────────────────────
 *   Machine area (centered in upper half of GUI):
 *     Gun slot:     x=44,  y=35
 *     Payment slot: x=116, y=35
 *
 *   Player inventory (standard Minecraft layout):
 *     Row 0: x=8, y=84
 *     Row 1: x=8, y=102
 *     Row 2: x=8, y=120
 *   Player hotbar: x=8, y=142
 *
 * Note: These pixel positions are used by the Screen in Lesson 3.
 *   The Menu stores the positions so the Screen can query them.
 *   Changing them here automatically moves the slots in the rendered GUI.
 *
 * THE TWO-CONSTRUCTOR PATTERN:
 * ──────────────────────────────────────────────────────────
 * Constructor 1 (CLIENT) — called by MenuType factory on the client:
 *   PackAPunchMenu(int, Inventory, FriendlyByteBuf)
 *   Creates a dummy ItemStackHandler — its contents are immediately
 *   overwritten by Minecraft's slot sync packets from the server.
 *
 * Constructor 2 (SERVER) — called by BlockEntity.createMenu():
 *   PackAPunchMenu(int, Inventory, PackAPunchBlockEntity, ContainerData)
 *   Uses the real BlockEntity's ItemStackHandler directly.
 *
 * Both constructors route to the shared private constructor which
 * does the actual slot layout work — DRY (Don't Repeat Yourself).
 */
@SuppressWarnings("null")
public class PackAPunchMenu extends AbstractContainerMenu {

    // ─────────────────────────────────────────────────────────
    //  GUI LAYOUT CONSTANTS
    // ─────────────────────────────────────────────────────────

    // Machine slot positions (in pixels, relative to GUI background top-left)
    public static final int GUN_SLOT_X     = 44;
    public static final int GUN_SLOT_Y     = 35;
    public static final int PAYMENT_SLOT_X = 114;
    public static final int PAYMENT_SLOT_Y = 35;

    // Player inventory position
    private static final int INV_START_X = 8;
    private static final int INV_START_Y = 104;
    private static final int HOT_START_Y = 162;

    // ─────────────────────────────────────────────────────────
    //  FIELDS
    // ─────────────────────────────────────────────────────────

    /**
     * Synced integer data. Used to read upgradeLevel on both sides.
     * Server ContainerData writes → client ContainerData receives automatically.
     */
    private final ContainerData data;

    /**
     * The world position of the Pack-a-Punch machine this menu belongs to.
     *
     * LESSON: HOW FriendlyByteBuf SYNCHRONIZATION WORKS
     * ────────────────────────────────────────────────────────
     * When a player opens the GUI, this happens:
     *
     * SERVER:
     *   PackAPunchBlock.useWithoutItem() calls:
     *     player.openMenu(blockEntity, buf -> buf.writeBlockPos(pos))
     *   NeoForge's IMenuTypeExtension sees the extra Consumer<FriendlyByteBuf>.
     *   It writes the BlockPos bytes into the open-menu packet before sending.
     *
     * CLIENT:
     *   Minecraft receives the open-menu packet.
     *   It calls MenuType's factory (our IMenuTypeExtension lambda):
     *     new PackAPunchMenu(containerId, playerInventory, buf)
     *   The buf now contains the BlockPos bytes the server wrote.
     *   Our client constructor reads: blockPos = buf.readBlockPos()
     *
     * RESULT:
     *   Both the server menu AND the client menu have the same BlockPos.
     *   The client uses blockPos for:
     *     1. stillValid() — checking player distance from the machine
     *     2. ServerboundUpgradePacket — telling the server WHICH machine to upgrade
     *
     * WHY NOT USE ContainerData FOR THIS?
     *   ContainerData syncs integers AFTER the menu is open. We need the
     *   BlockPos BEFORE/AT open time. FriendlyByteBuf is the correct tool.
     *   Also, a BlockPos is 3 ints (x, y, z) — awkward to split into ContainerData.
     */
    private final BlockPos blockPos;

    // ─────────────────────────────────────────────────────────
    //  CONSTRUCTORS
    // ─────────────────────────────────────────────────────────

    /**
     * CLIENT-SIDE CONSTRUCTOR — called by IMenuTypeExtension factory.
     *
     * LESSON: FriendlyByteBuf on the client side
     * ────────────────────────────────────────────────────────
     * The FriendlyByteBuf buf is populated by the server in
     * PackAPunchBlock.useWithoutItem() via the Consumer<FriendlyByteBuf> passed
     * to player.openMenu(). The server writes:
     *   buf.writeBlockPos(pos)
     *
     * The client reads in the SAME ORDER the server wrote:
     *   buf.readBlockPos()
     *
     * This gives the client the exact BlockPos of the machine — needed for
     * stillValid() distance checks and for the upgrade packet's target address.
     *
     * We create a dummy ItemStackHandler; Minecraft will immediately overwrite
     * slot 0 and slot 1 with the server's actual contents via sync packets.
     * SimpleContainerData(DATA_COUNT) starts at all zeros; the server fills it in.
     */
    public PackAPunchMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new ItemStackHandler(PackAPunchBlockEntity.SLOT_COUNT),
                new SimpleContainerData(PackAPunchBlockEntity.DATA_COUNT),
                buf.readBlockPos()); // ← Read the BlockPos the server wrote
    }

    /**
     * SERVER-SIDE CONSTRUCTOR — called by PackAPunchBlockEntity.createMenu().
     *
     * Takes the real BlockEntity so we get a live reference to its
     * ItemStackHandler and ContainerData. Changes the player makes to
     * slots in this menu directly modify the BlockEntity's inventory.
     *
     * blockEntity.getBlockPos() gives us the world position, stored
     * in the blockPos field for stillValid() and packet validation.
     */
    public PackAPunchMenu(int containerId, Inventory playerInventory,
                          PackAPunchBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getItemHandler(), data,
                blockEntity.getBlockPos()); // ← Pass the machine's position
    }

    /**
     * SHARED PRIVATE CONSTRUCTOR — does all the real work.
     *
     * Both public constructors delegate here with either real or dummy
     * inventory data. This avoids code duplication.
     *
     * The blockPos parameter is used for:
     *   - stillValid() — checking player proximity to the machine
     *   - getBlockPos() — exposed so the Screen can build the upgrade packet
     */
    private PackAPunchMenu(int containerId, Inventory playerInventory,
                           IItemHandler machineItems, ContainerData data, BlockPos blockPos) {
        super(ModMenuTypes.PACK_A_PUNCH_MACHINE.get(), containerId);

        this.data = data;
        this.blockPos = blockPos;

        // ── Machine Slots (SlotItemHandler = NeoForge's IItemHandler-aware Slot) ──
        // SlotItemHandler links the menu slot to a specific slot in our ItemStackHandler.
        // The x,y position tells the Screen where to render the slot graphic.

        // Slot 0: Gun slot — top-left of machine area
        this.addSlot(new SlotItemHandler(machineItems, PackAPunchBlockEntity.SLOT_GUN,
                GUN_SLOT_X, GUN_SLOT_Y));

        // Slot 1: Payment slot — top-right of machine area
        this.addSlot(new SlotItemHandler(machineItems, PackAPunchBlockEntity.SLOT_PAYMENT,
                PAYMENT_SLOT_X, PAYMENT_SLOT_Y));

        // ── Player Inventory (slots 2–28) ─────────────────────────────────────
        // Three rows of 9 slots. Uses vanilla Slot class (not SlotItemHandler)
        // because playerInventory is a vanilla Container, not IItemHandler.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                // playerInventory.items starts at index 9 (hotbar is 0-8).
                // The formula (row * 9 + col + 9) correctly maps to the
                // non-hotbar inventory items.
                this.addSlot(new Slot(playerInventory,
                        row * 9 + col + 9,               // inventory index
                        INV_START_X + col * 18,           // pixel X
                        INV_START_Y + row * 18));         // pixel Y
            }
        }

        // ── Player Hotbar (slots 29–37) ───────────────────────────────────────
        // Hotbar items are indices 0–8 in playerInventory.items.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory,
                    col,                               // inventory index (0-8 = hotbar)
                    INV_START_X + col * 18,            // pixel X
                    HOT_START_Y));                     // pixel Y (one row below inventory)
        }

        // ── Register ContainerData ────────────────────────────────────────────
        // addDataSlots() registers the ContainerData with this menu.
        // Minecraft will now automatically sync all integers in `data`
        // between the server BlockEntity and the client menu every tick.
        addDataSlots(data);
    }

    // ─────────────────────────────────────────────────────────
    //  DATA ACCESSORS
    // ─────────────────────────────────────────────────────────

    /**
     * Read the synced upgrade level.
     * Safe to call on both server and client — ContainerData handles sync.
     */
    public int getUpgradeLevel() {
        return data.get(PackAPunchBlockEntity.DATA_UPGRADE_LEVEL);
    }

    /**
     * The world position of the machine this menu is bound to.
     *
     * Used by:
     *   - stillValid() — ContainerLevelAccess checks player ≤ 8 blocks away
     *   - PackAPunchScreen — builds ServerboundUpgradePacket with this pos
     *   - ServerboundUpgradePacket.handle() — re-validates on the server
     *
     * On the CLIENT: read from the FriendlyByteBuf sent by the server.
     * On the SERVER: copied from blockEntity.getBlockPos() at menu creation.
     * Both sides always agree on the same value.
     */
    public BlockPos getBlockPos() {
        return blockPos;
    }

    // ─────────────────────────────────────────────────────────
    //  STILL VALID CHECK
    // ─────────────────────────────────────────────────────────

    /**
     * Called every tick while the menu is open.
     * Returns false when the menu should force-close:
     *   - The block was broken while the GUI was open
     *   - The player moved too far away (> 8 blocks)
     *
     * LESSON: HOW ContainerLevelAccess WORKS
     * ────────────────────────────────────────────────────────
     * ContainerLevelAccess.create(level, pos) gives AbstractContainerMenu
     * access to both the world (Level) and the block position.
     *
     * AbstractContainerMenu.stillValid(access, player, block) then:
     *   1. Checks that the block at `pos` is the expected block type
     *   2. Checks that the player is within 64 block-distance-squared (~8 blocks)
     *
     * This replaces our old temporary "return true" with a proper check that:
     *   - Auto-closes the GUI if the machine is broken
     *   - Auto-closes the GUI if the player walks too far away
     *
     * Previously we couldn't do this because we had no BlockPos. Now we do!
     */
    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(
                ContainerLevelAccess.create(player.level(), blockPos),
                player,
                ModBlocks.PACK_A_PUNCH_MACHINE.get()
        );
    }

    // ─────────────────────────────────────────────────────────
    //  SHIFT-CLICK (Quick Move) LOGIC
    // ─────────────────────────────────────────────────────────

    /**
     * Called when the player Shift+Clicks a slot.
     * We decide WHERE the item should go based on which slot was clicked.
     *
     * Routing rules:
     *   Click on machine slot (0-1) → try to move to player inventory (2-37)
     *   Click on player inventory (2-28) → try machine slots first, then hotbar
     *   Click on hotbar (29-37) → try machine slots first, then inventory
     *
     * moveItemStackTo(stack, from, to, reverse):
     *   Tries to merge stack into any slot in the [from, to) range.
     *   reverse=true means it tries the last slot first (used for hotbar→inventory).
     *
     * IMPORTANT: You MUST copy the stack before modification and return
     * EMPTY if nothing was moved, or the copied stack if something moved.
     * Returning wrong values here causes item duplication/deletion bugs!
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack returnStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            returnStack = slotStack.copy(); // Copy BEFORE modification

            if (slotIndex < PackAPunchBlockEntity.SLOT_COUNT) {
                // Clicked machine slot → move to player inventory (slots 2–37)
                if (!this.moveItemStackTo(slotStack, PackAPunchBlockEntity.SLOT_COUNT,
                        this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Clicked player slot → move to machine slots (slots 0–1)
                if (!this.moveItemStackTo(slotStack, 0,
                        PackAPunchBlockEntity.SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return returnStack;
    }
}
