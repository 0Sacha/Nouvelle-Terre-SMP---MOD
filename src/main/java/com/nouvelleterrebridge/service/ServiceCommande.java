package com.nouvelleterrebridge.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande d'un service : le contrat entre un client et un prestataire.
 *
 * Le titre et le prix sont recopiés depuis l'annonce à la commande — l'annonce
 * peut être modifiée ou retirée ensuite, le contrat déjà conclu ne doit pas
 * changer sous les pieds des deux parties.
 */
public class ServiceCommande {

    /** Prestation acceptée, en cours de réalisation. */
    public static final String EN_COURS = "EN_COURS";
    /** Le prestataire a déclaré avoir terminé ; en attente de validation du client. */
    public static final String LIVREE   = "LIVREE";
    /** Les deux ont validé : solde versé, commande archivée. */
    public static final String TERMINEE = "TERMINEE";
    /** Annulée d'un commun accord ou par arbitrage : archivée. */
    public static final String ANNULEE  = "ANNULEE";

    public static class Message {
        public String auteur;
        public String texte;
        public long   envoyeLe;

        public Message() {}
        public Message(String auteur, String texte) {
            this.auteur   = auteur;
            this.texte    = texte;
            this.envoyeLe = System.currentTimeMillis();
        }
    }

    public int    id;
    public int    annonceId;
    public String titre;         // recopié de l'annonce
    public String client;
    public String prestataire;
    public int    prix;
    public int    acompte;       // versé au prestataire à la commande
    public int    sequestre;     // détenu par $Sequestre jusqu'à validation

    public String statut = EN_COURS;
    public boolean valideParPrestataire;
    public boolean valideParClient;

    /** Pseudo de celui qui demande l'annulation, null si personne. */
    public String annulationDemandeePar;

    public long creeLe;
    public long termineeLe;

    /** Note de 1 à 5 laissée par le client, 0 tant qu'il n'a pas noté. */
    public int    note;
    public String avis = "";

    public List<Message> messages = new ArrayList<>();

    public ServiceCommande() {}

    public ServiceCommande(int id, ServiceAnnonce annonce, String client, int acompte, int sequestre) {
        this.id          = id;
        this.annonceId   = annonce.id;
        this.titre       = annonce.titre;
        this.prestataire = annonce.auteur;
        this.client      = client;
        this.prix        = annonce.prix;
        this.acompte     = acompte;
        this.sequestre   = sequestre;
        this.creeLe      = System.currentTimeMillis();
    }

    public boolean estArchivee() {
        return TERMINEE.equals(statut) || ANNULEE.equals(statut);
    }

    public boolean concerne(String pseudo) {
        return client.equalsIgnoreCase(pseudo) || prestataire.equalsIgnoreCase(pseudo);
    }
}
