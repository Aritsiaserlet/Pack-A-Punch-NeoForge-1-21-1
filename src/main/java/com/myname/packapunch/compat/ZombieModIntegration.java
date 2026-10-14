package com.myname.packapunch.compat;

import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;
import java.util.UUID;

public class ZombieModIntegration {
    private static Boolean isLoaded = null;
    private static Method getPointsMethod = null;
    private static Method removePointsMethod = null;
    private static Method getClientPointsMethod = null;
    private static Method syncPacketMethod = null;

    public static boolean isModLoaded() {
        if (isLoaded == null) {
            try {
                Class.forName("com.zombiemod.manager.PointsManager");
                isLoaded = true;
            } catch (Throwable t) {
                isLoaded = false;
            }
        }
        return isLoaded;
    }

    public static int getPoints(Player player) {
        if (player == null || !isModLoaded()) return 0;
        try {
            if (player.level().isClientSide()) {
                if (getClientPointsMethod == null) {
                    Class<?> clientClass = Class.forName("com.zombiemod.client.ClientGameData");
                    getClientPointsMethod = clientClass.getMethod("getLocalPlayerPoints");
                }
                Object result = getClientPointsMethod.invoke(null);
                return result instanceof Integer ? (Integer) result : 0;
            } else {
                if (getPointsMethod == null) {
                    Class<?> pmClass = Class.forName("com.zombiemod.manager.PointsManager");
                    getPointsMethod = pmClass.getMethod("getPoints", UUID.class);
                }
                Object result = getPointsMethod.invoke(null, player.getUUID());
                return result instanceof Integer ? (Integer) result : 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean deductPoints(Player player, int cost) {
        if (player == null || !isModLoaded()) return false;
        try {
            if (!player.level().isClientSide()) {
                if (removePointsMethod == null) {
                    Class<?> pmClass = Class.forName("com.zombiemod.manager.PointsManager");
                    removePointsMethod = pmClass.getMethod("removePoints", UUID.class, int.class);
                }
                Object result = removePointsMethod.invoke(null, player.getUUID(), cost);
                boolean success = result instanceof Boolean && (Boolean) result;
                if (success && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    try {
                        if (syncPacketMethod == null) {
                            Class<?> gmClass = Class.forName("com.zombiemod.manager.GameManager");
                            syncPacketMethod = gmClass.getMethod("sendSyncPacketToAll", net.minecraft.server.level.ServerLevel.class);
                        }
                        syncPacketMethod.invoke(null, serverLevel);
                    } catch (Throwable ignored) {}
                }
                return success;
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }
}
