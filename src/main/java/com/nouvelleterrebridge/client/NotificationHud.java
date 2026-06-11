package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

@Environment(EnvType.CLIENT)
public class NotificationHud {

    public static final int COLOR_GREEN = 0xFF2EAD6B;
    public static final int COLOR_GOLD  = 0xFFE8A838;
    public static final int COLOR_RED   = 0xFFBF2040;

    private static final long DURATION_MS = 4500;
    private static final int  MAX_TOASTS  = 3;
    private static final int  TOAST_W     = 190;
    private static final int  LINE_H      = 10;
    private static final int  PAD         = 6;

    private record Toast(String[] lines, int color, long born) {}
    private static final Deque<Toast> queue = new ArrayDeque<>();

    public static void push(int color, String... lines) {
        if (queue.size() >= MAX_TOASTS) queue.removeLast();
        queue.addFirst(new Toast(lines, color, System.currentTimeMillis()));
    }

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            long now = System.currentTimeMillis();
            queue.removeIf(t -> now - t.born() > DURATION_MS);
            if (queue.isEmpty()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            int slot = 0;
            for (Toast t : queue) {
                int h = t.lines().length * LINE_H + PAD * 2;
                int x = sw - TOAST_W - 8;
                int y = sh - 50 - slot * (h + 4);

                // fond + bordure gauche colorée
                ctx.fill(x, y, x + TOAST_W, y + h, 0xCC1B1D22);
                ctx.fill(x, y, x + 2, y + h, t.color());

                // lignes : titre en couleur, détails en gris
                for (int l = 0; l < t.lines().length; l++) {
                    int col = l == 0 ? t.color() : 0xFF9096A3;
                    ctx.drawText(mc.textRenderer, t.lines()[l],
                        x + 9, y + PAD + l * LINE_H, col, false);
                }
                slot++;
            }
        });
    }
}
