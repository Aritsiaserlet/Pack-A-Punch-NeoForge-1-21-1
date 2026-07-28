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
    //  CODECS — how this component is read/written
    // ─────────────────────────────────────────────────────────

    public static final Codec<UpgradeLevelComponent> CODEC =
            Codec.intRange(0, 100)
                 .xmap(UpgradeLevelComponent::new, UpgradeLevelComponent::level);

    public static final StreamCodec<ByteBuf, UpgradeLevelComponent> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(UpgradeLevelComponent::new, UpgradeLevelComponent::level);

    // ─────────────────────────────────────────────────────────
    //  COMPACT CONSTRUCTOR — validation
    // ─────────────────────────────────────────────────────────

    public UpgradeLevelComponent(int level) {
        this.level = Math.clamp(level, 0, 100);
    }
}
