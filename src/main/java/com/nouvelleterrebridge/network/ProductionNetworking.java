package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

public final class ProductionNetworking {

    public static final CustomPayload.Id<NtPayload> PROD_OPEN = NtNet.canal("prod_open");
    public static final CustomPayload.Id<NtPayload> PROD_ACTION = NtNet.canal("prod_action");
    public static final CustomPayload.Id<NtPayload> PROD_RESULT = NtNet.canal("prod_result");

    public static final int ACTION_RESET     = 0;  // op : remet compteurs et seuils à zéro
    public static final int ACTION_RECHECK   = 1;  // op : renvoie un état frais (les seuils sont lus en direct)
    public static final int ACTION_RELOAD    = 2;  // op : recharge seuils-shop.json
    public static final int ACTION_SET_PRICE = 3;  // op : change le prix d'un item (itemId, prix)
    public static final int ACTION_TOGGLE    = 4;  // op : active/désactive la vente d'un item (itemId)
    public static final int ACTION_DELETE    = 5;  // op : supprime l'entrée du catalogue (itemId)
    public static final int ACTION_SET_RACHAT = 6; // op : prix de rachat imposé (itemId, prix ; 0 = auto)
    public static final int ACTION_PURGE_MARCHE = 7; // op : efface flux et demande (soldes et production intacts)

    private ProductionNetworking() {}
}
