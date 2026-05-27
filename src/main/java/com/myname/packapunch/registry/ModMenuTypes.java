package com.myname.packapunch.registry;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.menu.PackAPunchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MODMENUTYPES — Menu (Container) Type Registry
 *
 * MenuType is the "factory ID" for a Menu class.
 *
 * HOW THE OPEN-MENU FLOW WORKS:
 * ──────────────────────────────────────────────────────────────
 * SERVER SIDE:
 *   1. Player right-clicks the block
 *   2. Block calls: player.openMenu(blockEntity)
 *   3. NeoForge sends a packet to the client:
 *      "Open menu with MenuType ID = 'packapunch:pack_a_punch_machine'"
 *
 * CLIENT SIDE:
 *   4. Packet received → look up MenuType in registry
 *   5. Call MenuType's factory (IMenuTypeExtension lambda)
 *   6. Factory calls: new PackAPunchMenu(containerId, playerInventory, buf)
 *   7. The returned Menu is handed to the Screen for rendering
 *
 * IMenuTypeExtension.create() vs vanilla MenuType:
 * ──────────────────────────────────────────────────────────────
 * Vanilla MenuType factory: (containerId, inventory) → Menu
 * NeoForge IMenuTypeExtension: (containerId, inventory, FriendlyByteBuf) → Menu
 *
 * The extra FriendlyByteBuf lets the server send any data the client
 * needs to build the menu (e.g., BlockPos to look up the BlockEntity).
 * In our setup we use a "dummy inventory" approach on the client
 * (slots are synced automatically), so the buf is empty.
 */
@SuppressWarnings("null")
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PackAPunchMod.MOD_ID);

    /**
     * Our Pack-a-Punch menu type.
     *
     * IMenuTypeExtension.create(PackAPunchMenu::new) references the
     * three-argument constructor:
     *   PackAPunchMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf)
     *
     * This is the CLIENT-SIDE constructor called when the open-menu
     * packet arrives. The SERVER-SIDE constructor takes a BlockEntity
     * directly (bypassing the packet system entirely — it's called locally).
     */
    public static final DeferredHolder<MenuType<?>, MenuType<PackAPunchMenu>>
            PACK_A_PUNCH_MACHINE = MENU_TYPES.register(
                    "pack_a_punch_machine",
                    () -> IMenuTypeExtension.create(PackAPunchMenu::new)
            );
}
