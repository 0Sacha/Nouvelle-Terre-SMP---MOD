package com.nouvelleterrebridge.service;

/**
 * Annonce de service du LeBonCube — une prestation proposée par un joueur.
 *
 * Contrairement au HDV et au Shop Serveur, rien n'est livré en items : ce sont
 * des services RP (stand d'événement, chantier de minage, transport…) dont
 * l'exécution se passe en jeu, entre les deux joueurs.
 */
public class ServiceAnnonce {

    public int    id;
    public String auteur;        // pseudo du prestataire
    public String titre;
    public String description;
    public String imageUrl;      // "" si aucune ; hôte validé côté serveur
    public int    prix;          // ◆ pour la prestation complète
    public String contact;       // ServiceManager.CONTACTS
    public String categorie;     // ServiceManager.CATEGORIES
    public long   creeLe;
    public boolean active = true;

    public ServiceAnnonce() {}

    public ServiceAnnonce(int id, String auteur, String titre, String description,
                          String imageUrl, int prix, String contact, String categorie) {
        this.id          = id;
        this.auteur      = auteur;
        this.titre       = titre;
        this.description = description;
        this.imageUrl    = imageUrl;
        this.prix        = prix;
        this.contact     = contact;
        this.categorie   = categorie;
        this.creeLe      = System.currentTimeMillis();
    }
}
