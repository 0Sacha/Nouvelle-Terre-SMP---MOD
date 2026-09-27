package com.nouvelleterrebridge.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.ServerShopActions;
import com.nouvelleterrebridge.economy.TransactionLog;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LeBonCube — petites annonces de services entre joueurs.
 *
 * Persiste dans {@code leboncube.json}. Le fichier contient les annonces, les
 * commandes (en cours et archivées) et les notes des prestataires.
 *
 * <p><b>Circuit de l'argent.</b> À la commande, le client paie le prix complet :
 * la moitié part immédiatement au prestataire (l'acompte, il commence à
 * travailler), l'autre moitié est retenue par le compte système
 * {@link #COMPTE_SEQUESTRE} jusqu'à ce que les deux parties valident. Retenir le
 * solde est indispensable : sans ça, un client fauché au moment de la validation
 * bloquerait une prestation déjà réalisée.
 */
public final class ServiceManager {

    /** Compte système qui détient le solde des prestations en cours. */
    public static final String COMPTE_SEQUESTRE = "$Sequestre";

    /** Taxe de publication, versée au serveur. Volontairement modeste — 2 %, minimum 5 ◆. */
    public static final int TAXE_MIN = 5;

    public static final String[] CONTACTS = {"Chat", "Oral", "Courrier"};

    /** Longueur max d'une catégorie saisie par un joueur. */
    private static final int CATEGORIE_MAX = 20;

    /**
     * Catégories existantes, déduites des annonces en ligne.
     *
     * Elles ne sont pas figées dans le code : le premier joueur qui propose du
     * minage crée « Mineur », et les suivants la retrouvent dans la liste. Les
     * déduire des annonces plutôt que les stocker à part évite d'accumuler des
     * catégories mortes — une catégorie disparaît d'elle-même avec la dernière
     * annonce qui l'utilisait.
     */
    public synchronized List<String> categories() {
        return donnees.annonces.stream()
            .filter(a -> a.active)
            .map(a -> a.categorie)
            .filter(c -> c != null && !c.isBlank())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    /** Met une catégorie en forme : espaces réduits, longueur bornée, initiale en majuscule. */
    public static String normaliserCategorie(String brut) {
        if (brut == null) return "";
        String c = brut.trim().replaceAll("\\s+", " ");
        if (c.length() > CATEGORIE_MAX) c = c.substring(0, CATEGORIE_MAX).trim();
        if (c.isEmpty()) return "";
        return Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    /** Note laissée par un client sur une prestation terminée. */
    public static class Note {
        public String client;
        public int    etoiles;
        public String avis = "";
        public long   laisseeLe;
        public int    commandeId;

        public Note() {}
        public Note(String client, int etoiles, String avis, int commandeId) {
            this.client     = client;
            this.etoiles    = etoiles;
            this.avis       = avis == null ? "" : avis;
            this.commandeId = commandeId;
            this.laisseeLe  = System.currentTimeMillis();
        }
    }

    private static class Donnees {
        List<ServiceAnnonce>  annonces  = new ArrayList<>();
        List<ServiceCommande> commandes = new ArrayList<>();
        /** pseudo (minuscule) du prestataire → notes reçues. */
        Map<String, List<Note>> notes   = new HashMap<>();
        int prochainIdAnnonce  = 1;
        int prochainIdCommande = 1;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FICHIER =
        FMLPaths.GAMEDIR.get().resolve("leboncube.json");

    private static ServiceManager instance;

    private Donnees donnees = new Donnees();
    private final AtomicInteger idAnnonce  = new AtomicInteger(1);
    private final AtomicInteger idCommande = new AtomicInteger(1);

    private ServiceManager() { charger(); }

    public static synchronized ServiceManager getInstance() {
        if (instance == null) instance = new ServiceManager();
        return instance;
    }

    // ── Persistance ───────────────────────────────────────────────────────────

    private void charger() {
        File f = FICHIER.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Donnees d = GSON.fromJson(r, Donnees.class);
            if (d != null) {
                donnees = d;
                if (donnees.annonces  == null) donnees.annonces  = new ArrayList<>();
                if (donnees.commandes == null) donnees.commandes = new ArrayList<>();
                if (donnees.notes     == null) donnees.notes     = new HashMap<>();
                idAnnonce.set(Math.max(1, donnees.prochainIdAnnonce));
                idCommande.set(Math.max(1, donnees.prochainIdCommande));
            }
            NouvelleTerreBridge.LOGGER.info("[LeBonCube] {} annonce(s), {} commande(s) chargée(s).",
                donnees.annonces.size(), donnees.commandes.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LeBonCube] Erreur lecture : {}", e.getMessage());
        }
    }

    private void sauver() {
        donnees.prochainIdAnnonce  = idAnnonce.get();
        donnees.prochainIdCommande = idCommande.get();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FICHIER.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(donnees, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LeBonCube] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    public synchronized List<ServiceAnnonce> annoncesActives() {
        return donnees.annonces.stream().filter(a -> a.active).toList();
    }

    public synchronized List<ServiceAnnonce> toutesAnnonces() {
        return new ArrayList<>(donnees.annonces);
    }

    public synchronized Optional<ServiceAnnonce> annonce(int id) {
        return donnees.annonces.stream().filter(a -> a.id == id).findFirst();
    }

    public synchronized Optional<ServiceCommande> commande(int id) {
        return donnees.commandes.stream().filter(c -> c.id == id).findFirst();
    }

    /** Commandes en cours où le joueur est prestataire. */
    public synchronized List<ServiceCommande> prestationsDe(String pseudo) {
        return donnees.commandes.stream()
            .filter(c -> !c.estArchivee() && c.prestataire.equalsIgnoreCase(pseudo))
            .toList();
    }

    /** Commandes en cours où le joueur est client. */
    public synchronized List<ServiceCommande> commandesDe(String pseudo) {
        return donnees.commandes.stream()
            .filter(c -> !c.estArchivee() && c.client.equalsIgnoreCase(pseudo))
            .toList();
    }

    public synchronized List<ServiceCommande> archivesDe(String pseudo) {
        return donnees.commandes.stream()
            .filter(c -> c.estArchivee() && c.concerne(pseudo))
            .sorted(Comparator.comparingLong((ServiceCommande c) -> c.termineeLe).reversed())
            .toList();
    }

    public synchronized List<ServiceCommande> toutesCommandes() {
        return new ArrayList<>(donnees.commandes);
    }

    public synchronized List<Note> notesDe(String prestataire) {
        return donnees.notes.getOrDefault(prestataire.toLowerCase(), List.of());
    }

    /** Note moyenne sur 5, 0 si le prestataire n'a jamais été noté. */
    public synchronized float noteMoyenne(String prestataire) {
        List<Note> l = notesDe(prestataire);
        if (l.isEmpty()) return 0f;
        int somme = 0;
        for (Note n : l) somme += n.etoiles;
        return somme / (float) l.size();
    }

    /** Total ◆ actuellement retenu en séquestre (prestations en cours). */
    public synchronized int totalSequestre() {
        int total = 0;
        for (ServiceCommande c : donnees.commandes)
            if (!c.estArchivee()) total += c.sequestre;
        return total;
    }

    // ── Publication ───────────────────────────────────────────────────────────

    public static int taxePublication(int prix) {
        return Math.max(TAXE_MIN, prix / 50);
    }

    /**
     * Publie une annonce. Le prix couvre la prestation entière ; la taxe est
     * prélevée immédiatement pour décourager les annonces jetables.
     *
     * @return message d'erreur, ou null si publiée
     */
    public synchronized String publier(String auteur, String titre, String description,
                                       String imageUrl, int prix, String contact, String categorie) {
        if (titre == null || titre.isBlank())       return "§cIl faut un titre.";
        if (description == null || description.isBlank()) return "§cIl faut une description.";
        if (prix <= 0)                              return "§cLe prix doit être supérieur à 0.";
        if (titre.length() > 60)                    return "§cTitre trop long (60 caractères max).";
        if (description.length() > 500)             return "§cDescription trop longue (500 caractères max).";

        String url = imageUrl == null ? "" : imageUrl.trim();
        // Motif précis plutôt qu'un refus générique : le joueur doit savoir si c'est
        // l'hôte, le format ou le protocole qui coince.
        String refus = ServiceImages.verifier(url);
        if (refus != null) return refus;

        int taxe = taxePublication(prix);
        LocalEconomy eco = LocalEconomy.getInstance();
        if (eco.getBalance(auteur) < taxe)
            return "§cPublication : §f" + taxe + " ◆§c de taxe requis, tu as §f"
                 + eco.getBalance(auteur) + " ◆§c.";

        String cat = normaliserCategorie(categorie);
        if (cat.isEmpty()) return "§cChoisissez une catégorie, ou créez-en une.";

        eco.removeShards(auteur, taxe);
        eco.addShards(ServerShopActions.COMPTE_SERVEUR, taxe, "Taxe de publication LeBonCube");
        TransactionLog.log(auteur, TransactionLog.TYPE_BUY, "Publication LeBonCube : " + titre, taxe);

        ServiceAnnonce a = new ServiceAnnonce(idAnnonce.getAndIncrement(), auteur, titre.trim(),
            description.trim(), url, prix, normaliser(contact, CONTACTS, "Chat"), cat);
        donnees.annonces.add(a);
        sauver();
        return null;
    }

    private static String normaliser(String valeur, String[] autorisees, String defaut) {
        if (valeur == null) return defaut;
        for (String v : autorisees) if (v.equalsIgnoreCase(valeur)) return v;
        return defaut;
    }

    /** Retire une annonce (son auteur uniquement, ou un op). */
    public synchronized String retirer(String demandeur, int annonceId, boolean estOp) {
        Optional<ServiceAnnonce> opt = annonce(annonceId);
        if (opt.isEmpty()) return "§cAnnonce introuvable.";
        ServiceAnnonce a = opt.get();
        if (!estOp && !a.auteur.equalsIgnoreCase(demandeur))
            return "§cCette annonce n'est pas la tienne.";
        a.active = false;
        sauver();
        return null;
    }

    // ── Commande ──────────────────────────────────────────────────────────────

    /**
     * Le client commande la prestation : il règle le prix complet, dont la moitié
     * part tout de suite au prestataire et l'autre en séquestre.
     */
    public synchronized String commander(String client, int annonceId) {
        Optional<ServiceAnnonce> opt = annonce(annonceId);
        if (opt.isEmpty() || !opt.get().active) return "§cCette annonce n'est plus disponible.";
        ServiceAnnonce a = opt.get();

        if (a.auteur.equalsIgnoreCase(client))
            return "§cTu ne peux pas commander ta propre prestation.";

        boolean dejaEnCours = donnees.commandes.stream().anyMatch(c ->
            !c.estArchivee() && c.annonceId == annonceId && c.client.equalsIgnoreCase(client));
        if (dejaEnCours) return "§cTu as déjà une commande en cours sur cette annonce.";

        LocalEconomy eco = LocalEconomy.getInstance();
        if (eco.getBalance(client) < a.prix)
            return "§cSolde insuffisant — §f" + a.prix + " ◆§c requis, tu as §f"
                 + eco.getBalance(client) + " ◆§c.";

        int acompte   = a.prix / 2;
        int sequestre = a.prix - acompte;   // le reste, pour ne pas perdre l'impair

        eco.removeShards(client, a.prix);
        TransactionLog.log(client, TransactionLog.TYPE_TRANSFER_OUT,
            "LeBonCube : " + a.titre + " (acompte + séquestre)", a.prix);

        eco.addShards(a.auteur, acompte, "LeBonCube — acompte : " + a.titre);
        eco.addShards(COMPTE_SEQUESTRE, sequestre, "Séquestre LeBonCube");

        ServiceCommande c = new ServiceCommande(idCommande.getAndIncrement(), a, client, acompte, sequestre);
        donnees.commandes.add(c);
        sauver();
        return null;
    }

    /** Le prestataire déclare la prestation terminée. */
    public synchronized String marquerLivree(String prestataire, int commandeId) {
        Optional<ServiceCommande> opt = commande(commandeId);
        if (opt.isEmpty()) return "§cCommande introuvable.";
        ServiceCommande c = opt.get();
        if (!c.prestataire.equalsIgnoreCase(prestataire)) return "§cCette prestation n'est pas la tienne.";
        if (c.estArchivee()) return "§cCette commande est déjà close.";

        c.valideParPrestataire = true;
        c.statut = ServiceCommande.LIVREE;
        sauver();
        return null;
    }

    /**
     * Le client valide la prestation : le séquestre est libéré vers le prestataire
     * et la commande part aux archives. La note est facultative (0 = pas de note).
     */
    public synchronized String validerClient(String client, int commandeId, int etoiles, String avis) {
        Optional<ServiceCommande> opt = commande(commandeId);
        if (opt.isEmpty()) return "§cCommande introuvable.";
        ServiceCommande c = opt.get();
        if (!c.client.equalsIgnoreCase(client)) return "§cCette commande n'est pas la tienne.";
        if (c.estArchivee()) return "§cCette commande est déjà close.";
        if (!c.valideParPrestataire)
            return "§cLe prestataire n'a pas encore déclaré la prestation terminée.";

        c.valideParClient = true;
        c.statut          = ServiceCommande.TERMINEE;
        c.termineeLe      = System.currentTimeMillis();

        LocalEconomy eco = LocalEconomy.getInstance();
        if (c.sequestre > 0) {
            eco.forceDeduct(COMPTE_SEQUESTRE, c.sequestre);
            eco.addShards(c.prestataire, c.sequestre, "LeBonCube — solde : " + c.titre);
            c.sequestre = 0;
        }

        if (etoiles >= 1 && etoiles <= 5) {
            c.note = etoiles;
            c.avis = avis == null ? "" : avis.trim();
            donnees.notes.computeIfAbsent(c.prestataire.toLowerCase(), k -> new ArrayList<>())
                .add(new Note(client, etoiles, c.avis, c.id));
        }
        sauver();
        return null;
    }

    /**
     * Demande d'annulation. Quand les deux parties l'ont demandée, la commande est
     * annulée et le client est <b>intégralement</b> remboursé — acompte compris,
     * puisque le prestataire y consent en acceptant.
     */
    public synchronized String demanderAnnulation(String demandeur, int commandeId) {
        Optional<ServiceCommande> opt = commande(commandeId);
        if (opt.isEmpty()) return "§cCommande introuvable.";
        ServiceCommande c = opt.get();
        if (!c.concerne(demandeur)) return "§cCette commande ne te concerne pas.";
        if (c.estArchivee()) return "§cCette commande est déjà close.";

        if (c.annulationDemandeePar == null) {
            c.annulationDemandeePar = demandeur;
            sauver();
            return null;
        }
        if (c.annulationDemandeePar.equalsIgnoreCase(demandeur))
            return "§cTu as déjà demandé l'annulation — en attente de l'autre partie.";

        annulerEtRembourser(c, true);
        sauver();
        return null;
    }

    /**
     * Annule la commande et rembourse le client.
     *
     * @param rembourserAcompte true = remboursement intégral (accord mutuel) ;
     *                          false = seul le séquestre revient au client, le
     *                          prestataire garde l'acompte du travail engagé.
     */
    private void annulerEtRembourser(ServiceCommande c, boolean rembourserAcompte) {
        LocalEconomy eco = LocalEconomy.getInstance();
        int rendu = 0;

        if (c.sequestre > 0) {
            eco.forceDeduct(COMPTE_SEQUESTRE, c.sequestre);
            rendu += c.sequestre;
            c.sequestre = 0;
        }
        if (rembourserAcompte && c.acompte > 0) {
            // forceDeduct : le prestataire a pu dépenser l'acompte entre-temps,
            // le remboursement ne doit pas être bloqué pour autant.
            eco.forceDeduct(c.prestataire, c.acompte);
            rendu += c.acompte;
        }
        if (rendu > 0) {
            eco.addShards(c.client, rendu, "LeBonCube — remboursement : " + c.titre);
        }

        c.statut     = ServiceCommande.ANNULEE;
        c.termineeLe = System.currentTimeMillis();
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    public synchronized String envoyerMessage(String auteur, int commandeId, String texte) {
        Optional<ServiceCommande> opt = commande(commandeId);
        if (opt.isEmpty()) return "§cCommande introuvable.";
        ServiceCommande c = opt.get();
        if (!c.concerne(auteur)) return "§cCette commande ne te concerne pas.";
        if (texte == null || texte.isBlank()) return "§cMessage vide.";
        if (texte.length() > 200) return "§cMessage trop long (200 caractères max).";

        c.messages.add(new ServiceCommande.Message(auteur, texte.trim()));
        // Les échanges anciens n'ont pas d'intérêt et alourdiraient le paquet réseau
        while (c.messages.size() > 50) c.messages.remove(0);
        sauver();
        return null;
    }

    // ── Arbitrage (op, via /server-admin) ─────────────────────────────────────

    /** Un op tranche un litige : rembourse le client intégralement. */
    public synchronized String arbitrerRembourser(int commandeId) {
        Optional<ServiceCommande> opt = commande(commandeId);
        if (opt.isEmpty()) return "§cCommande introuvable.";
        ServiceCommande c = opt.get();
        if (c.estArchivee()) return "§cCette commande est déjà close.";
        annulerEtRembourser(c, true);
        sauver();
        return null;
    }

    /** Un op tranche un litige : libère le séquestre au prestataire. */
    public synchronized String arbitrerPayer(int commandeId) {
        Optional<ServiceCommande> opt = commande(commandeId);
        if (opt.isEmpty()) return "§cCommande introuvable.";
        ServiceCommande c = opt.get();
        if (c.estArchivee()) return "§cCette commande est déjà close.";

        LocalEconomy eco = LocalEconomy.getInstance();
        if (c.sequestre > 0) {
            eco.forceDeduct(COMPTE_SEQUESTRE, c.sequestre);
            eco.addShards(c.prestataire, c.sequestre, "LeBonCube — arbitrage : " + c.titre);
            c.sequestre = 0;
        }
        c.statut     = ServiceCommande.TERMINEE;
        c.termineeLe = System.currentTimeMillis();
        sauver();
        return null;
    }
}
