package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

/**
 * Canaux du Shop Serveur — écran autonome (accessible depuis le Parchemin),
 * distinct du HDV entre joueurs.
 */
public final class ShopNetworking {

    public static final Identifier SHOP_OPEN   = new Identifier("nouvelle-terre-bridge", "shop_open");
    public static final Identifier SHOP_ACTION = new Identifier("nouvelle-terre-bridge", "shop_action");
    public static final Identifier SHOP_RESULT = new Identifier("nouvelle-terre-bridge", "shop_result");

    public static final int ACTION_BUY             = 0;  // le joueur achète au serveur
    public static final int ACTION_SELL            = 1;  // le joueur revend au serveur
    public static final int ACTION_CLAIM_PARCHEMIN = 2;  // récupère un Parchemin gratuit

    private ShopNetworking() {}
}
