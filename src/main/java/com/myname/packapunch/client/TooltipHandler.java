package com.myname.packapunch.client;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.component.UpgradeLevelComponent;
import com.myname.packapunch.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║         TOOLTIPHANDLER — CLIENT-ONLY TOOLTIP RENDERER   ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * Listens to ItemTooltipEvent and appends upgrade level information
 * to ANY item that carries the UpgradeLevelComponent.
 *
 * HOW ITEM TOOLTIPS WORK IN NeoForge 1.21.1:
 * ──────────────────────────────────────────────────────────
 * ItemTooltipEvent fires on the CLIENT's GAME event bus every time
 * Minecraft renders a tooltip. The event provides:
 *   - event.getItemStack() → the item being hovered
 *   - event.getToolTip()   → a mutable List<Component> (the tooltip lines)
 *   - event.getContext()   → hover context (NORMAL, ADVANCED, etc.)
 *
 * We ADD entries to the tooltip list. We do NOT clear the existing lines.
 *   - Index 0 is always the item name (set by vanilla).
 *   - We insert at index 1 to place our text directly below the item name,
 *     above vanilla lore/durability lines.
 *   - We add index 2 for the "next upgrade cost" hint.
 *
 * WHY CLIENT-ONLY?
 * ──────────────────────────────────────────────────────────
 * Tooltips are a RENDERING concern — the server never shows tooltips.
 * Loading this class on the server would crash (no GuiGraphics, no Font).
 * Dist.CLIENT in @EventBusSubscriber ensures this class is never loaded
 * on a dedicated server.
 *
 * HOW DOES THE CLIENT KNOW THE UPGRADE LEVEL?
 * ──────────────────────────────────────────────────────────
 * The UpgradeLevelComponent travels WITH the ItemStack via the network.
 * When the server sends slot sync packets, each ItemStack's components
 * are serialized using STREAM_CODEC and sent to the client.
 * The client deserializes them — so the component is always present and
 * accurate without any manual networking on our part.
 *
 * This is the power of Data Components: persistence and network sync
 * are handled automatically by the framework.
 *
 * BUS: GAME (default) — ItemTooltipEvent is a game-world event,
 * not a mod-loading event. Do NOT use Bus.MOD here.
 */
@SuppressWarnings("null")
@EventBusSubscriber(modid = PackAPunchMod.MOD_ID, value = Dist.CLIENT)
public class TooltipHandler {

    /**
     * Called every time the client renders a tooltip for any item.
     *
     * We check for our Data Component on the item. If present and level > 0,
     * we append visual upgrade information to the tooltip.
     *
     * Star notation:
     *   Level 1 → ★☆☆   (1 filled, 2 empty)
     *   Level 2 → ★★☆   (2 filled, 1 empty)
     *   Level 3 → ★★★   (3filled, MAX)
     *
     * @param event The tooltip event containing item and mutable tooltip list.
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // stack.get() returns null if the component is not present on this item.
        // This is safe — we simply skip items that haven't been upgraded.
        UpgradeLevelComponent comp = stack.get(ModDataComponents.UPGRADE_LEVEL.get());
        if (comp == null || comp.level() <= 0) {
            return; // Item has no upgrade — don't modify its tooltip
        }

        int level = comp.level();
        int maxLevel = UpgradeLevelComponent.MAX_LEVEL;

        // ── Build star string ─────────────────────────────────────────────
        // "★".repeat(n) produces n filled stars
        // "☆".repeat(n) produces n empty stars
        String stars = "★".repeat(level) + "☆".repeat(maxLevel - level);

        // ── Line 1: Stars ───────────────────────────────────────────
        event.getToolTip().add(1,
                Component.literal(stars)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        float multiplier = com.myname.packapunch.UpgradeConfig.getMultiplierForLevel(level);
        event.getToolTip().add(2, Component.literal("Damage Bonus: x" + multiplier).withStyle(ChatFormatting.YELLOW));

        int insertIndex = 3;

        // ── Calculate dynamic final damage for Vanilla weapons ───────────
        double playerBaseDamage = 1.0; // Minecraft player's default unarmed damage
        double addValue = 0.0;
        double addMultipliedBase = 0.0;
        double addMultipliedTotal = 0.0;
        boolean hasAttackDamageModifier = false;

        net.minecraft.world.item.component.ItemAttributeModifiers modifiers = stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                    // Only care about modifiers applied when held in the main hand
                    if (entry.slot().test(net.minecraft.world.entity.EquipmentSlot.MAINHAND)) {
                        hasAttackDamageModifier = true;
                        var op = entry.modifier().operation();
                        if (op == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                            addValue += entry.modifier().amount();
                        } else if (op == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                            addMultipliedBase += entry.modifier().amount();
                        } else if (op == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                            addMultipliedTotal += entry.modifier().amount();
                        }
                    }
                }
            }
        }

        if (hasAttackDamageModifier) {
            // Reconstruct the true displayed damage (what Vanilla tooltip calculates)
            double totalBaseDamage = (playerBaseDamage + addValue) * (1.0 + addMultipliedBase) * (1.0 + addMultipliedTotal);
            
            // Apply our runtime multiplier
            double finalDamage = totalBaseDamage * multiplier;
            
            // Format to 1 decimal place
            String formattedDamage = String.format("%.1f", finalDamage);
            event.getToolTip().add(insertIndex++, Component.literal("Current Damage: " + formattedDamage).withStyle(ChatFormatting.GREEN));
        }

        // Add blank line
        event.getToolTip().add(insertIndex++, Component.empty());
        event.getToolTip().add(insertIndex++, Component.literal("Next Upgrade:").withStyle(ChatFormatting.GRAY));

        // ── Next upgrade cost hint ───────────────────────────────────────
        if (com.myname.packapunch.UpgradeConfig.isMaxLevel(level)) {
            event.getToolTip().add(insertIndex,
                    Component.literal("MAX LEVEL")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        } else {
            int nextCost = com.myname.packapunch.UpgradeConfig.getCostForLevel(level + 1);
            net.minecraft.world.item.Item reqItem = com.myname.packapunch.UpgradeConfig.getItemForLevel(level + 1);
            String reqItemName = reqItem.getDescription().getString();
            
            event.getToolTip().add(insertIndex,
                    Component.literal(nextCost + " " + reqItemName)
                            .withStyle(ChatFormatting.AQUA));
        }
    }
}
