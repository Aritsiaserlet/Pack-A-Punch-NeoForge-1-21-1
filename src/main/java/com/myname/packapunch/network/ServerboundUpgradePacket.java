package com.myname.packapunch.network;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.blockentity.PackAPunchBlockEntity;
import com.myname.packapunch.menu.PackAPunchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║      SERVERBOUNDUPGRADEPACKET — CLIENT→SERVER PACKET    ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This packet is sent from the CLIENT to the SERVER when the player
 * clicks the "Upgrade" button in the GUI.
 *
 * ═══════════════════════════════════════════════════════════
 * LESSON: HOW CustomPacketPayload WORKS (NeoForge 1.21.1)
 * ═══════════════════════════════════════════════════════════
 *
 * In NeoForge 1.21.1, custom networking is built around CustomPacketPayload.
 * This is the MODERN replacement for the old SimpleChannel / PacketByteBuf API.
 *
 * The flow for a serverbound packet:
 *
 *   CLIENT SIDE:
 *   ────────────────────────────────────────────────────────
 *   1. Player clicks the Upgrade button in PackAPunchScreen
 *   2. Screen calls:
 *        PacketDistributor.sendToServer(new ServerboundUpgradePacket(pos))
 *   3. NeoForge looks up our registered packet TYPE in its payload registry
 *   4. NeoForge calls STREAM_CODEC.encode(buf, packet) to serialize the packet
 *      into a FriendlyByteBuf (the raw network bytes)
 *   5. The bytes are wrapped in a vanilla CustomPayloadPacket and sent over
 *      the player's network connection
 *
 *   SERVER SIDE:
 *   ────────────────────────────────────────────────────────
 *   6. Server receives the raw CustomPayloadPacket
 *   7. NeoForge looks up the TYPE from the packet's resource location ID
 *   8. NeoForge calls STREAM_CODEC.decode(buf) to deserialize → our record
 *   9. NeoForge calls our handle() method on the NETTY thread
 *  10. We call ctx.enqueueWork() to move execution to the GAME (server) thread
 *  11. Our handle() logic runs safely on the server thread
 *
 * WHY enqueueWork()?
 * ──────────────────────────────────────────────────────────
 * Minecraft is NOT thread-safe. The network layer runs on a dedicated Netty
 * thread. Accessing game objects (BlockEntity, Player, Level) from the
 * Netty thread causes race conditions and crashes.
 *
 * ctx.enqueueWork(Runnable) queues our logic to run on the main SERVER GAME
 * THREAD on the next tick. This is the REQUIRED pattern for all packet handlers.
 *
 * WHY THE CLIENT NEVER CONTROLS UPGRADE STATE:
 * ──────────────────────────────────────────────────────────
 * The packet only contains a BlockPos — WHERE the upgrade should happen.
 * The server decides WHETHER the upgrade should happen (diamonds, level cap).
 *
 * A malicious client could send ANY BlockPos in the packet, or spam it
 * repeatedly. Our handler defends against this with three checks:
 *
 *   1. Is the player's open menu a PackAPunchMenu? (They must have the GUI open)
 *   2. Does the menu's stored BlockPos match the packet's pos? (Same machine)
 *   3. Is the BlockEntity at that pos a PackAPunchBlockEntity? (Sanity check)
 *
 * Even if all 3 pass, tryUpgrade() on the BlockEntity re-validates diamonds,
 * level cap, and item presence. NOTHING is trusted from the client.
 *
 * WHY A RECORD?
 * ──────────────────────────────────────────────────────────
 * Packets are VALUE OBJECTS: they carry data and have no mutable state.
 * Java records are a perfect fit: immutable, compact, and auto-generate
 * equals()/hashCode()/toString() for free.
 */
@SuppressWarnings("null")
public record ServerboundUpgradePacket(BlockPos pos) implements CustomPacketPayload {

    // ─────────────────────────────────────────────────────────
    //  PACKET TYPE — the "name tag" of this packet
    // ─────────────────────────────────────────────────────────

