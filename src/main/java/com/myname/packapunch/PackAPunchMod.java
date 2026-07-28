package com.myname.packapunch;

import com.mojang.logging.LogUtils;
import com.myname.packapunch.network.ServerboundUpgradePacket;
import com.myname.packapunch.registry.ModBlockEntityTypes;
import com.myname.packapunch.registry.ModBlocks;
import com.myname.packapunch.registry.ModCreativeTabs;
import com.myname.packapunch.registry.ModDataComponents;
import com.myname.packapunch.registry.ModItems;
import com.myname.packapunch.registry.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║             PACK-A-PUNCH MOD — MAIN CLASS               ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This is the ENTRY POINT of our mod.
 *
 * @Mod("packapunch") tells NeoForge:
 *   "Hey, when the game loads, find THIS class and call its constructor."
 *
 * The mod ID "packapunch" must:
 *   - Match the mod_id in gradle.properties
 *   - Match the modId in neoforge.mods.toml
 *   - Be lowercase with no spaces
 *
 * NeoForge automatically injects IEventBus and ModContainer
 * into our constructor. We don't create them — NeoForge provides them.
 *
 * IEventBus  = the "radio station" for MOD LOADING events
 * ModContainer = the handle to our own mod's metadata/config
 */
@SuppressWarnings("null")
@Mod(PackAPunchMod.MOD_ID)
public class PackAPunchMod {

    // ─────────────────────────────────────────────────────────
    //  CONSTANTS
    // ─────────────────────────────────────────────────────────

    /**
     * Our mod ID. This is the NAMESPACE for every resource we create.
     *
     * Example: a block named "pack_a_punch_machine" becomes
     *   packapunch:pack_a_punch_machine
     *
     * This string is used EVERYWHERE — block IDs, item IDs, texture paths,
     * recipe paths, NBT keys, network packet IDs. Keep it consistent!
     */
    public static final String MOD_ID = "packapunch";

    /**
     * The logger. Use LOGGER.info(), LOGGER.warn(), LOGGER.error() to
     * print messages to the Minecraft console/log file.
     * NEVER use System.out.println() — it bypasses Minecraft's log system.
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    // ─────────────────────────────────────────────────────────
    //  CONSTRUCTOR — This is called once when the mod loads
    // ─────────────────────────────────────────────────────────

    /**
     * The constructor is your mod's "startup sequence".
     *
     * @param modEventBus  The event bus for mod-loading events.
     *                     We pass this to each registry class so they can
     *                     register themselves with NeoForge.
     * @param modContainer The handle to our mod. Used for config registration.
     *                     (We'll use this in a later lesson for config files.)
     */
    public PackAPunchMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[PackAPunch] Mod is loading...");

        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, com.myname.packapunch.config.ModConfig.SPEC, "packapunch-common.toml");

        // ── Register all our custom Blocks ──────────────────────
        ModBlocks.BLOCKS.register(modEventBus);

        // ── Register all our custom Items ───────────────────────
        ModItems.ITEMS.register(modEventBus);

        // ── Register our Creative Mode Tab ──────────────────────
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // ── Register BlockEntity Types (NEW in Lesson 2) ────────
        // Links PackAPunchBlockEntity class to its registry ID.
        // Must be registered AFTER blocks because it references them.
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);

        // ── Register Menu Types (NEW in Lesson 2) ────────────────
        // Links PackAPunchMenu to its registry ID.
        // Client uses this ID to look up the menu factory when the
        // server sends a "open screen" packet.
        ModMenuTypes.MENU_TYPES.register(modEventBus);

        // ── Register Data Component Types (NEW in Lesson 4) ──────
        // Registers "packapunch:upgrade_level" into the global DataComponentType
        // registry. Must happen before any ItemStack tries to use it.
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);

        // ── Subscribe to mod-loading events ─────────────────────
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterPayloads);

        LOGGER.info("[PackAPunch] Mod loaded successfully!");
    }

    // ─────────────────────────────────────────────────────────
    //  EVENT HANDLERS
    // ─────────────────────────────────────────────────────────

    /**
     * Common Setup — runs on BOTH server and client after all registries
     * are finalized. This is a safe place to:
     *  - Register capability handlers (advanced topics, later lessons)
     *  - Set up inter-mod compatibility (e.g., TaCZ hooks)
     *  - Log confirmation that setup completed
     *
     * IMPORTANT: Do NOT reference block/item instances during class loading.
     * Always access them via supplier lambdas or inside event methods.
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[PackAPunch] Common setup complete.");
        // We will add TaCZ compatibility registration here in a later lesson.
    }

    /**
     * LESSON: HOW PayloadRegistrar WORKS (NeoForge 1.21.1)
     * ──────────────────────────────────────────────────────────
     * RegisterPayloadsEvent fires on the MOD event bus during loading.
     * It gives us a PayloadRegistrar that we use to "announce" our packets
     * to NeoForge's networking layer.
     *
     * event.registrar(modId) → returns an IPayloadRegistrar scoped to our mod.
     * This ensures our packets are only active for our mod's version.
     *
     * .playToServer(TYPE, STREAM_CODEC, handler)
     *   → TYPE        : the ResourceLocation ID of the packet
     *   → STREAM_CODEC: how to encode/decode the packet bytes
     *   → handler     : the function to call when the server receives this packet
     *                   Signature: void handle(T packet, IPayloadContext ctx)
     *
     * After registration:
     *   - The client knows how to SEND this packet type
     *   - The server knows how to RECEIVE and HANDLE it
     *   - NeoForge enforces that both sides agree on the codec
     *
     * .playToClient() would register a packet the server sends to the client.
     * We don't need that here — our upgrade response is via slot sync (automatic).
     */
    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(MOD_ID)
                .playToServer(
                        ServerboundUpgradePacket.TYPE,         // packet identity
                        ServerboundUpgradePacket.STREAM_CODEC, // network codec
                        ServerboundUpgradePacket::handle       // server handler
                );

        LOGGER.info("[PackAPunch] Network payloads registered.");
    }
}
