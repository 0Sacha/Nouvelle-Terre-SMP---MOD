package com.nouvelleterrebridge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Canaux du Shop Serveur — écran autonome (accessible depuis le Parchemin),
 * distinct du HDV entre joueurs.
 */
public final class ShopNetworking {

    public static final CustomPacketPayload.Type<NtPayload> SHOP_OPEN = NtNet.canal("shop_open");
    public static final CustomPacketPayload.Type<NtPayload> SHOP_ACTION = NtNet.canal("shop_action");
    public static final CustomPacketPayload.Type<NtPayload> SHOP_RESULT = NtNet.canal("shop_result");

    public static final int ACTION_BUY             = 0;  // le joueur achète au serveur
    public static final int ACTION_SELL            = 1;  // le joueur revend au serveur
    public static final int ACTION_CLAIM_PARCHEMIN = 2;  // récupère un Parchemin gratuit

    private ShopNetworking() {}
}
