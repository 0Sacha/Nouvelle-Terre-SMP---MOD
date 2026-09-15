package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

/**
 * Canaux du Parchemin — hub d'accès aux différentes fenêtres du mod
 * pour les joueurs qui ne passent pas par les commandes du chat.
 */
public final class HubNetworking {

    public static final CustomPayload.Id<NtPayload> HUB_OPEN = NtNet.canal("hub_open");
    public static final CustomPayload.Id<NtPayload> HUB_ACTION = NtNet.canal("hub_action");

    public static final int ACTION_HDV        = 0;
    public static final int ACTION_BANK       = 1;
    public static final int ACTION_QUETES     = 2;
    public static final int ACTION_PRODUCTION = 3;
    public static final int ACTION_REGISTRE   = 4;
    public static final int ACTION_CONFLIT    = 5;
    public static final int ACTION_WIKI       = 6;
    public static final int ACTION_SHOP       = 7;
    public static final int ACTION_MARCHE     = 8;  // LeBonCube — services entre joueurs

    private HubNetworking() {}
}
