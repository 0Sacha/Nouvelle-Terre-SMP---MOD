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

            ClientConfig cfg = ClientConfig.get();
            if (!cfg.notifEnabled || queue.isEmpty()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            // Ancre = coin haut-gauche de la zone de notification (clamped comme un widget)
            int ax = Math.max(0, Math.min((int)(cfg.notifX * sw), sw - TOAST_W));
            int ay = Math.max(0, Math.min((int)(cfg.notifY * sh), sh - 40));

            int slot = 0;
            for (Toast t : queue) {
                int h = t.lines().length * LINE_H + PAD * 2;
                int x = ax;
                int y = ay + slot * (h + 4);

                ctx.fill(x, y, x + TOAST_W, y + h, 0xCC1B1D22);
                ctx.fill(x, y, x + 2, y + h, t.color());
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
