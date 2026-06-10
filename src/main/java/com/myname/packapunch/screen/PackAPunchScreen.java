package com.myname.packapunch.screen;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.menu.PackAPunchMenu;
import com.myname.packapunch.network.ServerboundUpgradePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║        PACKAPUNCHSCREEN — CLIENT-ONLY RENDERER          ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This class is CLIENT-ONLY. It must NEVER be loaded on a server.
 * (Enforced by @EventBusSubscriber(value = Dist.CLIENT) in ClientSetup.)
 *
 * WHAT THIS CLASS DOES:
 * ──────────────────────────────────────────────────────────
 * Renders the visual GUI each frame:
 *   1. Background texture (our PNG)
 *   2. Text labels (title, "Inventory", upgrade level)
 *   3. Slot item stacks (handled automatically by AbstractContainerScreen)
 *   4. Hover slot highlighting (handled automatically)
 *   5. Item tooltips on hover (we call renderTooltip manually)
 *
 * THIS CLASS DOES NOT:
 * ──────────────────────────────────────────────────────────
 * - Handle game logic (use PackAPunchMenu for that)
 * - Validate upgrades (use PackAPunchBlockEntity / server events)
 * - Modify inventory directly (always goes through the Menu/Container)
 *
 * KEY INHERITED FIELDS FROM AbstractContainerScreen:
 * ──────────────────────────────────────────────────────────
 *   this.menu         → The PackAPunchMenu instance (data access)
 *   this.font         → Minecraft's font renderer
 *   this.imageWidth   → Width of the GUI panel in pixels (we set = 176)
 *   this.imageHeight  → Height of the GUI panel in pixels (we set = 166)
 *   this.leftPos      → Screen X of the GUI's top-left corner (auto-centered)
 *   this.topPos       → Screen Y of the GUI's top-left corner (auto-centered)
 *   this.title        → Component passed from MenuProvider.getDisplayName()
 *   this.playerInventoryTitle → "Inventory" translated string
 *   this.titleLabelX  → X for title text (relative to leftPos), default = 8
 *   this.titleLabelY  → Y for title text (relative to topPos), default = 6
 *   this.inventoryLabelX → X for inventory label, default = 8
 *   this.inventoryLabelY → Y for inventory label, default = imageHeight - 94
 */
@SuppressWarnings("null")
public class PackAPunchScreen extends AbstractContainerScreen<PackAPunchMenu> {

    // ─────────────────────────────────────────────────────────
    //  TEXTURE RESOURCE LOCATION
    // ─────────────────────────────────────────────────────────

