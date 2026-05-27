package com.myname.packapunch.client;

import com.myname.packapunch.PackAPunchMod;
import com.myname.packapunch.registry.ModMenuTypes;
import com.myname.packapunch.screen.PackAPunchScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║          CLIENTSETUP — CLIENT-ONLY EVENT HANDLER        ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * This class handles all client-side mod initialization.
 *
 * @EventBusSubscriber ANNOTATION BREAKDOWN:
 * ──────────────────────────────────────────────────────────
 *
 *   modid = PackAPunchMod.MOD_ID
 *     → Tells NeoForge this listener belongs to our mod.
 *       NeoForge uses this to only fire our events for our mod.
 *
 *   bus = EventBusSubscriber.Bus.MOD
 *     → Subscribe to the MOD event bus (not the game/Forge event bus).
 *       RegisterMenuScreensEvent fires on the MOD bus during loading.
 *       If you use Bus.FORGE here, the event never arrives → screen never registers.
 *
 *   value = Dist.CLIENT
 *     → THE CRITICAL FLAG. This tells NeoForge's class loader:
 *       "Only load this class if we're running on a CLIENT."
 *       On a DEDICATED SERVER, this class is never loaded at all.
 *       Without this flag:
 *         - Server tries to load PackAPunchScreen
 *         - PackAPunchScreen imports GuiGraphics, Font, etc.
 *         - Those classes don't exist on the server
 *         - CRASH: ClassNotFoundException
 *
 * WHY NOT PUT THIS IN PackAPunchMod.java?
 * ──────────────────────────────────────────────────────────
 * PackAPunchMod.java runs on BOTH sides. If we registered the screen
 * there, the server would try to load PackAPunchScreen → crash.
 *
 * Using @EventBusSubscriber on a separate class is the clean solution:
 * NeoForge scans all annotated classes but ONLY loads ones that match
 * the current Dist (distribution: CLIENT or DEDICATED_SERVER).
 *
 * NO CHANGES NEEDED IN PackAPunchMod.java:
 * ──────────────────────────────────────────────────────────
 * The @EventBusSubscriber annotation is discovered automatically.
 * You do NOT need to reference ClientSetup anywhere else.
 * NeoForge's annotation scanning finds it and hooks it up.
 */
@SuppressWarnings({"null", "removal"})
@EventBusSubscriber(modid = PackAPunchMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    /**
     * Screen Registration — the missing link between Menu and Screen.
     *
     * RegisterMenuScreensEvent fires during the mod loading lifecycle,
     * after all registries (MenuTypes, etc.) are finalized, but before
     * the game's main menu appears.
     *
     * event.register(menuType, screenFactory):
     *   menuType      → The registered MenuType we created in ModMenuTypes.
     *                   When the client receives "open this MenuType", it looks
     *                   up the corresponding Screen factory here.
     *   screenFactory → A constructor reference: PackAPunchScreen::new
     *                   This is shorthand for:
     *                   (menu, inv, title) -> new PackAPunchScreen(menu, inv, title)
     *                   The three parameters match PackAPunchScreen's constructor.
     *
     * After this call, the flow is:
     *   Server sends "open packapunch:pack_a_punch_machine"
     *     → Client looks up MenuType in registry → found
     *     → Client looks up Screen factory in MenuScreens → found (registered here)
     *     → Client calls PackAPunchScreen::new
     *     → GUI appears on screen ✅
     *
     * @param event The event object that holds the registration method.
     *              Static because @EventBusSubscriber requires static methods
     *              when annotating a class (not an instance).
     */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(
                ModMenuTypes.PACK_A_PUNCH_MACHINE.get(), // Which MenuType to bind
                PackAPunchScreen::new                    // Which Screen to open for it
        );

        PackAPunchMod.LOGGER.info("[PackAPunch] Screen registered for Pack-a-Punch machine.");
    }
}
