package com.myname.packapunch.network;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.UpgradeConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ClientboundSyncConfigPacket(List<String> upgrades) implements CustomPacketPayload {
    public static final Type<ClientboundSyncConfigPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PackAPunchMod.MOD_ID, "sync_config"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncConfigPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(java.util.ArrayList::new, ByteBufCodecs.STRING_UTF8),
            ClientboundSyncConfigPacket::upgrades,
            ClientboundSyncConfigPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundSyncConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // This runs on the client main thread
            UpgradeConfig.syncFromServer(packet.upgrades());
        });
    }
}
