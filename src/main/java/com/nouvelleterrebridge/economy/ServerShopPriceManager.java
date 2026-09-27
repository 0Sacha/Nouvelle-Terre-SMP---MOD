package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Gère les prix dynamiques du shop serveur.
 * Le prix augmente avec le nombre de ventes (supply/demand).
 * Persiste dans <gameDir>/server-shop-prices.json.
 */
public class ServerShopPriceManager {

    /**
     * Part du prix de vente que le serveur consent à payer quand c'est lui qui
     * achète. La marge est indispensable : sans elle, revendre immédiatement ce
     * qu'on vient d'acheter serait neutre, et la moindre variation de prix
     * transformerait le shop en machine à shards.
     */
    private static final float RATIO_RACHAT = 0.55f;

    /**
     * Décote maximale liée à l'abondance produite sur le serveur.
     * Volontairement modeste : la production ne mesure pas ce qui reste en jeu.
     */
    private static final double DECOTE_MAX = 0.30;

    /**
     * Demi-vie de la demande récente. Une razzia sur un item fait monter son prix
     * tout de suite, puis la pression retombe si elle ne se répète pas.
     */
    private static final double DEMI_VIE_MS = 3 * 24 * 3600 * 1000.0;

    /**
     * Solde médian considéré comme « normal ». L'inflation se mesure par rapport
     * à lui : 3 000 ◆ était très en dessous du serveur réel et clouait le
     * multiplicateur au plafond en permanence.
     */
    private static final double SOLDE_MEDIAN_REFERENCE = 20_000.0;

    public static class PriceEntry {
        public int  basePrice    = 1;
        public long unitsSold    = 0;   // vendues par le serveur aux joueurs (cumul)
        public long unitsBought  = 0;   // rachetées par le serveur aux joueurs (cumul)
        public int  dynamicPrice = 1;

        /**
         * Pression d'achat récente, amortie dans le temps (voir {@link #DEMI_VIE_MS}).
         * Positive = on achète, négative = on revend au serveur.
         */
        public double demandeRecente = 0.0;
        public long   dernierAmortissement = 0;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.GAMEDIR.get().resolve("server-shop-prices.json");
    private static Map<String, PriceEntry> prices = new HashMap<>();

    public static synchronized void load() {
        File f = FILE.toFile();
        if (!f.exists()) {
            prices = new HashMap<>();
            save();
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, PriceEntry>>(){}.getType();
            Map<String, PriceEntry> loaded = GSON.fromJson(r, type);
            if (loaded != null) prices = new HashMap<>(loaded);
            NouvelleTerreBridge.LOGGER.info("[ServerShopPriceManager] {} prix chargé(s).", prices.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ServerShopPriceManager] Erreur lecture : {}", e.getMessage());
        }
        resyncBasePrices();
    }

    /**
     * Réaligne le prix de base de chaque entrée sur {@link ShopThresholds}.
     *
     * {@code basePrice} est une copie figée au moment de la création : sans cette
     * resynchronisation, une révision des prix de référence resterait sans effet
     * sur tout item déjà échangé au shop. Le flux net (vendu/racheté), lui, est
     * l'état réel du marché et n'est jamais réinitialisé.
     */
    public static void resyncBasePrices() {
        int corriges = 0;
        for (Map.Entry<String, PriceEntry> e : prices.entrySet()) {
            ShopThresholds.Entry seuil = ShopThresholds.get(e.getKey());
            if (seuil == null || seuil.prix == e.getValue().basePrice) continue;
            e.getValue().basePrice = seuil.prix;
            e.getValue().dynamicPrice = calculatePrice(e.getKey(), e.getValue());
            corriges++;
        }
        if (corriges > 0) {
            save();
            NouvelleTerreBridge.LOGGER.info("[ServerShopPriceManager] {} prix de base resynchronisé(s).", corriges);
        }
    }

    public static synchronized void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(prices, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ServerShopPriceManager] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    /** Récupère ou crée l'entrée de prix pour un item. */
    public static synchronized PriceEntry getOrCreate(String itemId) {
        return prices.computeIfAbsent(itemId, k -> {
            ShopThresholds.Entry threshold = ShopThresholds.get(itemId);
            if (threshold == null) threshold = ShopThresholds.getOrCreate(itemId);
            PriceEntry e = new PriceEntry();
            e.basePrice = threshold != null ? threshold.prix : 1;
            e.dynamicPrice = e.basePrice;
            return e;
        });
    }

    /** Le serveur a vendu des unités au joueur : l'item se raréfie, le prix monte. */
    public static synchronized void recordSale(String itemId, int quantity) {
        PriceEntry e = getOrCreate(itemId);
        amortir(e);
        e.unitsSold      += quantity;
        e.demandeRecente += quantity;
        e.dynamicPrice = calculatePrice(itemId, e);
        save();
    }

