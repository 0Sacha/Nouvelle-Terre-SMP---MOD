package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class DiscordRPCManager {

    // !! Remplacer par l'Application ID de votre app Discord Developer Portal !!
    // https://discord.com/developers/applications → New Application → General Information → Application ID
    private static final String APP_ID = "1509165712562983042";

    public static final DiscordRPCManager INSTANCE = new DiscordRPCManager();

    private final DiscordIPCClient ipc = new DiscordIPCClient();
    private boolean active      = false;
    private long    sessionStart = 0;
    private int     tickCounter  = 0;

    public void onJoin(String serverAddress) {
        if (!ClientConfig.get().discordRPCEnabled) return;
        if (APP_ID.equals("REPLACE_ME")) {
            DiscordIPCClient.LOGGER.warn("[Discord RPC] APP_ID non configuré — Rich Presence désactivée.");
            return;
        }

        String configHost = ClientConfig.get().discordRPCServer.split(":")[0].trim();
        String joinHost   = serverAddress.split(":")[0].trim();
        if (!configHost.isBlank() && !joinHost.equalsIgnoreCase(configHost)
                && !joinHost.toLowerCase().contains(configHost.toLowerCase())) {
            return;
        }

        sessionStart = System.currentTimeMillis();
        active = true;
        tickCounter = 0;

        Thread t = new Thread(() -> {
            if (ipc.connect(APP_ID)) updatePresence();
        }, "discord-rpc-connect");
        t.setDaemon(true);
        t.start();
    }

    public void onLeave() {
        if (!active) return;
        active = false;
        ipc.close();
    }

    public void tick() {
        if (!active) return;
        tickCounter++;
        if (tickCounter >= 600) { // toutes les 30 s
            tickCounter = 0;
            updatePresence();
        }
    }

    private void updatePresence() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int count = 0;
        if (mc.getNetworkHandler() != null) {
            count = mc.getNetworkHandler().getPlayerList().size();
        }
        String state = count > 0
            ? count + " joueur" + (count > 1 ? "s" : "") + " en ligne"
            : null;
        ipc.setActivity("Nouvelle Terre SMP", state, sessionStart);
    }
}
