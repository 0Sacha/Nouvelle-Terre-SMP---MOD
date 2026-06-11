package com.nouvelleterrebridge.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Gestionnaire du marché (HDV). Stockage JSON dans <gameDir>/marche.json.
 * Singleton thread-safe.
 */
public class MarketManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MarketManager instance;

    private final Path fichierMarche;
    private final List<MarketListing> annonces = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    private MarketManager() {
        fichierMarche = FabricLoader.getInstance().getGameDir().resolve("marche.json");
        load();
    }

    public static synchronized MarketManager getInstance() {
        if (instance == null) {
            instance = new MarketManager();
        }
        return instance;
    }

    /**
     * Ajoute une nouvelle annonce au marché.
     */
    public synchronized MarketListing addListing(String seller, String item, int quantity, int pricePerUnit) {
        int id = nextId.getAndIncrement();
        MarketListing listing = new MarketListing(id, seller, item, quantity, pricePerUnit);
        annonces.add(listing);
        save();
        NouvelleTerreBridge.LOGGER.info("[MarketManager] Nouvelle annonce #{} : {} vend {}x {} à {}/u",
                id, seller, quantity, item, pricePerUnit);
        return listing;
    }

    /**
     * Supprime une annonce par son ID.
     * @return true si l'annonce existait et a été supprimée, false sinon.
     */
    public synchronized boolean removeListing(int id) {
        boolean removed = annonces.removeIf(l -> l.id == id);
        if (removed) {
            save();
            NouvelleTerreBridge.LOGGER.info("[MarketManager] Annonce #{} supprimée.", id);
        }
        return removed;
    }

    /**
     * Récupère une annonce par son ID.
     */
    public synchronized Optional<MarketListing> getListing(int id) {
        return annonces.stream().filter(l -> l.id == id).findFirst();
    }

    /**
     * Retourne toutes les annonces actives.
     */
    public synchronized List<MarketListing> getAll() {
        return new ArrayList<>(annonces);
    }

    /**
     * Met à jour la quantité d'une annonce (pour achat partiel).
     * Si newQuantity <= 0, supprime l'annonce.
     */
    public synchronized void updateQuantity(int id, int newQuantity) {
        if (newQuantity <= 0) {
            removeListing(id);
            return;
        }
        annonces.stream().filter(l -> l.id == id).findFirst()
                .ifPresent(l -> { l.quantity = newQuantity; save(); });
    }

    /**
     * Trouve une annonce par vendeur + item (insensible à la casse).
     */
    public synchronized Optional<MarketListing> getBySellerAndItem(String seller, String itemId) {
        return annonces.stream()
                .filter(l -> l.seller.equalsIgnoreCase(seller) && l.item.equalsIgnoreCase(itemId))
                .findFirst();
    }

    /**
     * Retourne toutes les annonces d'un vendeur donné.
     */
    public synchronized List<MarketListing> getBySeller(String seller) {
        return annonces.stream()
                .filter(l -> l.seller.equalsIgnoreCase(seller))
                .collect(Collectors.toList());
    }

    // ── Persistance ──────────────────────────────────────────────────────────

    private void load() {
        File f = fichierMarche.toFile();
        if (!f.exists()) {
            NouvelleTerreBridge.LOGGER.info("[MarketManager] Aucun fichier marche.json trouvé, démarrage vide.");
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<MarketListing>>() {}.getType();
            List<MarketListing> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) {
                annonces.addAll(loaded);
                // Recalcule le prochain ID
                int maxId = annonces.stream().mapToInt(l -> l.id).max().orElse(0);
                nextId.set(maxId + 1);
                NouvelleTerreBridge.LOGGER.info("[MarketManager] {} annonce(s) chargée(s).", annonces.size());
            }
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[MarketManager] Erreur de lecture de marche.json : {}", e.getMessage());
        }
    }

    private void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(fichierMarche.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(annonces, writer);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[MarketManager] Erreur de sauvegarde de marche.json : {}", e.getMessage());
        }
    }
}
