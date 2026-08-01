package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

/**
 * Canaux du Parchemin — hub d'accès aux différentes fenêtres du mod
 * pour les joueurs qui ne passent pas par les commandes du chat.
 */
public final class HubNetworking {

    public static final Identifier HUB_OPEN   = new Identifier("nouvelle-terre-bridge", "hub_open");
    public static final Identifier HUB_ACTION = new Identifier("nouvelle-terre-bridge", "hub_action");

    public static final int ACTION_HDV        = 0;
    public static final int ACTION_BANK       = 1;
    public static final int ACTION_QUETES     = 2;
    public static final int ACTION_PRODUCTION = 3;
    public static final int ACTION_REGISTRE   = 4;
    public static final int ACTION_CONFLIT    = 5;
    public static final int ACTION_WIKI       = 6;
    public static final int ACTION_SHOP       = 7;

    private HubNetworking() {}
}
