package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class NouvelleSettingsScreen extends Screen {

    private static final int C_BG    = 0xFF14161A;
    private static final int C_GOLD  = 0xFFE8A838;
    private static final int C_MID   = 0xFF9096A3;

    private final Screen parent;

    public NouvelleSettingsScreen(Screen parent) {
        super(Text.literal("Nouvelle Terre — Paramètres"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        addDrawableChild(ButtonWidget.builder(hudToggleText(), btn -> {
            ClientConfig cfg = ClientConfig.get();
            cfg.hudEnabled = !cfg.hudEnabled;
            cfg.save();
            btn.setMessage(hudToggleText());
        }).dimensions(cx - 100, cy - 22, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(rpcToggleText(), btn -> {
            ClientConfig cfg = ClientConfig.get();
            cfg.discordRPCEnabled = !cfg.discordRPCEnabled;
            cfg.save();
            btn.setMessage(rpcToggleText());
            if (!cfg.discordRPCEnabled) DiscordRPCManager.INSTANCE.onLeave();
        }).dimensions(cx - 100, cy + 4, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Retour"), btn -> close())
            .dimensions(cx - 75, cy + 34, 150, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
        ctx.fill(0, height / 2 - 36, width, height / 2 - 34, 0x20E8A838); // gold separator
        ctx.drawCenteredTextWithShadow(textRenderer, "Nouvelle Terre", width / 2, height / 2 - 56, C_GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, "Paramètres client", width / 2, height / 2 - 44, C_MID);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private static Text hudToggleText() {
        boolean en = ClientConfig.get().hudEnabled;
        return Text.literal("HUD Solde : " + (en ? "§aActivé" : "§cDésactivé"));
    }

    private static Text rpcToggleText() {
        boolean en = ClientConfig.get().discordRPCEnabled;
        return Text.literal("Discord Rich Presence : " + (en ? "§aActivé" : "§cDésactivé"));
    }
}