    /** Le serveur a racheté des unités au joueur : l'item devient abondant, le prix baisse. */
    public static synchronized void recordPurchase(String itemId, int quantity) {
        PriceEntry e = getOrCreate(itemId);
        amortir(e);
        e.unitsBought    += quantity;
        e.demandeRecente -= quantity;
        e.dynamicPrice = calculatePrice(itemId, e);
        save();
    }

    /**
     * Fait décroître la demande récente selon le temps écoulé (décroissance
     * exponentielle de demi-vie {@link #DEMI_VIE_MS}). Appliqué paresseusement :
     * pas de tâche périodique à maintenir, le calcul se fait à la lecture.
     */
    private static void amortir(PriceEntry e) {
        long now = System.currentTimeMillis();
        if (e.dernierAmortissement == 0) { e.dernierAmortissement = now; return; }
        long dt = now - e.dernierAmortissement;
        if (dt <= 0) return;
        e.demandeRecente *= Math.pow(0.5, dt / DEMI_VIE_MS);
        e.dernierAmortissement = now;
        if (Math.abs(e.demandeRecente) < 0.01) e.demandeRecente = 0;
    }

    /**
     * Prix d'achat (ce que paie le joueur) : prix de référence corrigé par
     * quatre facteurs — flux net cumulé, demande récente, abondance produite,
     * et inflation générale du serveur.
     */
    private static int calculatePrice(String itemId, PriceEntry entry) {
        double echelle = echelle(itemId);
        double prix = entry.basePrice
            * multiplicateurFlux(entry, echelle)
            * multiplicateurDemandeRecente(entry, echelle)
            * multiplicateurInflation();
        prix *= (1.0 - decoteAbondance(itemId));
        return Math.max(1, (int) Math.round(prix));
    }

    /**
     * Volume de référence d'un item, au-delà duquel les échanges pèsent vraiment
     * sur son prix.
     *
     * Rapporté au seuil de déblocage : 256 diamants échangés n'ont rien à voir
     * avec 256 blocs de terre. L'ancienne version comparait des volumes absolus,
     * si bien qu'un item cher ne bougeait pratiquement jamais de prix.
     */
    private static double echelle(String itemId) {
        ShopThresholds.Entry seuil = ShopThresholds.get(itemId);
        return Math.max(16, seuil != null ? seuil.seuil : 64);
    }

    /**
     * Pression cumulée sur la boutique : plus le serveur a vendu, plus c'est cher ;
     * plus il a racheté, moins ça l'est. Courbe continue, bornée à [0,60 ; 2,50].
     */
    private static double multiplicateurFlux(PriceEntry entry, double echelle) {
        double net = (entry.unitsSold - entry.unitsBought) / echelle;
        return borner(1.0 + 0.25 * net, 0.60, 2.50);
    }

    /**
     * Réaction à la demande des derniers jours — c'est ce facteur qui fait bouger
     * le prix tout de suite quand un item part en masse, là où le cumul seul
     * mettait des milliers d'unités à réagir.
     */
    private static double multiplicateurDemandeRecente(PriceEntry entry, double echelle) {
        return borner(1.0 + 0.50 * (entry.demandeRecente / echelle), 0.75, 2.00);
    }

    /**
     * Inflation générale : si les joueurs s'enrichissent, tout coûte plus cher.
     *
     * ⚠ Indexée sur le <b>solde médian</b> et non sur la moyenne. Avec la moyenne,
     * quatre comptes gonflés par un exploit suffisaient à clouer le multiplicateur
     * au plafond, si bien que <i>tous</i> les autres joueurs payaient le double —
     * alors qu'ils n'y étaient pour rien. C'est le même raisonnement que pour la
     * taxe de fortune : une moyenne ment dès qu'il existe des fortunes extrêmes.
     *
     * Ne mesure que l'argent <b>bancarisé</b> : les Shards physiques gardés en
     * coffre échappent au calcul. C'est acceptable, ils sont inertes — aucun achat
     * ne les accepte tant qu'ils ne sont pas redéposés.
     */
    public static double multiplicateurInflation() {
        int median = LocalEconomy.getInstance().soldeMedian();
        if (median <= 0) return 1.0;
        return borner(Math.sqrt(median / SOLDE_MEDIAN_REFERENCE), 0.80, 2.00);
    }

    /**
     * Surcoût appliqué aux joueurs fortunés, en pourcentage du prix.
     *
     * Indexé sur la <b>médiane</b> et non la moyenne : quand une poignée de
     * millionnaires côtoie des joueurs pauvres, la moyenne est tirée vers le haut
     * et taxerait tout le monde. Un joueur au niveau de la médiane ne paie rien ;
     * au-delà, la surtaxe croît jusqu'à +40 %.
     */
    public static double taxeRichesse(String pseudo) {
        LocalEconomy eco = LocalEconomy.getInstance();
        int median = eco.soldeMedian();
        if (median <= 0) return 0.0;
        double ratio = (double) eco.getBalance(pseudo) / median;
        if (ratio <= 2.0) return 0.0;               // jusqu'à 2× la médiane : exonéré
        return Math.min(0.40, 0.10 * Math.log10(ratio / 2.0) * 4.0);
    }

