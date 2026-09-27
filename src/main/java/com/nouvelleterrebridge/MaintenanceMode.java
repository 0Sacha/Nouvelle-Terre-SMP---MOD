package com.nouvelleterrebridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mode maintenance : seuls les op peuvent se connecter.
 *
 * <p><b>Pourquoi pas la whitelist.</b> Elle contient déjà tous les joueurs du
 * serveur ; l'activer n'isolerait donc personne, et la vider pour la reconstituer
 * ensuite ferait courir le risque de perdre la liste. Le mode maintenance est une
 * couche indépendante : {@code whitelist.json} n'est jamais touché et continue de
 * filtrer normalement.
 *
 * <p><b>Pourquoi l'état est écrit sur disque.</b> Un drapeau en mémoire seule
 * rouvrirait le serveur à tout le monde au redémarrage — or redémarrer est
 * exactement ce qu'on fait pendant une maintenance (déploiement d'une version,
 * migration des prix). Le serveur redémarre donc fermé tant qu'on n'a pas
 * explicitement rouvert.
 */
public final class MaintenanceMode {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NOM_FICHIER = "nouvelle-terre-maintenance.json";

    public static final String MESSAGE_DEFAUT = "Serveur en maintenance — retour très bientôt.";

    /** Forme sérialisée : un objet, pour pouvoir enrichir sans casser le fichier. */
    private static class Etat {
        boolean actif = false;
        String message = MESSAGE_DEFAUT;
    }

    private static Etat etat = new Etat();

    private MaintenanceMode() {}

    private static Path chemin() {
        return FMLPaths.GAMEDIR.get().resolve(NOM_FICHIER);
    }

    public static void load() {
        Path p = chemin();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Etat lu = GSON.fromJson(r, Etat.class);
            if (lu != null) etat = lu;
            if (etat.message == null || etat.message.isBlank()) etat.message = MESSAGE_DEFAUT;
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[Maintenance] Lecture impossible : {}", e.getMessage());
        }
        if (etat.actif)
            NouvelleTerreBridge.LOGGER.warn("[Maintenance] Le serveur démarre EN MAINTENANCE — seuls les op peuvent se connecter.");
    }

    private static void save() {
        try (Writer w = Files.newBufferedWriter(chemin(), StandardCharsets.UTF_8)) {
            GSON.toJson(etat, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[Maintenance] Écriture impossible : {}", e.getMessage());
        }
    }

    public static boolean estActif()   { return etat.actif; }
    public static String  getMessage() { return etat.message; }

    /** @param message message affiché aux joueurs refusés, null pour garder le précédent */
    public static void activer(String message) {
        etat.actif = true;
        if (message != null && !message.isBlank()) etat.message = message;
        save();
    }

    public static void desactiver() {
        etat.actif = false;
        save();
    }
}
