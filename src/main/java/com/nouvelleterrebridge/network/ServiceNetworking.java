package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

/** Canaux du LeBonCube (/leboncube) et du monitoring serveur (/server-admin). */
public final class ServiceNetworking {

    public static final CustomPayload.Id<NtPayload> MARCHE_OPEN = NtNet.canal("marche_open");
    public static final CustomPayload.Id<NtPayload> MARCHE_ACTION = NtNet.canal("marche_action");
    public static final CustomPayload.Id<NtPayload> MARCHE_RESULT = NtNet.canal("marche_result");

    public static final CustomPayload.Id<NtPayload> ADMIN_OPEN = NtNet.canal("admin_open");
    public static final CustomPayload.Id<NtPayload> ADMIN_ACTION = NtNet.canal("admin_action");

    // ── Actions LeBonCube ──
    public static final int ACTION_PUBLIER      = 0;  // titre, desc, image, prix, contact, categorie
    public static final int ACTION_RETIRER      = 1;  // annonceId
    public static final int ACTION_COMMANDER    = 2;  // annonceId
    public static final int ACTION_LIVREE       = 3;  // commandeId (prestataire)
    public static final int ACTION_VALIDER      = 4;  // commandeId + note + avis (client)
    public static final int ACTION_ANNULER      = 5;  // commandeId
    public static final int ACTION_MESSAGE      = 6;  // commandeId + texte

    // ── Actions admin (op 4 requis, revalidé serveur) ──
    public static final int ADMIN_REMBOURSER    = 0;  // commandeId
    public static final int ADMIN_PAYER         = 1;  // commandeId
    public static final int ADMIN_RETIRER_ANNONCE = 2; // annonceId

    private ServiceNetworking() {}
}
