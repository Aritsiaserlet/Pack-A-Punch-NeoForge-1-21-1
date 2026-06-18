package com.myname.packapunch.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.fml.ModList;
import java.util.List;

public class TaczIntegration {
    
    /**
     * Safely attempts to extract the base damage of a TaCZ gun using Reflection
     * to avoid a hard compile-time dependency. If successful, appends the multiplied
     * damage to the tooltip elements.
     */
    public static void tryAddTaczDamageTooltip(ItemStack stack, float multiplier, List<com.mojang.datafixers.util.Either<net.minecraft.network.chat.FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent>> elements) {
        if (!ModList.get().isLoaded("tacz")) return;

        try {
            // 1. Get IGun instance: IGun iGunObj = IGun.getIGunOrNull(stack);
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            java.lang.reflect.Method getIGunOrNull = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            Object iGunObj = getIGunOrNull.invoke(null, stack);
            if (iGunObj == null) return;

            // 2. Get Gun ID: ResourceLocation gunId = iGunObj.getGunId(stack);
            java.lang.reflect.Method getGunId = iGunClass.getMethod("getGunId", ItemStack.class);
            net.minecraft.resources.ResourceLocation gunId = (net.minecraft.resources.ResourceLocation) getGunId.invoke(iGunObj, stack);
            if (gunId == null) return;

            // 3. Get Gun Index: Optional<?> indexOpt = TimelessAPI.getCommonGunIndex(gunId);
            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            java.lang.reflect.Method getCommonGunIndex = timelessApiClass.getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class);
            java.util.Optional<?> indexOpt = (java.util.Optional<?>) getCommonGunIndex.invoke(null, gunId);
            if (!indexOpt.isPresent()) return;
            Object gunIndex = indexOpt.get();

            // 4. Get Gun Data: Object gunData = gunIndex.getGunData();
            java.lang.reflect.Method getGunData = gunIndex.getClass().getMethod("getGunData");
            Object gunData = getGunData.invoke(gunIndex);

            // 5. Search for damage deeply inside GunData and its sub-objects (e.g., BulletData)
            float baseDamage = findDamageDeep(gunData, 0);

            // 6. If we successfully found the base damage, append our custom line
            if (baseDamage > 0) {
                float packDamage = baseDamage * multiplier;
                String formattedDamage = String.format(java.util.Locale.US, packDamage % 1.0 == 0 ? "%.0f" : "%.1f", packDamage);
                Component newComp = Component.literal("Damage: " + formattedDamage)
                    .withStyle(ChatFormatting.GREEN);
                
                // Find the Damage Bonus line and insert right below it
                int insertIndex = elements.size();
                for (int i = 0; i < elements.size(); i++) {
                    var element = elements.get(i);
                    if (element.left().isPresent()) {
                        String rawText = element.left().get().getString();
                        String stripped = ChatFormatting.stripFormatting(rawText);
                        if (stripped != null && (stripped.contains("Damage Bonus") || stripped.contains("โบนัสดาเมจ"))) {
                            insertIndex = i + 1;
                            break;
                        }
                    }
                }
                elements.add(insertIndex, com.mojang.datafixers.util.Either.left(newComp));
            } else {
                Component debugComp = Component.literal("Pack-A-Punch TaCZ Error: Damage field not found! Methods:")
                    .withStyle(ChatFormatting.RED);
                elements.add(com.mojang.datafixers.util.Either.left(debugComp));
                
                // Dump all method names to tooltip so we can see what's available
                StringBuilder msgs = new StringBuilder();
                for (java.lang.reflect.Method m : gunData.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                        msgs.append(m.getName()).append(", ");
                    }
                }
                elements.add(com.mojang.datafixers.util.Either.left(
                    Component.literal(msgs.toString()).withStyle(ChatFormatting.GRAY)
                ));
            }
        } catch (Throwable t) {
            // Soft dependency: if TaCZ API changes or classes are missing, show on tooltip for debugging.
            Component errorComp = Component.literal("Pack-A-Punch TaCZ Error: " + t.toString())
                .withStyle(ChatFormatting.RED);
            elements.add(com.mojang.datafixers.util.Either.left(errorComp));
        }
    }

    private static float findDamageDeep(Object obj, int depth) {
        if (depth > 2 || obj == null) return -1;
        
        // Priority 1: Exact matches for common base damage getters
        for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String name = m.getName().toLowerCase(java.util.Locale.US);
                if (name.equals("getdamage") || name.equals("damage") || 
                    name.equals("getbasedamage") || name.equals("basedamage") ||
                    name.equals("getbulletdamage") || name.equals("getgunbasedamage") ||
                    name.equals("getdamageamount") || name.equals("damageamount")) {
                    try {
                        Object result = m.invoke(obj);
                        if (result instanceof Number) {
                            return ((Number)result).floatValue();
                        }
                    } catch (Exception e) {}
                }
            }
        }
        
        // Priority 2: Check sub-objects (like BulletData) for exact matches
        for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                if (m.getReturnType().getName().contains("tacz")) {
                    try {
                        Object result = m.invoke(obj);
                        float dmg = findDamageDeep(result, depth + 1);
                        if (dmg > 0) return dmg;
                    } catch (Exception e) {}
                }
            }
        }
        
        return -1;
    }
}
