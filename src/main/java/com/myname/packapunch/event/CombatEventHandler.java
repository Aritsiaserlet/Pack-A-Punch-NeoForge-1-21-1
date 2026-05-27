package com.myname.packapunch.event;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.component.UpgradeLevelComponent;
import com.myname.packapunch.registry.ModDataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║        COMBATEVENTHANDLER — DYNAMIC DAMAGE SCALING       ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This class listens to combat events and dynamically scales damage
 * based on the weapon's Pack-a-Punch upgrade level.
 *
 * WHY AVOID MODIFYING ATTRIBUTES DIRECTLY?
 * ──────────────────────────────────────────────────────────
 * Instead of rewriting NBT tags, Attributes, or TaCZ internal data:
 * 1. Compatibility: Works seamlessly with vanilla swords, axes, and
 *    any modded weapon (including TaCZ guns).
 * 2. Safety: No permanent changes are made to the item's base stats,
 *    preventing bugs where damage stacks infinitely.
 * 3. Simplicity: The UpgradeLevelComponent remains the single source
 *    of truth.
 *
 * NeoForge 1.21.1 EVENT PIPELINE:
 * ──────────────────────────────────────────────────────────
 * LivingIncomingDamageEvent is fired BEFORE armor and potion effects
 * reduce the damage. This is the correct place to apply raw weapon
 * multipliers so that the target's armor still functions normally.
 */
@SuppressWarnings({"null", "removal"})
@EventBusSubscriber(modid = PackAPunchMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CombatEventHandler {

    /**
     * Intercepts incoming damage and multiplies it if the attacker
     * is wielding a Pack-a-Punch upgraded weapon.
     */
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        // The entity that dealt the damage (could be null if environmental)
        Entity sourceEntity = event.getSource().getEntity();

        // We only care if the damage was dealt by a LivingEntity (e.g., Player)
        // using their main hand.
        if (sourceEntity instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();

            // Read the upgrade level from the weapon's Data Component
            UpgradeLevelComponent comp = weapon.get(ModDataComponents.UPGRADE_LEVEL.get());
            if (comp != null && comp.level() > 0) {
                float multiplier = getDamageMultiplier(comp.level());
                
                // Apply the multiplier to the raw incoming damage amount
                float originalDamage = event.getAmount();
                event.setAmount(originalDamage * multiplier);
            }
        }
    }

    /**
     * Maps an upgrade level to a damage multiplier.
     * Level 0 = x1.0 (handled implicitly by skipping the multiplier logic)
     * Level 1 = x1.25
     * Level 2 = x1.50
     * Level 3 = x2.00
     */
    public static float getDamageMultiplier(int level) {
        int clampedLevel = Math.clamp(level, 0, com.myname.packapunch.UpgradeConfig.MAX_LEVEL);
        return com.myname.packapunch.UpgradeConfig.getMultiplierForLevel(clampedLevel);
    }
}
