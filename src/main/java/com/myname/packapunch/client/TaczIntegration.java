package com.myname.packapunch.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.fml.ModList;
import java.util.List;

public class TaczIntegration {
    
    public static void tryAddTaczDamageTooltip(ItemStack stack, float multiplier, List<com.mojang.datafixers.util.Either<net.minecraft.network.chat.FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent>> elements) {
        // เช็คว่าโหลดมอด tacz มาหรือไม่ ถ้าไม่มีให้หยุดการทำงานทันที
        if (!ModList.get().isLoaded("tacz")) return;

        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            java.lang.reflect.Method getIGunOrNull = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            Object iGunObj = getIGunOrNull.invoke(null, stack);
            if (iGunObj == null) return;

            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            java.lang.reflect.Method getGunId = iGunClass.getMethod("getGunId", ItemStack.class);
            net.minecraft.resources.ResourceLocation gunId = (net.minecraft.resources.ResourceLocation) getGunId.invoke(iGunObj, stack);
            if (gunId == null) return;

            java.lang.reflect.Method getCommonGunIndex = timelessApiClass.getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class);
            java.util.Optional<?> indexOpt = (java.util.Optional<?>) getCommonGunIndex.invoke(null, gunId);
            if (!indexOpt.isPresent()) return;
            Object gunIndex = indexOpt.get();

            java.lang.reflect.Method getGunData = gunIndex.getClass().getMethod("getGunData");
            Object gunData = getGunData.invoke(gunIndex);

            float baseDamage = -1;
            float explosionDamage = -1;

            int pelletCount = -1;

            // พยายามหาฟิลด์ดาเมจจาก GunData
            if (gunData != null) {
                if (baseDamage <= 0) baseDamage = findSpecificDamageDeep(gunData, 0, false);
                
                Boolean isExplosive = isExplosiveDeep(gunData, 0);
                boolean canExplode = false;
                
                if (isExplosive != null) {
                    canExplode = isExplosive;
                } else {
                    canExplode = findExplosionRadiusDeep(gunData, 0) > 0;
                }
                
                if (canExplode) {
                    if (explosionDamage <= 0) explosionDamage = findSpecificDamageDeep(gunData, 0, true);
                } else {
                    explosionDamage = -1; // No explosion
                }
                
                pelletCount = findPelletCountDeep(gunData, 0);
            }

            // ถ้าเจอดาเมจ ให้แทรกข้อมูลลงใน Tooltip
            if (baseDamage > 0 || explosionDamage > 0) {
                String text = "Damage: ";
                if (baseDamage > 0) {
                    if (pelletCount > 1) {
                        baseDamage = baseDamage / pelletCount;
                    }
                    float packDamage = baseDamage * multiplier;
                    text += String.format(java.util.Locale.US, packDamage % 1.0 == 0 ? "%.0f" : "%.1f", packDamage);
                    if (pelletCount > 1) {
                        text += " × " + pelletCount;
                    }
                } else {
                    text += "0";
                }
                
                if (explosionDamage > 0) {
                    float packExp = explosionDamage * multiplier;
                    text += " + " + String.format(java.util.Locale.US, packExp % 1.0 == 0 ? "%.0f" : "%.1f", packExp) + " (Explosion)";
                }

                Component newComp = Component.literal(text).withStyle(ChatFormatting.GREEN);
                
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
            }
        } catch (Throwable t) {
            // ถ้า Error ปล่อยผ่านเงียบๆ ไม่ให้เกมพัง
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
                    if (name.contains("explosion") && name.contains("damage") 
                        && !name.contains("multiplier") && !name.contains("reduction") 
                        && !name.contains("radius") && !name.contains("knockback")) {
                        match = true;
                    } else if (clazz.getName().toLowerCase(java.util.Locale.US).contains("explosion") 
                        && name.contains("damage") && !name.contains("armor") 
                        && !name.contains("multiplier") && !name.contains("reduction")) {
                        match = true;
                    }
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
        
        if (maxVal >= 0) return maxVal;
        
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String rType = m.getReturnType().getName().toLowerCase(java.util.Locale.US);
                
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

    private static int findPelletCountDeep(Object obj, int depth) {
        if (depth > 5 || obj == null) return -1;
        if (obj instanceof java.util.Optional) {
            java.util.Optional<?> opt = (java.util.Optional<?>) obj;
            if (!opt.isPresent()) return -1;
            obj = opt.get();
        }
        
        int maxVal = -1;
        Class<?> clazz = obj.getClass();
        
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String name = m.getName().toLowerCase(java.util.Locale.US);
                
                if ((name.contains("pellet") || name.contains("bullet") || name.contains("tracer") || name.contains("projectile")) 
                    && (name.contains("count") || name.contains("amount") || name.contains("num"))) {
                    try {
                        Object result = m.invoke(obj);
                        if (result instanceof Number) {
                            int val = ((Number)result).intValue();
                            if (val > maxVal) maxVal = val;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        
        if (maxVal > 1) return maxVal;
        
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String rType = m.getReturnType().getName().toLowerCase(java.util.Locale.US);
                
                if ((rType.contains("tacz") || rType.contains("optional")) && !m.getReturnType().isEnum()) {
                    try {
                        Object result = m.invoke(obj);
                        int count = findPelletCountDeep(result, depth + 1);
                        if (count > maxVal) maxVal = count;
                    } catch (Exception e) {}
                }
            }
        }
        return maxVal;
    }

    private static float findExplosionRadiusDeep(Object obj, int depth) {
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
                
                if (name.contains("radius") && (name.contains("explosion") || clazz.getName().toLowerCase(java.util.Locale.US).contains("explosion"))) {
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
        
        if (maxVal >= 0) return maxVal;
        
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String rType = m.getReturnType().getName().toLowerCase(java.util.Locale.US);
                if ((rType.contains("tacz") || rType.contains("optional")) && !m.getReturnType().isEnum()) {
                    try {
                        Object result = m.invoke(obj);
                        float radius = findExplosionRadiusDeep(result, depth + 1);
                        if (radius > maxVal) maxVal = radius;
                    } catch (Exception e) {}
                }
            }
        }
        return maxVal;
    }

    private static Boolean isExplosiveDeep(Object obj, int depth) {
        if (depth > 5 || obj == null) return null;
        if (obj instanceof java.util.Optional) {
            java.util.Optional<?> opt = (java.util.Optional<?>) obj;
            if (!opt.isPresent()) return false;
            obj = opt.get();
        }
        
        Class<?> clazz = obj.getClass();
        Boolean foundExplicitFalse = false;
        
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String name = m.getName().toLowerCase(java.util.Locale.US);
                if (name.equals("isexplode") || name.equals("hasexplosion")) {
                    try {
                        Object result = m.invoke(obj);
                        if (result instanceof Boolean) {
                            if ((Boolean) result) {
                                return true;
                            } else {
                                foundExplicitFalse = true;
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }
        
        if (foundExplicitFalse) return false;
        
        Boolean anyTrue = null;
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                String rType = m.getReturnType().getName().toLowerCase(java.util.Locale.US);
                if ((rType.contains("tacz") || rType.contains("optional")) && !m.getReturnType().isEnum()) {
                    try {
                        Object result = m.invoke(obj);
                        Boolean childExplosive = isExplosiveDeep(result, depth + 1);
                        if (childExplosive != null) {
                            if (childExplosive) return true;
                            anyTrue = false;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        return anyTrue;
    }
}
