package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.client.hud.HudWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Notifications du serveur (achat, virement, déblocage au Shop…).
 *
 * Leur position est celle du widget « notif » de l'éditeur HUD, qui ne sert qu'à
 * ça : il ne dessine rien en jeu, c'est ici que les vraies notifications sortent.
 */
public class NotificationHud {

    public static final int COLOR_GREEN = 0xFF2EAD6B;
    public static final int COLOR_GOLD  = 0xFFE8A838;
    public static final int COLOR_RED   = 0xFFBF2040;

    public static final int LARGEUR = 190;

    private static final long DURATION_MS = 4500;
    private static final int  MAX_TOASTS  = 3;
    private static final int  LINE_H      = 10;
    private static final int  PAD         = 6;

    private record Toast(String[] lines, int color, long born) {}
    private static final Deque<Toast> queue = new ArrayDeque<>();

    public static void push(int color, String... lines) {
        if (queue.size() >= MAX_TOASTS) queue.removeLast();
        queue.addFirst(new Toast(lines, color, System.currentTimeMillis()));
    }

    /** Dessine une notification. Partagé avec l'aperçu de l'éditeur HUD. @return sa hauteur */
    public static int dessiner(GuiGraphics ctx, Minecraft mc, int x, int y, int couleur, String[] lignes) {
        int h = lignes.length * LINE_H + PAD * 2;
        HudWidget.fondArrondi(ctx, x, y, LARGEUR, h, 0xB4000000);
        ctx.fill(x, y + 1, x + 2, y + h - 1, couleur);
        for (int l = 0; l < lignes.length; l++)
            ctx.drawString(mc.font, lignes[l], x + 9, y + PAD + l * LINE_H,
                l == 0 ? couleur : 0xFFB4B9C4, true);
        return h;
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                com.nouvelleterrebridge.NouvelleTerreBridge.MOD_ID, "notifications"),
            (ctx, tickDelta) -> {
            long now = System.currentTimeMillis();
            queue.removeIf(t -> now - t.born() > DURATION_MS);
            if (queue.isEmpty()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            HudWidget zone = HudEditorScreen.widget("notif");
            if (zone == null || !zone.enabled) return;

            int x = zone.getPixelX(mc.getWindow().getGuiScaledWidth(), mc);
            int y = zone.getPixelY(mc.getWindow().getGuiScaledHeight(), mc);
            for (Toast t : queue)
                y += dessiner(ctx, mc, x, y, t.color(), t.lines()) + 4;
        });
    }
}
