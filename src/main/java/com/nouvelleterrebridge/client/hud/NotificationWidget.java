package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.nouvelleterrebridge.client.NotificationHud;

/**
 * Emplacement des notifications. Ne dessine rien en jeu : {@link NotificationHud}
 * y affiche les vraies. Dans l'éditeur, montre une notification d'exemple pour
 * qu'on voie ce qu'on déplace.
 */
public class NotificationWidget extends HudWidget {

    public NotificationWidget() { super("notif", "Notifications", 0.99f, 0.85f, true); }

    @Override public boolean isDragOnly() { return true; }

    @Override protected int contentWidth(Minecraft mc)  { return NotificationHud.LARGEUR; }
    @Override protected int contentHeight(Minecraft mc) { return 32; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        if (!enEdition) return;
        NotificationHud.dessiner(ctx, mc, 0, 0, C_GREEN,
            new String[]{"Notification", "Les messages du serveur s'affichent ici"});
    }
}