    /**
     * CustomPacketPayload.Type is a simple wrapper around a ResourceLocation.
     * It uniquely identifies our packet in NeoForge's payload registry.
     *
     * The ResourceLocation "packapunch:upgrade_request" must be:
     *   - Unique across ALL mods (namespace ensures this)
     *   - Registered in PackAPunchMod via RegisterPayloadsEvent
     *   - Consistent between client and server (both use this class)
     *
     * NeoForge uses this ID when:
     *   - Sending: wraps the encoded bytes with this ID
     *   - Receiving: looks up which codec/handler to use by this ID
     */
    public static final CustomPacketPayload.Type<ServerboundUpgradePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(PackAPunchMod.MOD_ID, "upgrade_request")
            );

    // ─────────────────────────────────────────────────────────
    //  STREAM CODEC — how to encode/decode over the network
    // ─────────────────────────────────────────────────────────

    /**
     * StreamCodec — the network serializer/deserializer for this packet.
     *
     * HOW StreamCodec.composite() WORKS:
     * ────────────────────────────────────────────────────────
     * StreamCodec.composite() is a factory for records/classes with multiple fields.
     * It takes (fieldCodec, fieldGetter) pairs plus a constructor reference.
     *
     * For encoding (packet → bytes):
     *   composite calls fieldGetter on the packet → writes the value via fieldCodec
     *
     * For decoding (bytes → packet):
     *   composite reads each fieldCodec in order → calls constructor with results
     *
     * In our case:
     *   BlockPos.STREAM_CODEC encodes 3 longs (x, y, z) packed into 8 bytes via zigzag
     *   ServerboundUpgradePacket::pos is the getter for our BlockPos field
     *   ServerboundUpgradePacket::new is the record's constructor (BlockPos) → packet
     *
     * RegistryFriendlyByteBuf extends FriendlyByteBuf with registry lookup support.
     * Use it for any codec that might reference registry entries (items, blocks, etc.)
     * BlockPos doesn't need it, but it's the standard buffer type for play packets.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundUpgradePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,          // how to encode/decode BlockPos
                    ServerboundUpgradePacket::pos,  // getter: packet → BlockPos
                    ServerboundUpgradePacket::new   // constructor: BlockPos → packet
            );

    // ─────────────────────────────────────────────────────────
    //  CustomPacketPayload INTERFACE
    // ─────────────────────────────────────────────────────────

    /**
     * Returns this packet's TYPE. Required by the CustomPacketPayload interface.
     * NeoForge calls this to look up the codec and handler when sending.
     */
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ─────────────────────────────────────────────────────────
    //  SERVER-SIDE HANDLER
    // ─────────────────────────────────────────────────────────

    /**
     * Called on the SERVER when this packet arrives.
     *
     * IMPORTANT: This method is first called on the Netty network thread.
     * We immediately defer to the game thread via ctx.enqueueWork().
     * All game-object access MUST be inside enqueueWork().
     *
     * SECURITY VALIDATION CHAIN:
     * ────────────────────────────────────────────────────────
     * 1. ctx.player() → cast to ServerPlayer (always valid on server)
     * 2. Check player.containerMenu is our PackAPunchMenu → must have GUI open
     * 3. Check menu.getBlockPos().equals(packet.pos()) → must be this machine
     * 4. Check BlockEntity at pos is PackAPunchBlockEntity → sanity
     * 5. Call pap.tryUpgrade(player) → server validates diamonds, level, etc.
     *
     * This multi-layer validation prevents:
     *   - Bots sending packets without having the GUI open
     *   - Clients targeting a different machine than the one they opened
     *   - Race conditions where the block was destroyed mid-flight
     *
     * @param packet The decoded packet (just contains a BlockPos)
     * @param ctx    Context providing player, level, and enqueueWork()
     */
    public static void handle(ServerboundUpgradePacket packet, IPayloadContext ctx) {
        // Move to the server game thread — NEVER access game objects here directly
        ctx.enqueueWork(() -> {
            // Cast is safe: on the server, all players are ServerPlayers
            ServerPlayer player = (ServerPlayer) ctx.player();

            // ── GUARD 1: Player must have a PackAPunchMenu open ──────────
            if (!(player.containerMenu instanceof PackAPunchMenu menu)) {
                PackAPunchMod.LOGGER.warn(
                        "[PackAPunch] {} sent upgrade packet but has no PackAPunchMenu open!",
                        player.getName().getString());
                return;
            }

            // ── GUARD 2: The packet's pos must match the open menu's pos ─
            // This prevents a client from targeting a different machine
            // than the one whose GUI they actually opened.
            if (!menu.getBlockPos().equals(packet.pos())) {
                PackAPunchMod.LOGGER.warn(
                        "[PackAPunch] {} sent upgrade packet for wrong position! Expected {}, got {}",
                        player.getName().getString(), menu.getBlockPos(), packet.pos());
                return;
            }

            // ── GUARD 3: A PackAPunchBlockEntity must exist at that pos ──
            if (!(player.level().getBlockEntity(packet.pos()) instanceof PackAPunchBlockEntity pap)) {
                PackAPunchMod.LOGGER.warn(
                        "[PackAPunch] No PackAPunchBlockEntity found at {} for player {}",
                        packet.pos(), player.getName().getString());
                return;
            }

            // All guards passed — run the real upgrade logic on the server
            pap.tryUpgrade(player);
        });
    }
}
