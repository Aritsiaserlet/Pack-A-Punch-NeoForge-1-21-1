package com.myname.packapunch.registry;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.component.UpgradeLevelComponent;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║       MODDATACOMPONENTS — DATA COMPONENT REGISTRY       ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This class registers our Data Component TYPES with Minecraft's registry.
 *
 * WHAT IS A DataComponentType?
 * ──────────────────────────────────────────────────────────
 * A DataComponentType<T> is a REGISTRY ENTRY that describes HOW to store
 * a component of type T. It contains:
 *   - The namespaced ID: "packapunch:upgrade_level"
 *   - The persistence codec (for disk storage)
 *   - The stream codec (for network packets)
 *   - Optional flags: networkSynchronized, cacheEncoding, etc.
 *
 * The type itself (UpgradeLevelComponent) is just a plain Java record.
 * The DataComponentType<UpgradeLevelComponent> is its registry "slot".
 *
 * ANALOGY:
 * ──────────────────────────────────────────────────────────
 * Think of it like this:
 *   UpgradeLevelComponent  →  the data (e.g., level = 2)
 *   DataComponentType      →  the schema (how to read/write that data)
 *   DeferredHolder         →  the registered handle (like DeferredBlock/DeferredItem)
 *
 * You READ from an ItemStack with:
 *   ItemStack stack = ...;
 *   UpgradeLevelComponent comp = stack.get(ModDataComponents.UPGRADE_LEVEL.get());
 *   // comp is null if the component is not present
 *
 * You WRITE to an ItemStack with:
 *   stack.set(ModDataComponents.UPGRADE_LEVEL.get(), new UpgradeLevelComponent(2));
 *
 * You REMOVE from an ItemStack with:
 *   stack.remove(ModDataComponents.UPGRADE_LEVEL.get());
 *
 * WHY DeferredRegister.createDataComponents()?
 * ──────────────────────────────────────────────────────────
 * DeferredRegister.DataComponents is a NeoForge shorthand that:
 *   - Creates a DeferredRegister<DataComponentType<?>>
 *   - Points to Registries.DATA_COMPONENT_TYPE
 *   - Provides the registerComponentType() builder method
 *
 * registerComponentType() takes a UnaryOperator on DataComponentType.Builder<T>
 * so you can fluently configure persistence and network sync.
 *
 * REGISTRATION FLOW:
 * ──────────────────────────────────────────────────────────
 * 1. PackAPunchMod constructor calls DATA_COMPONENTS.register(modEventBus)
 * 2. During Minecraft's registry phase, NeoForge fires the Register event
 * 3. Our lambda runs: registers "packapunch:upgrade_level" into the global registry
 * 4. From this point, any ItemStack in the game can carry our component
 */
@SuppressWarnings({"null", "removal"})
public class ModDataComponents {

    // ─────────────────────────────────────────────────────────
    //  THE DEFERRED REGISTER
    // ─────────────────────────────────────────────────────────

    /**
     * DeferredRegister.DataComponents — typed register for DataComponentTypes.
     * Made public so PackAPunchMod can call DATA_COMPONENTS.register(modEventBus).
     */
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(PackAPunchMod.MOD_ID);

    // ─────────────────────────────────────────────────────────
    //  COMPONENT REGISTRATIONS
    // ─────────────────────────────────────────────────────────

    /**
     * THE UPGRADE_LEVEL COMPONENT
     * ─────────────────────────────────────────────────────────
     * Registry ID: "packapunch:upgrade_level"
     *
     * .persistent(CODEC)
     *   → Use UpgradeLevelComponent.CODEC when reading/writing world save files.
     *   → Without this, the component would NOT survive relogs or server restarts.
     *   → With it, the NBT tag "packapunch:upgrade_level": N is written into
     *     the ItemStack's component tag inside chunk data.
     *
     * .networkSynchronized(STREAM_CODEC)
     *   → Use UpgradeLevelComponent.STREAM_CODEC when sending ItemStacks over
     *     the network (server→client slot sync, etc.)
     *   → Without this, the client's copy of the item would NOT have the component.
     *   → With it, the tooltip and any client rendering see the correct level.
     *
     * DeferredHolder<DataComponentType<?>, DataComponentType<UpgradeLevelComponent>>
     *   → The outer type is DataComponentType<?> (the registry type)
     *   → The inner type is DataComponentType<UpgradeLevelComponent> (our specific type)
     *   → .get() returns the inner type for use with stack.get() / stack.set()
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UpgradeLevelComponent>>
            UPGRADE_LEVEL = DATA_COMPONENTS.registerComponentType(
                    "upgrade_level",
                    builder -> builder
                            .persistent(UpgradeLevelComponent.CODEC)
                            .networkSynchronized(UpgradeLevelComponent.STREAM_CODEC)
            );
}