    /**
     * ResourceLocation points to our GUI texture file.
     *
     * Path breakdown:
     *   namespace  = "packapunch"  (our mod ID)
     *   path       = "textures/gui/pack_a_punch_machine.png"
     *
     * Full file path on disk:
     *   src/main/resources/assets/packapunch/textures/gui/pack_a_punch_machine.png
     *
     * ResourceLocation.fromNamespaceAndPath() is the 1.21.1 preferred API.
     * The older new ResourceLocation(namespace, path) still works but is deprecated.
     */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PackAPunchMod.MOD_ID, "textures/gui/pack_a_punch_machine.png"
    );

    // ─────────────────────────────────────────────────────────
    //  TEXT COLORS
    // ─────────────────────────────────────────────────────────

    /** White text for dark backgrounds. 0xFFFFFF = RGB white. */
    private static final int COLOR_WHITE        = 0xFFFFFF;
    /** Gold color for upgrade level display. */
    private static final int COLOR_GOLD         = 0xFFD700;
    /** Light gray for secondary labels. */
    private static final int COLOR_LIGHT_GRAY   = 0xAAAAAA;

    // ────────────────────────────────────────────────────────
    //  WIDGETS
    // ────────────────────────────────────────────────────────

    /**
     * The upgrade button widget.
     * Stored as a field so we can update its active/disabled state
     * each render frame based on current slot contents and upgrade level.
     */
    private Button upgradeButton;

    // ─────────────────────────────────────────────────────────
    //  CONSTRUCTOR
    // ─────────────────────────────────────────────────────────

    /**
     * Called by MenuScreens when the client receives the open-screen packet.
     *
     * The constructor signature MUST be:
     *   (MenuType, Inventory, Component)
     * because AbstractContainerScreen<M> requires exactly this.
     * The MenuScreens.register() call (in ClientSetup) maps our MenuType
     * to this constructor via a method reference (PackAPunchScreen::new).
     *
     * @param menu            Our PackAPunchMenu — gives access to upgrade level etc.
     * @param playerInventory The player's inventory (used by super for the title).
     * @param title           The display name from BlockEntity.getDisplayName().
     *
     * imageWidth / imageHeight control the GUI size in pixels.
     * They also affect:
     *   - leftPos = (screenWidth - imageWidth) / 2   (auto-calculated by super)
     *   - topPos  = (screenHeight - imageHeight) / 2
     *   - inventoryLabelY = imageHeight - 94
     * Change imageWidth/imageHeight here to resize the whole GUI.
     */
    public PackAPunchScreen(PackAPunchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = 176;
        this.imageHeight = 186;
    }

    // ─────────────────────────────────────────────────────────
    //  INITIALIZATION
    // ─────────────────────────────────────────────────────────

    /**
     * Called once when the screen is first displayed (after the constructor).
     * We create the Upgrade Button widget here.
     *
     * LESSON: WHY THE BUTTON IS ONLY A UX HINT
     * ────────────────────────────────────────────────────────
     * The button is disabled on the client when:
     *   - Gun slot is empty
     *   - Payment slot doesn't have enough diamonds
     *   - Item is already at MAX level
     *
     * HOWEVER: This client-side check is ONLY for user experience.
     * A hacked client could send the packet anyway even if the button
     * is disabled. The server ALWAYS re-validates everything in
     * PackAPunchBlockEntity.tryUpgrade() regardless of what the client sends.
     *
     * The rule is:
     *   "Trust the server. The client only SHOWS what the server decides."
     */
    @Override
    protected void init() {
        super.init();

        // Center the title label horizontally over the machine area.
        this.titleLabelX = (this.imageWidth / 2) - (this.font.width(this.title) / 2);

        // ── Create the Upgrade Button ─────────────────────────────────
        // Button.builder(label, onPress) is the Minecraft 1.21.1 builder API.
        // .bounds(x, y, width, height) sets the screen position.
        // The x/y here are ABSOLUTE screen coordinates (not relative to GUI).
        //   leftPos = screen X of our GUI's top-left corner
        //   topPos  = screen Y of our GUI's top-left corner
        // We center the button horizontally: leftPos + (imageWidth/2) - (100/2)
        upgradeButton = this.addRenderableWidget(
                Button.builder(
                        Component.literal("☆ UPGRADE"),
                        button -> onUpgradeClicked()
                )
                .bounds(
                        this.leftPos + (this.imageWidth / 2) - 45, // center (Width 90)
                        this.topPos + 76,                           // moved down slightly to fit labels
                        90,   // intentional visual width
                        20    // height
                )
                .build()
        );
    }

    // ─────────────────────────────────────────────────────────
    //  RENDER — Main frame render entry point
    // ─────────────────────────────────────────────────────────

    /**
     * Called every render frame (typically 60+ times per second).
     *
     * We update the button's state before rendering so it reflects
     * the latest slot contents (which may have changed since last frame).
     * Then we call super.render() which draws the background, slots, and labels.
     * Finally we draw the item tooltip on top of everything else.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (upgradeButton != null) {
            updateButtonState();
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        // ── Pulsing Glow Effect around active button ─────────────
        if (upgradeButton != null && upgradeButton.active) {
            long millis = net.minecraft.Util.getMillis();
            float pulse = (float) (Math.sin(millis / 200.0) + 1.0) / 2.0f;
            int alpha = (int) (60 + pulse * 60); // oscillate between 60 and 120
            int color = (alpha << 24) | 0xFFD700; // Gold color
            
            int bx = upgradeButton.getX();
            int by = upgradeButton.getY();
            int bw = upgradeButton.getWidth();
            int bh = upgradeButton.getHeight();
            
            // Draw 2px border around the button
            graphics.fill(bx - 2, by - 2, bx + bw + 2, by, color); // Top
            graphics.fill(bx - 2, by + bh, bx + bw + 2, by + bh + 2, color); // Bottom
            graphics.fill(bx - 2, by, bx, by + bh, color); // Left
            graphics.fill(bx + bw, by, bx + bw + 2, by + bh, color); // Right

            // ── Slot Readiness Glow (Center Core) ─────────────
            // Visual readiness feedback when active
            int rx = this.leftPos + 80;
            int ry = this.topPos + 36;
            graphics.fill(rx - 2, ry - 2, rx + 18, ry, color); // Top
            graphics.fill(rx - 2, ry + 16, rx + 18, ry + 18, color); // Bottom
            graphics.fill(rx - 2, ry, rx, ry + 16, color); // Left
            graphics.fill(rx + 16, ry, rx + 18, ry + 16, color); // Right
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Updates the Upgrade Button's active state and label each frame.
     *
     * Reads slot contents and upgrade level to determine whether the
     * player CAN currently upgrade. This is a CLIENT-SIDE UX hint only.
     */
    private void updateButtonState() {
        ItemStack gunStack = this.menu.getSlot(0).getItem();

        // 1. Missing Weapon
        if (gunStack.isEmpty()) {
            upgradeButton.setMessage(Component.literal("INSERT WEAPON"));
            upgradeButton.active = false;
            return;
        }

        // 2. Max Level
        int level = this.menu.getUpgradeLevel();
        if (com.myname.packapunch.UpgradeConfig.isMaxLevel(level)) {
            upgradeButton.setMessage(Component.literal("❖ MAX LEVEL"));
            upgradeButton.active = false;
            return;
        }

        // 3. Insufficient Payment
        int nextLevel = level + 1;
        net.minecraft.world.item.Item requiredItem = com.myname.packapunch.UpgradeConfig.getItemForLevel(nextLevel);
        int nextCost = com.myname.packapunch.UpgradeConfig.getCostForLevel(nextLevel);

        int count = 0;
        net.minecraft.world.entity.player.Inventory playerInv = net.minecraft.client.Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < playerInv.getContainerSize(); i++) {
            ItemStack invStack = playerInv.getItem(i);
            if (invStack.is(requiredItem)) {
                count += invStack.getCount();
            }
        }

        if (count < nextCost) {
            String itemName = requiredItem.getDescription().getString();
            upgradeButton.setMessage(Component.literal("NEED " + nextCost + " " + itemName));
            upgradeButton.active = false;
            return;
        }

        // 4. Ready
        float multiplier = com.myname.packapunch.UpgradeConfig.getMultiplierForLevel(nextLevel);
        upgradeButton.setMessage(Component.literal("☆ UPGRADE x" + multiplier));
        upgradeButton.active = true;
    }

    /**
     * Called when the player clicks the Upgrade button.
     *
     * Sends a ServerboundUpgradePacket to the server.
     * The packet contains the machine's BlockPos so the server knows
     * which machine to run the upgrade on.
     *
     * PacketDistributor.sendToServer() is NeoForge 1.21.1's API for
     * sending a registered CustomPacketPayload from the client to the server.
     * The packet must have been registered in RegisterPayloadsEvent.
     */
    private void onUpgradeClicked() {
        PacketDistributor.sendToServer(
                new ServerboundUpgradePacket(this.menu.getBlockPos())
        );
    }

    // ─────────────────────────────────────────────────────────
    //  RENDERBG — Draw the background texture
    // ─────────────────────────────────────────────────────────

    /**
     * Called by the parent render() to draw the GUI background texture.
     *
     * graphics.blit() — blits (copies) a rectangular region of a texture
     * onto the screen.
     *
     * Signature: blit(texture, screenX, screenY, texU, texV, width, height)
     *   texture  = our ResourceLocation (the PNG file)
     *   screenX  = this.leftPos  (top-left X of GUI on screen, auto-centered)
     *   screenY  = this.topPos   (top-left Y of GUI on screen, auto-centered)
     *   texU     = 0  (start reading texture from pixel X=0)
     *   texV     = 0  (start reading texture from pixel Y=0)
     *   width    = this.imageWidth  (176 px — how wide to draw)
     *   height   = this.imageHeight (166 px — how tall to draw)
     *
     * This assumes a 256×256 texture. Our PNG is 256×256 and the
     * actual GUI art occupies the top-left 176×166 region.
     *
     * The remaining area of the texture (right and bottom) is transparent
     * and is never drawn — it's just padding to meet the 256×256 requirement.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE,
                this.leftPos,        // screen X of GUI top-left
                this.topPos,         // screen Y of GUI top-left
                0,                   // texture U offset (start at left edge)
                0,                   // texture V offset (start at top edge)
                this.imageWidth,     // draw 176 pixels wide
                this.imageHeight);   // draw 166 pixels tall
    }

    // ─────────────────────────────────────────────────────────
    //  RENDERLABELS — Draw text labels
    // ─────────────────────────────────────────────────────────

    /**
     * Called after renderBg() to draw text onto the GUI.
     *
     * CRITICAL: Coordinates here are RELATIVE to (leftPos, topPos).
     *   x=0, y=0 = the top-left corner of the GUI panel.
     *   x=8, y=6 = 8 pixels from the left edge, 6 pixels from the top.
     *
     * This is different from renderBg() where we use leftPos/topPos
     * to position in screen space. In renderLabels() the translation
     * matrix is already shifted by (leftPos, topPos) by the parent class.
     *
     * We override completely (no super call) so we can use our own colors.
     * The super would draw text in dark gray (0x404040), invisible on dark bg.
     *
     * drawString(font, text, x, y, color, shadow):
     *   font   = this.font  (Minecraft's default font renderer)
     *   text   = Component or String
     *   x, y   = position RELATIVE to GUI top-left
     *   color  = 0xRRGGBB (no alpha needed here)
     *   shadow = true adds a 1px black drop shadow (improves readability)
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {

        // ── GUI Title (e.g. "Pack-a-Punch Machine") ──────────────────────────
        // titleLabelX was centered in init(). titleLabelY = 6 (default).
        graphics.drawString(this.font, this.title,
                this.titleLabelX, this.titleLabelY,
                COLOR_WHITE, false);   // white, no shadow (clean on dark bg)

        // (We omit rendering the "Inventory" label to save space for our dynamic stats)

        // ── Upgrade Level Display ─────────────────────────────────────────────────────
        // All values are read from UpgradeConfig — never hardcoded here.
        int level = this.menu.getUpgradeLevel();

        String levelText = "Level " + level + " / " + com.myname.packapunch.UpgradeConfig.getMaxLevel();
        String multiplierText = "Multiplier x" + com.myname.packapunch.UpgradeConfig.getMultiplierForLevel(level);

        int textX = (this.imageWidth / 2) - (this.font.width(levelText) / 2);
        graphics.drawString(this.font, levelText,
                textX, 50,
                COLOR_GOLD, true);

        int multX = (this.imageWidth / 2) - (this.font.width(multiplierText) / 2);
        graphics.drawString(this.font, multiplierText,
                multX, 60,
                COLOR_LIGHT_GRAY, true);

        // ── Next Upgrade Requirement — fully driven by UpgradeConfig ────────────────
        // This mirrors exactly what the server will validate in tryUpgrade().
        // The client NEVER assumes any item type independently.
        if (!com.myname.packapunch.UpgradeConfig.isMaxLevel(level)) {
            int nextLevel = level + 1;
            net.minecraft.world.item.Item reqItem = com.myname.packapunch.UpgradeConfig.getItemForLevel(nextLevel);
            int reqCost = com.myname.packapunch.UpgradeConfig.getCostForLevel(nextLevel);
            String reqName = reqItem.getDescription().getString();
            String reqText = "Cost: " + reqCost + "x " + reqName;
            int reqX = (this.imageWidth / 2) - (this.font.width(reqText) / 2);
            graphics.drawString(this.font, reqText, reqX, 70, 0x55FFFF, true);
        } else {
            String maxText = "✦ MAX LEVEL REACHED";
            int maxX = (this.imageWidth / 2) - (this.font.width(maxText) / 2);
            graphics.drawString(this.font, maxText, maxX, 70, 0xFF55FF, true);
        }


    }
}
