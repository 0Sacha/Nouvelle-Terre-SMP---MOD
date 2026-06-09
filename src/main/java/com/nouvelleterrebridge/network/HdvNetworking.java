package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

public final class HdvNetworking {

    // S2C — serveur ouvre le HDV avec les données du marché
    public static final Identifier HDV_OPEN   = new Identifier("nouvelle-terre-bridge", "hdv_open");
    // C2S — joueur effectue une action (acheter / vendre / retirer)
    public static final Identifier HDV_ACTION = new Identifier("nouvelle-terre-bridge", "hdv_action");
    // S2C — résultat d'une action + données mises à jour
    public static final Identifier HDV_RESULT = new Identifier("nouvelle-terre-bridge", "hdv_result");
    // S2C — version du mod serveur envoyée à la connexion pour vérification client
    public static final Identifier NT_VERSION = new Identifier("nouvelle-terre-bridge", "nt_version");

    public static final int ACTION_BUY      = 0;
    public static final int ACTION_SELL     = 1;
    public static final int ACTION_WITHDRAW = 2;
    public static final int ACTION_TRANSFER = 3;

    private HdvNetworking() {}
}
