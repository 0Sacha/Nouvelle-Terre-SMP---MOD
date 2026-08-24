package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

/** Canaux du LeBonCube (/leboncube) et du monitoring serveur (/server-admin). */
public final class ServiceNetworking {

    public static final Identifier MARCHE_OPEN   = new Identifier("nouvelle-terre-bridge", "marche_open");
    public static final Identifier MARCHE_ACTION = new Identifier("nouvelle-terre-bridge", "marche_action");
    public static final Identifier MARCHE_RESULT = new Identifier("nouvelle-terre-bridge", "marche_result");

    public static final Identifier ADMIN_OPEN    = new Identifier("nouvelle-terre-bridge", "admin_open");
    public static final Identifier ADMIN_ACTION  = new Identifier("nouvelle-terre-bridge", "admin_action");

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
