package com.nouvelleterrebridge.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class NouvelleSettingsScreen extends Screen {

    private static final int C_BG   = 0xFF14161A;
    private static final int C_GOLD = 0xFFE8A838;
    private static final int C_MID  = 0xFF9096A3;

    private final Screen parent;

    public NouvelleSettingsScreen(Screen parent) {
        super(Component.literal("Nouvelle Terre — Paramètres"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;

        addRenderableWidget(Button.builder(Component.literal("Éditeur HUD →"),
                btn -> this.minecraft.setScreen(new HudEditorScreen()))
            .bounds(cx - 100, cy - 22, 200, 20).build());

        addRenderableWidget(Button.builder(rpcToggleText(), btn -> {
            ClientConfig cfg = ClientConfig.get();
            cfg.discordRPCEnabled = !cfg.discordRPCEnabled;
            cfg.save();
            btn.setMessage(rpcToggleText());
            if (!cfg.discordRPCEnabled) DiscordRPCManager.INSTANCE.onLeave();
        }).bounds(cx - 100, cy + 4, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Retour"), btn -> onClose())
            .bounds(cx - 75, cy + 34, 150, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // No-op — cet écran dessine son propre fond ; super.render() (appelé en dernier
        // pour les widgets vanilla) réappliquerait sinon flou + texture menu par-dessus.
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
        ctx.fill(0, height / 2 - 36, width, height / 2 - 34, 0x20E8A838);
        ctx.drawCenteredString(font, "Nouvelle Terre", width / 2, height / 2 - 56, C_GOLD);
        ctx.drawCenteredString(font, "Paramètres client", width / 2, height / 2 - 44, C_MID);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() { if (this.minecraft != null) this.minecraft.setScreen(parent); }

    private static Component rpcToggleText() {
        boolean en = ClientConfig.get().discordRPCEnabled;
        return Component.literal("Discord Rich Presence : " + (en ? "§aActivé" : "§cDésactivé"));
    }
}