    private static double borner(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }

    /**
     * Décote liée à l'abondance : ce que le serveur a réellement produit
     * ({@link ProductionTracker}), et non le seul volume passé en boutique.
     *
     * L'échelle est logarithmique et rapportée au seuil de déblocage de l'item,
     * sinon un item courant et un minerai rare ne seraient pas comparables.
     * Le dénominateur a un plancher : les items chers ont un seuil minuscule
     * (1 à 4) et atteindraient le plancher de prix bien trop vite.
     *
     * Plafonnée à −30 % : le compteur de production ne fait que monter — il
     * ignore ce qui est consommé, posé ou perdu — donc sans plafond tout
     * finirait mécaniquement au prix plancher.
     */
    private static double decoteAbondance(String itemId) {
        ShopThresholds.Entry seuil = ShopThresholds.get(itemId);
        if (seuil == null) return 0.0;

        long production = ProductionTracker.get(itemId);
        double reference = Math.max(seuil.seuil, 64);
        double ratio = production / reference;
        if (ratio <= 1.0) return 0.0;

        return Math.min(DECOTE_MAX, 0.10 * Math.log10(ratio));
    }

    /**
     * Prix auquel le joueur achète l'item au serveur.
     * Recalculé à la lecture : la production évolue en continu, une valeur mise
     * en cache lors de la dernière transaction serait périmée.
     */
    public static synchronized int getPrice(String itemId) {
        PriceEntry e = getOrCreate(itemId);
        // Amortir ici aussi : sans transaction nouvelle, un pic de demande ne
        // retomberait jamais et le prix resterait figé en haut.
        amortir(e);
        return calculatePrice(itemId, e);
    }

    /** Prix payé par un joueur donné, surtaxe de fortune comprise. */
    public static synchronized int getPricePour(String itemId, String pseudo) {
        double prix = getPrice(itemId) * (1.0 + taxeRichesse(pseudo));
        return Math.max(1, (int) Math.round(prix));
    }

    /**
     * Prix auquel le serveur rachète l'item au joueur.
     *
     * Par défaut, une part du prix de vente ({@link #RATIO_RACHAT}). Un admin peut
     * imposer un montant fixe via /production ; il est alors **plafonné au prix de
     * vente courant**. Ce plafond n'est pas une précaution de principe : le prix de
     * vente bouge en permanence, et un rachat fixe finirait tôt ou tard au-dessus,
     * transformant l'aller-retour achat/revente en machine à shards.
     */
    public static synchronized int getBuybackPrice(String itemId) {
        int vente = getPrice(itemId);
        ShopThresholds.Entry seuil = ShopThresholds.get(itemId);
        if (seuil != null && seuil.prixRachat > 0)
            return Math.max(1, Math.min(seuil.prixRachat, vente));

        int rachat = Math.max(1, Math.round(vente * RATIO_RACHAT));

        // ⚠ Écart asymétrique sur les minerais. Leur prix de VENTE est calé sur le
        // rendement maximal sous Fortune III, pour qu'en acheter ne soit jamais
        // rentable. Appliquer la marge de 55 % à ce prix ferait racheter un minerai
        // de diamant 290 ◆ alors qu'il rend en moyenne 145 ◆ de diamants : la Silk
        // Touch deviendrait deux fois plus payante que la Fortune. Le serveur
        // n'achète donc que sur ce qu'il est *sûr* d'obtenir.
        Integer garanti = PrixDeriveur.valeurGarantie(itemId, ShopThresholds::prixReference);
        if (garanti != null)
            rachat = Math.min(rachat, Math.max(1, Math.round(garanti * RATIO_RACHAT)));

        return rachat;
    }

    /** Vrai si le rachat de cet item est imposé par un admin plutôt que calculé. */
    public static synchronized boolean rachatImpose(String itemId) {
        ShopThresholds.Entry seuil = ShopThresholds.get(itemId);
        return seuil != null && seuil.prixRachat > 0;
    }

    public static synchronized Map<String, PriceEntry> all() {
        return new HashMap<>(prices);
    }

    /**
     * Efface l'état du marché : flux cumulés et demande récente.
     *
     * Nécessaire après un exploit : le flux cumulé n'a <b>aucun amortissement</b>,
     * il resterait au plafond pour toujours. Ne touche ni les soldes, ni les
     * compteurs de production, ni les seuils — uniquement les compteurs de
     * transactions. Une copie du fichier est gardée avant effacement.
     */
    public static synchronized void reset() {
        SauvegardeFichier.sauver("server-shop-prices.json", "avant-purge");
        prices.clear();
        save();
        NouvelleTerreBridge.LOGGER.info("[ServerShopPriceManager] Etat du marche purge.");
    }
}
