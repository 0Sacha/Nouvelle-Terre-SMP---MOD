package com.nouvelleterrebridge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Canaux de la location d'objets entre joueurs.
 *
 * <pre>
 * LOC_OPEN   : int balance | offres[] | contrats[] | retours[]
 * LOC_ACTION : int action | int id | int prix | int caution
 *              id = emplacement d'inventaire pour PROPOSER, numéro d'offre,
 *              de contrat ou de retour sinon. prix et caution = 0 hors PROPOSER :
 *              un format unique évite de faire dépendre la lecture de l'action.
 * LOC_RESULT : bool ok | string msg | [même contenu que LOC_OPEN]
 *
 * offres[]   : int n → (int id, string proprietaire, string item, string nbt,
 *                       int prixJour, int caution, bool mienne) × n
 * contrats[] : int n → (int id, string proprietaire, string locataire, string item,
 *                       int prixJour, int caution, long resteMs, int etat,
 *                       bool resilie, bool jeSuisLocataire) × n
 *              resteMs = temps avant le prochain loyer (contrat à jour) ou avant
 *              la reprise (objet verrouillé) — une durée et non une date, les
 *              horloges du client et du serveur n'ayant aucune raison de concorder.
 * retours[]  : int n → (int id, string item, string nbt, string origine) × n
 * </pre>
 */
public final class LocationNetworking {

    public static final CustomPacketPayload.Type<NtPayload> LOC_OPEN   = NtNet.canal("loc_open");
    public static final CustomPacketPayload.Type<NtPayload> LOC_ACTION = NtNet.canal("loc_action");
    public static final CustomPacketPayload.Type<NtPayload> LOC_RESULT = NtNet.canal("loc_result");

    public static final int ACTION_PROPOSER  = 0;  // propriétaire : met un objet en location
    public static final int ACTION_RETIRER   = 1;  // propriétaire : retire son offre
    public static final int ACTION_LOUER     = 2;  // locataire : prend l'offre
    public static final int ACTION_RENDRE    = 3;  // locataire : rend l'objet
    public static final int ACTION_PERDU     = 4;  // locataire : objet cassé ou introuvable
    public static final int ACTION_RESILIER  = 5;  // propriétaire : met fin au contrat
    public static final int ACTION_RECUPERER = 6;  // propriétaire : reprend l'objet revenu

    private LocationNetworking() {}
}
