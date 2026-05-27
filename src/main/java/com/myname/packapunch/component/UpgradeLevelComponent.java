package com.myname.packapunch.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║         UPGRADELEVELCOMPONENT — DATA COMPONENT          ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * A Data Component is a typed, persistent piece of data attached directly
 * to an ItemStack. Think of it as a strongly-typed replacement for raw NBT tags.
 *
 * WHY DATA COMPONENTS INSTEAD OF RAW NBT?
 * ──────────────────────────────────────────────────────────
 * Old approach (NBT):
 *   stack.getOrCreateTag().putInt("mymod_upgrade", 2);
 *   Problems:
 *     - Key string collisions with other mods ("upgrade" is too generic)
 *     - No type safety — any mod could write the wrong type
 *     - Manual codec/serialization for every field
 *     - Won't survive item splits unless you hook into copy events
 *
 * New approach (Data Component, NeoForge 1.21.1):
 *   stack.set(ModDataComponents.UPGRADE_LEVEL.get(), new UpgradeLevelComponent(2));
 *   Benefits:
 *     ✅ Type-safe at compile time
 *     ✅ Namespaced registry ID: "packapunch:upgrade_level" — no collisions
 *     ✅ Automatically serialized via our declared CODEC
 *     ✅ Automatically networked via our declared STREAM_CODEC
 *     ✅ Survives item splits, crafts, and modded inventory transfers
 *     ✅ Persists through relogs and chunk unload/reload
 *
 * HOW DOES PERSISTENCE WORK?
 * ──────────────────────────────────────────────────────────
 * When Minecraft saves an ItemStack to disk (inside a chest, player inventory,
 * BlockEntity, etc.), it serializes ALL attached Data Components using their
 * registered CODECs. When loading, it deserializes them back.
 *
 * The DataComponentType registration in ModDataComponents.java tells NeoForge:
 *   "This component type uses THIS codec for disk storage and THIS stream codec
 *    for network packets."
 *
 * HOW DOES SURVIVAL ACROSS INVENTORY MOVES WORK?
 * ──────────────────────────────────────────────────────────
 * An ItemStack is an OBJECT with identity. When Minecraft moves items between
 * inventories (chest→player, crafting, etc.), it copies the ItemStack. ItemStack
 * copy() copies ALL components. So the upgrade level travels with the item
 * everywhere — no special hooks needed.
 *
 * RECORD PATTERN:
 * ──────────────────────────────────────────────────────────
 * We use a Java record because Data Components are VALUE OBJECTS:
 *   - Immutable (all fields are final)
 *   - Equality by value (two components with level=2 are considered equal)
 *   - No subclassing needed
 *   - Compact syntax
 *
 * NeoForge requires Data Components to implement equals() and hashCode()
 * correctly for change detection. Java records provide these automatically.
 */
@SuppressWarnings("null")
public record UpgradeLevelComponent(int level) {

    // ─────────────────────────────────────────────────────────
    //  CONSTANTS
    // ─────────────────────────────────────────────────────────

    /** Maximum upgrade level. References centralized config. */
    public static final int MAX_LEVEL = com.myname.packapunch.UpgradeConfig.MAX_LEVEL;

    // ─────────────────────────────────────────────────────────
    //  CODECS — how this component is read/written
    // ─────────────────────────────────────────────────────────

    /**
     * CODEC — Used for DISK PERSISTENCE (NBT / JSON in world save files).
     *
     * Codec.INT wraps a raw integer. We use xmap to convert:
     *   int → UpgradeLevelComponent  (when reading from disk)
     *   UpgradeLevelComponent → int  (when writing to disk)
     *
     * In the world save, this produces a tag like:
     *   "packapunch:upgrade_level": 2
     *
     * Alternative: RecordCodecBuilder for multi-field records.
     * For a single-field record, Codec.INT + xmap is idiomatic.
     */
    public static final Codec<UpgradeLevelComponent> CODEC =
            Codec.intRange(0, MAX_LEVEL)
                 .xmap(UpgradeLevelComponent::new, UpgradeLevelComponent::level);

    /**
     * STREAM_CODEC — Used for NETWORK SYNCHRONIZATION (server ↔ client).
     *
     * When the server sends an ItemStack to the client (e.g., slot sync),
     * NeoForge serializes all attached components using their StreamCodecs.
     * The client deserializes them using the same codec.
     *
     * ByteBuf is the raw network buffer — more compact than NBT.
     * ByteBufCodecs.VAR_INT writes a variable-length integer (1–5 bytes),
     * which is more efficient than a fixed 4-byte INT for small numbers.
     *
     * The client therefore always sees the authoritative server value —
     * it NEVER needs to compute upgrade level locally.
     */
    public static final StreamCodec<ByteBuf, UpgradeLevelComponent> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(UpgradeLevelComponent::new, UpgradeLevelComponent::level);

    // ─────────────────────────────────────────────────────────
    //  COMPACT CONSTRUCTOR — validation
    // ─────────────────────────────────────────────────────────

    /**
     * Compact constructor (Java record feature) — runs before field assignment.
     * We validate the level range here so it's impossible to create an
     * UpgradeLevelComponent with an illegal value.
     *
     * This protects against:
     *   - Bugs where tryUpgrade() miscalculates the new level
     *   - Malicious data in save files (CODEC uses intRange(0, MAX_LEVEL)
     *     so this is a belt-and-suspenders check)
     */
    public UpgradeLevelComponent(int level) {
        this.level = Math.clamp(level, 0, MAX_LEVEL);
    }
}
