package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

public final class ProductionNetworking {

    public static final Identifier PROD_OPEN   = new Identifier("nouvelle-terre-bridge", "prod_open");
    public static final Identifier PROD_ACTION = new Identifier("nouvelle-terre-bridge", "prod_action");
    public static final Identifier PROD_RESULT = new Identifier("nouvelle-terre-bridge", "prod_result");

    public static final int ACTION_RESET     = 0;  // op : remet les compteurs à zéro + retire les annonces auto
    public static final int ACTION_RECHECK   = 1;  // op : re-vérifie les seuils
    public static final int ACTION_RELOAD    = 2;  // op : recharge seuils-shop.json
    public static final int ACTION_SET_PRICE = 3;  // op : change le prix d'un item (itemId, prix)
    public static final int ACTION_TOGGLE    = 4;  // op : active/désactive la vente d'un item (itemId)
    public static final int ACTION_DELETE    = 5;  // op : supprime l'entrée du catalogue (itemId)

    private ProductionNetworking() {}
}
