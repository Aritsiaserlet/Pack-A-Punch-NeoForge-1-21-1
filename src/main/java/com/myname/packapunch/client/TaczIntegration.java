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

            // 5. Search for Base Damage and Explosion Damage
            float baseDamage = -1;
            float explosionDamage = -1;

            // วิธีที่ 1: ลองหา Method ที่รับ ItemStack และคืนค่า Damage ใน IGun
            for (java.lang.reflect.Method m : iGunClass.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == ItemStack.class) {
                    String name = m.getName().toLowerCase(java.util.Locale.US);
                    if (name.contains("damage")) {
                        try {
                            Object result = m.invoke(iGunObj, stack);
                            if (result instanceof Number) {
                                float val = ((Number)result).floatValue();
                                if (name.contains("explosion")) {
                                    if (val > explosionDamage) explosionDamage = val;
                                } else if (!name.contains("armor") && !name.contains("headshot")) {
                                    if (val > baseDamage) baseDamage = val;
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            }

            // วิธีที่ 2: ลองหาใน TimelessAPI แบบ Static
            if (baseDamage <= 0 || explosionDamage <= 0) {
                for (java.lang.reflect.Method m : timelessApiClass.getMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == ItemStack.class) {
                        String name = m.getName().toLowerCase(java.util.Locale.US);
                        if (name.contains("damage")) {
                            try {
                                Object result = m.invoke(null, stack);
                                if (result instanceof Number) {
                                    float val = ((Number)result).floatValue();
                                    if (name.contains("explosion") && val > explosionDamage) {
                                        explosionDamage = val;
                                    } else if (!name.contains("armor") && !name.contains("headshot") && val > baseDamage) {
                                        baseDamage = val;
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }
                }
            }

            // วิธีที่ 3: ดึงข้อมูลจาก GunData ตรงๆ
            if (gunData != null) {
                if (baseDamage <= 0) baseDamage = findSpecificDamageDeep(gunData, 0, false);
                if (explosionDamage <= 0) explosionDamage = findSpecificDamageDeep(gunData, 0, true);
            }

            // 6. แทรกข้อความ Pack-A-Punch
            if (baseDamage > 0 || explosionDamage > 0) {
                String text = "Damage: ";
                if (baseDamage > 0) {
                    float packDamage = baseDamage * multiplier;
                    text += String.format(java.util.Locale.US, packDamage % 1.0 == 0 ? "%.0f" : "%.1f", packDamage);
                } else {
                    text += "0";
                }
                
                if (explosionDamage > 0) {
                    float packExp = explosionDamage * multiplier;
                    text += " + " + String.format(java.util.Locale.US, packExp % 1.0 == 0 ? "%.0f" : "%.1f", packExp) + " (Explosion)";
                }

                Component newComp = Component.literal(text).withStyle(ChatFormatting.GREEN);
                
                // หาบรรทัด Damage Bonus เพื่อแทรกต่อท้าย
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
                Component debugComp = Component.literal("Pack-A-Punch TaCZ Error: Damage field not found!").withStyle(ChatFormatting.RED);
                elements.add(com.mojang.datafixers.util.Either.left(debugComp));
            }
        } catch (Throwable t) {
            // ปล่อยผ่านเงียบๆ
        }
    }

    private static float findSpecificDamageDeep(Object obj, int depth, boolean lookForExplosion) {
        if (depth > 5 || obj == null) return -1;
        if (obj instanceof java.util.Optional) {
            java.util.Optional<?> opt = (java.util.Optional<?>) obj;
            if (!opt.isPresent()) return -1;
            obj = opt.get();
        }
        
        float maxVal = -1;
        Class<?> clazz = obj.getClass();
        
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String name = m.getName().toLowerCase(java.util.Locale.US);
                boolean match = false;
                
                if (lookForExplosion) {
                    if (name.contains("explosion") && name.contains("damage")) match = true;
                    else if (clazz.getName().toLowerCase(java.util.Locale.US).contains("explosion") && name.contains("damage") && !name.contains("armor")) match = true;
                } else {
                    if (name.contains("damage") && !name.contains("armor") && !name.contains("headshot") 
                        && !name.contains("multiplier") && !name.contains("reduction") 
                        && !name.contains("explosion") && !name.contains("knockback")
                        && !name.contains("fire") && !name.contains("ignite") && !name.contains("distance")
                        && !name.contains("rate") && !name.contains("speed")) {
                        match = true;
                    }
                }

                if (match) {
                    try {
                        Object result = m.invoke(obj);
                        if (result instanceof Number) {
                            float val = ((Number)result).floatValue();
                            if (val > maxVal) maxVal = val;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        
        if (maxVal > 0) return maxVal;
        
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String rType = m.getReturnType().getName().toLowerCase(java.util.Locale.US);
                
                // Recurse into any TaCZ class, or Optional
                if ((rType.contains("tacz") || rType.contains("optional")) && !m.getReturnType().isEnum()) {
                    try {
                        Object result = m.invoke(obj);
                        float dmg = findSpecificDamageDeep(result, depth + 1, lookForExplosion);
                        if (dmg > maxVal) maxVal = dmg;
                    } catch (Exception e) {}
                }
            }
        }
        return maxVal;
    }
}
