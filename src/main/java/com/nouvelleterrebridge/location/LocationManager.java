package com.nouvelleterrebridge.location;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.TransactionLog;
import com.nouvelleterrebridge.item.ShardItem;
import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.market.ItemComponentCodec;
import com.nouvelleterrebridge.service.ServiceManager;
import com.nouvelleterrebridge.util.FichierSur;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Location d'objets entre joueurs.
 *
 * <p>Le propriétaire dépose un objet dans l'interface avec un loyer par jour et
 * une caution. Le locataire paie le premier jour et la caution, reçoit l'objet
 * <b>étiqueté</b>, puis le loyer est prélevé tout seul chaque jour réel. Il rend
 * l'objet depuis l'interface et récupère sa caution.
 *
 * <p><b>Le verrou.</b> L'étiquette ({@link #CLE}, dans le composant
 * {@code custom_data}) porte le numéro du contrat et suit l'objet partout —
 * inventaire, coffre, sol. Tant que le contrat est à jour, l'objet s'utilise
 * normalement. Dès qu'un loyer n'a pas pu être prélevé, {@link LocationVerrou}
 * annule toute action faite avec lui.
 *
 * <p><b>La caution.</b> Le verrou ne peut pas tout couvrir : un objet d'un autre
 * mod peut avoir ses propres actions, et un objet caché dans un coffre ne peut
 * pas être repris. La caution, retenue sur {@code $Sequestre}, garantit que
 * tricher coûte de l'argent : elle part au propriétaire si l'objet ne revient pas.
 *
 * <p><b>L'objet rendu est l'objet réel</b>, nettoyé de son étiquette — et non une
 * copie de l'original. Reconstituer l'original aurait permis de dupliquer : vider
 * un contenant loué puis le « rendre » plein. Pour la même raison les contenants
 * garnis ne se louent pas, et l'enclume comme la meule refusent un objet loué.
 */
public final class LocationManager {

    /** Clé de l'étiquette dans {@code custom_data} : numéro du contrat. */
    public static final String CLE = "nt_location";

    public static final long JOUR_MS = 24L * 60 * 60 * 1000;
    /** Délai laissé au locataire pour régulariser ou rendre avant la reprise. */
    public static final long GRACE_MS = JOUR_MS;
    /** Au-delà, l'objet est considéré perdu et la caution part au propriétaire. */
    public static final long ABANDON_MS = 7 * JOUR_MS;

    public static final int PRIX_MAX      = 1_000_000;
    public static final int CAUTION_MAX   = 10_000_000;
    public static final int OFFRES_MAX    = 20;

    public static final int ETAT_OK     = 0;
    /** Loyer impayé ou contrat résilié : objet verrouillé, à rendre. */
    public static final int ETAT_BLOQUE = 1;
    /** Jamais rendu : caution versée au propriétaire, l'objet reste verrouillé. */
    public static final int ETAT_PERDU  = 2;

    public static class Offre {
        public int id;
        public String proprietaire;
        public String item;
        public String nbt;
        public int prixJour;
        public int caution;
    }

    public static class Contrat {
        public int id;
        public String proprietaire;
        public String locataire;
        public String item;
        public int prixJour;
        public int caution;
        public long echeance;
        /** 0 = à jour ; sinon instant où l'objet s'est verrouillé. */
        public long bloqueDepuis;
        /** Le propriétaire veut récupérer son bien : plus de loyer dû, objet à rendre. */
        public boolean resilie;
        public boolean perdu;

        public int etat() {
            return perdu ? ETAT_PERDU : (bloqueDepuis > 0 ? ETAT_BLOQUE : ETAT_OK);
        }
    }

    /** Objet revenu de location, en attente d'être repris par son propriétaire. */
    public static class Retour {
        public int id;
        public String proprietaire;
        public String item;
        public String nbt;
        public String origine;
    }

    private static class Donnees {
        int prochainId = 1;
        List<Offre> offres = new ArrayList<>();
        List<Contrat> contrats = new ArrayList<>();
        List<Retour> retours = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FICHIER = FMLPaths.GAMEDIR.get().resolve("locations.json");
    private static final LocationManager INSTANCE = new LocationManager();

    private Donnees donnees = new Donnees();
    /**
     * Contrats dont l'objet est utilisable, pour le test du verrou — appelé à
     * chaque clic d'un joueur tenant un objet loué, donc sans verrou ni parcours.
     */
    private final Map<Integer, Boolean> utilisables = new ConcurrentHashMap<>();

    private LocationManager() {}

    public static LocationManager get() { return INSTANCE; }

    // ── Persistance ───────────────────────────────────────────────────────────

    public synchronized void charger() {
        if (!Files.exists(FICHIER)) return;
        try (Reader r = new InputStreamReader(Files.newInputStream(FICHIER), StandardCharsets.UTF_8)) {
            Donnees d = GSON.fromJson(r, Donnees.class);
            if (d != null) {
                if (d.offres == null) d.offres = new ArrayList<>();
                if (d.contrats == null) d.contrats = new ArrayList<>();
                if (d.retours == null) d.retours = new ArrayList<>();
                donnees = d;
            }
            recalculerVerrous();
            NouvelleTerreBridge.LOGGER.info("[Location] {} offre(s), {} contrat(s) chargé(s).",
                donnees.offres.size(), donnees.contrats.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[Location] Erreur lecture : {}", e.getMessage());
        }
    }

    private void sauver() {
        recalculerVerrous();
        try {
            FichierSur.ecrire(FICHIER, GSON.toJson(donnees));
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[Location] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    private void recalculerVerrous() {
        utilisables.clear();
        for (Contrat c : donnees.contrats)
            if (c.etat() == ETAT_OK) utilisables.put(c.id, Boolean.TRUE);
    }

    // ── Étiquette ─────────────────────────────────────────────────────────────

    /** Numéro de contrat porté par la pile, ou 0 si elle n'est pas louée. */
    public static int contratDe(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains(CLE)) return 0;
        return cd.copyTag().getInt(CLE);
    }

    /**
     * Vrai si la pile est un objet loué qui ne doit plus servir : loyer impayé,
     * contrat résilié, objet déclaré perdu, ou contrat inconnu.
     */
    public boolean estVerrouille(ItemStack stack) {
        int id = contratDe(stack);
        return id != 0 && !utilisables.containsKey(id);
    }

    private static void etiqueter(ItemStack stack, Contrat c) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(CLE, c.id));
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        lore = lore.withLineAdded(Component.literal("§6Objet loué §7— propriétaire : §f" + c.proprietaire));
        lore = lore.withLineAdded(Component.literal("§7À rendre depuis le Parchemin → §fLocation"));
        stack.set(DataComponents.LORE, lore);
    }

    /** Retire l'étiquette et les deux lignes de description ajoutées à la location. */
    private static void nettoyer(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            var tag = cd.copyTag();
            tag.remove(CLE);
            if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
            else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            List<Component> lignes = new ArrayList<>(lore.lines());
            // Nos deux lignes sont toujours les dernières
            for (int i = 0; i < 2 && !lignes.isEmpty(); i++) lignes.remove(lignes.size() - 1);
            if (lignes.isEmpty()) stack.remove(DataComponents.LORE);
            else stack.set(DataComponents.LORE, new ItemLore(lignes));
        }
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    public synchronized List<Offre> offres() { return new ArrayList<>(donnees.offres); }

    public synchronized List<Contrat> contratsDe(String pseudo) {
        List<Contrat> out = new ArrayList<>();
        for (Contrat c : donnees.contrats) {
            // Un contrat perdu ne concerne plus le locataire : la caution a soldé le compte
            if (c.locataire.equalsIgnoreCase(pseudo) && !c.perdu) out.add(c);
            else if (c.proprietaire.equalsIgnoreCase(pseudo)) out.add(c);
        }
        return out;
    }

    public synchronized List<Retour> retoursDe(String pseudo) {
        List<Retour> out = new ArrayList<>();
        for (Retour r : donnees.retours) if (r.proprietaire.equalsIgnoreCase(pseudo)) out.add(r);
        return out;
    }

    // ── Propriétaire ──────────────────────────────────────────────────────────

    /** Motif pour lequel une pile ne peut pas être proposée, ou null si elle convient. */
    public static String refusDeLouer(ItemStack s) {
        if (s.isEmpty()) return "§cEmplacement vide.";
        if (s.getMaxStackSize() != 1)
            return "§cSeuls les objets non empilables se louent (outils, armes, armures…).";
        if (contratDe(s) != 0) return "§cCet objet est lui-même loué : il ne t'appartient pas.";
        if (s.getItem() instanceof ShardItem || s.is(NouvelleTerreBridge.PARCHEMIN.get()))
            return "§cCet objet ne se loue pas.";
        // Un contenant garni reviendrait vidé : le propriétaire perdrait son contenu
        // sans que la caution, fixée sur le contenant, le couvre.
        ItemContainerContents contenu = s.get(DataComponents.CONTAINER);
        if (contenu != null && contenu.nonEmptyItems().iterator().hasNext())
            return "§cVide ce contenant avant de le proposer.";
        var sac = s.get(DataComponents.BUNDLE_CONTENTS);
        if (sac != null && !sac.isEmpty()) return "§cVide ce sac avant de le proposer.";
        return null;
    }

    public synchronized String proposer(ServerPlayer joueur, int slot, int prixJour, int caution) {
        String pseudo = joueur.getName().getString();
        Inventory inv = joueur.getInventory();
        if (slot < 0 || slot >= inv.items.size()) return "§cEmplacement invalide.";
        ItemStack s = inv.items.get(slot);
        String refus = refusDeLouer(s);
        if (refus != null) return refus;

        if (prixJour <= 0 || prixJour > PRIX_MAX) return "§cLoyer invalide (1 à " + PRIX_MAX + " ◆ par jour).";
        // Caution d'au moins un jour : c'est elle qui paie le loyer resté dû
        if (caution < prixJour) return "§cLa caution doit valoir au moins un jour de loyer (" + prixJour + " ◆).";
        if (caution > CAUTION_MAX) return "§cCaution trop élevée.";

        long miennes = donnees.offres.stream().filter(o -> o.proprietaire.equalsIgnoreCase(pseudo)).count();
        if (miennes >= OFFRES_MAX) return "§cLimite atteinte : " + OFFRES_MAX + " offres en même temps.";

        Offre o = new Offre();
        o.id = donnees.prochainId++;
        o.proprietaire = pseudo;
        o.item = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        o.nbt = ItemComponentCodec.capturer(s, joueur.getServer().registryAccess());
        o.prixJour = prixJour;
        o.caution = caution;
        inv.items.set(slot, ItemStack.EMPTY);
        donnees.offres.add(o);
        sauver();
        return null;
    }

    public synchronized String retirerOffre(ServerPlayer joueur, int offreId) {
        Offre o = offre(offreId);
        if (o == null) return "§cCette offre n'existe plus.";
        if (!o.proprietaire.equalsIgnoreCase(joueur.getName().getString())) return "§cCette offre n'est pas la tienne.";
        if (joueur.getInventory().getFreeSlot() < 0) return "§cLibère une place dans ton inventaire.";

        ItemStack s = reconstruire(o.item, o.nbt, joueur.getServer());
        if (s.isEmpty()) return "§cObjet illisible — préviens un administrateur.";
        donnees.offres.remove(o);
        sauver();
        joueur.getInventory().add(s);
        return null;
    }

    /** Le propriétaire reprend son bien : plus de loyer, le locataire a un jour pour rendre. */
    public synchronized String resilier(ServerPlayer joueur, int contratId) {
        Contrat c = contrat(contratId);
        if (c == null || c.perdu) return "§cContrat introuvable.";
        if (!c.proprietaire.equalsIgnoreCase(joueur.getName().getString())) return "§cCe contrat n'est pas le tien.";
        if (c.resilie) return "§eDéjà résilié — en attente du retour de l'objet.";
        c.resilie = true;
        // L'objet reste utilisable jusqu'au terme du jour déjà payé
        sauver();
        prevenir(joueur.getServer(), c.locataire, "§e[Location] §f" + c.proprietaire
            + " §ereprend son §f" + nom(c.item) + "§e. Rends-le depuis le Parchemin → Location avant la fin du jour payé.");
        return null;
    }

    public synchronized String recuperer(ServerPlayer joueur, int retourId) {
        Retour r = null;
        for (Retour x : donnees.retours) if (x.id == retourId) r = x;
        if (r == null) return "§cObjet introuvable.";
        if (!r.proprietaire.equalsIgnoreCase(joueur.getName().getString())) return "§cCet objet n'est pas à toi.";
        if (joueur.getInventory().getFreeSlot() < 0) return "§cLibère une place dans ton inventaire.";

        ItemStack s = reconstruire(r.item, r.nbt, joueur.getServer());
        if (s.isEmpty()) return "§cObjet illisible — préviens un administrateur.";
        donnees.retours.remove(r);
        sauver();
        joueur.getInventory().add(s);
        return null;
    }

    // ── Locataire ─────────────────────────────────────────────────────────────

    public synchronized String louer(ServerPlayer joueur, int offreId) {
        String pseudo = joueur.getName().getString();
        Offre o = offre(offreId);
        if (o == null) return "§cCette offre n'existe plus.";
        if (o.proprietaire.equalsIgnoreCase(pseudo)) return "§cC'est ta propre offre.";
        if (joueur.getInventory().getFreeSlot() < 0) return "§cLibère une place dans ton inventaire.";

        LocalEconomy eco = LocalEconomy.getInstance();
        long total = (long) o.prixJour + o.caution;
        if (eco.getBalance(pseudo) < total)
            return "§cSolde insuffisant — il faut §f" + total + " ◆ §c(premier jour + caution).";

        ItemStack s = reconstruire(o.item, o.nbt, joueur.getServer());
        if (s.isEmpty()) return "§cObjet illisible — préviens un administrateur.";

        Contrat c = new Contrat();
        c.id = donnees.prochainId++;
        c.proprietaire = o.proprietaire;
        c.locataire = pseudo;
        c.item = o.item;
        c.prixJour = o.prixJour;
        c.caution = o.caution;
        c.echeance = System.currentTimeMillis() + JOUR_MS;

        // Loyer du premier jour au propriétaire, caution retenue par le séquestre
        eco.removeShards(pseudo, (int) total);
        eco.addShards(o.proprietaire, o.prixJour, "Location : " + nom(o.item) + " (à " + pseudo + ")");
        eco.addShards(ServiceManager.COMPTE_SEQUESTRE, o.caution, "Caution de location");
        TransactionLog.log(pseudo, TransactionLog.TYPE_BUY,
            "Location : " + nom(o.item) + " (1er jour + caution)", (int) total);

        etiqueter(s, c);
        donnees.offres.remove(o);
        donnees.contrats.add(c);
        sauver();
        joueur.getInventory().add(s);

        prevenir(joueur.getServer(), o.proprietaire, "§a[Location] §f" + pseudo + " §aloue ton §f"
            + nom(o.item) + " §a— §f" + o.prixJour + " ◆ §apar jour.");
        return null;
    }

    /** Le locataire rend l'objet : il doit l'avoir sur lui. */
    public synchronized String rendre(ServerPlayer joueur, int contratId) {
        Contrat c = contrat(contratId);
        if (c == null || c.perdu) return "§cContrat introuvable.";
        if (!c.locataire.equalsIgnoreCase(joueur.getName().getString())) return "§cCe contrat n'est pas le tien.";

        ItemStack pris = prendre(joueur, c.id);
        if (pris.isEmpty()) return "§cL'objet doit être dans ton inventaire pour être rendu.";

        cloreAvecRetour(joueur.getServer(), c, pris, "Rendu par " + c.locataire);
        return null;
    }

    /** L'objet est cassé ou introuvable : le locataire abandonne sa caution. */
    public synchronized String declarerPerdu(ServerPlayer joueur, int contratId) {
        Contrat c = contrat(contratId);
        if (c == null || c.perdu) return "§cContrat introuvable.";
        if (!c.locataire.equalsIgnoreCase(joueur.getName().getString())) return "§cCe contrat n'est pas le tien.";
        marquerPerdu(joueur.getServer(), c);
        sauver();
        return null;
    }

    // ── Règlements ────────────────────────────────────────────────────────────

    /**
     * Clôt le contrat avec l'objet récupéré. La caution revient au locataire,
     * moins un jour de loyer s'il était en impayé.
     */
    private void cloreAvecRetour(MinecraftServer server, Contrat c, ItemStack objet, String origine) {
        nettoyer(objet);
        Retour r = new Retour();
        r.id = donnees.prochainId++;
        r.proprietaire = c.proprietaire;
        r.item = BuiltInRegistries.ITEM.getKey(objet.getItem()).toString();
        r.nbt = ItemComponentCodec.capturer(objet, server.registryAccess());
        r.origine = origine;
        donnees.retours.add(r);

        LocalEconomy eco = LocalEconomy.getInstance();
        boolean impaye = c.bloqueDepuis > 0 && !c.resilie;
        int du = impaye ? Math.min(c.prixJour, c.caution) : 0;
        int rendu = c.caution - du;
        eco.forceDeduct(ServiceManager.COMPTE_SEQUESTRE, c.caution);
        if (du > 0) eco.addShards(c.proprietaire, du, "Location — loyer dû : " + nom(c.item));
        if (rendu > 0) eco.addShards(c.locataire, rendu, "Location — caution rendue : " + nom(c.item));

        donnees.contrats.remove(c);
        sauver();

        prevenir(server, c.proprietaire, "§a[Location] §fTon " + nom(c.item)
            + " §aest revenu. Récupère-le dans le Parchemin → Location.");
        prevenir(server, c.locataire, "§a[Location] §f" + nom(c.item) + " §arendu — caution : §f+"
            + rendu + " ◆" + (du > 0 ? " §7(" + du + " ◆ de loyer dû retenus)" : "") + "§a.");
    }

    /** La caution part au propriétaire ; le contrat reste, pour reprendre l'objet s'il réapparaît. */
    private void marquerPerdu(MinecraftServer server, Contrat c) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.forceDeduct(ServiceManager.COMPTE_SEQUESTRE, c.caution);
        eco.addShards(c.proprietaire, c.caution, "Location — caution saisie : " + nom(c.item));
        c.perdu = true;
        prevenir(server, c.proprietaire, "§e[Location] §fTon " + nom(c.item)
            + " §en'a pas été rendu par §f" + c.locataire + "§e. Sa caution de §f" + c.caution + " ◆ §et'est versée.");
        prevenir(server, c.locataire, "§c[Location] §f" + nom(c.item)
            + " §cnon rendu — ta caution de §f" + c.caution + " ◆ §cest perdue.");
    }

    // ── Passage périodique (chaque minute) ────────────────────────────────────

    public synchronized void tick(MinecraftServer server) {
        long maintenant = System.currentTimeMillis();
        LocalEconomy eco = LocalEconomy.getInstance();
        boolean change = false;

        for (Contrat c : new ArrayList<>(donnees.contrats)) {
            if (c.perdu) {
                // Objet déclaré perdu qui refait surface chez quelqu'un : il rentre
                change |= reprendreChezQuiconque(server, c);
                continue;
            }

            if (c.bloqueDepuis == 0) {
                if (maintenant < c.echeance) continue;
                if (c.resilie) {
                    c.bloqueDepuis = maintenant;
                    change = true;
                    prevenir(server, c.locataire, "§c[Location] §fLe contrat de ton " + nom(c.item)
                        + " §cest terminé : l'objet est verrouillé. Rends-le depuis le Parchemin → Location.");
                } else if (eco.transfer(c.locataire, c.proprietaire, c.prixJour)) {
                    // Un seul jour prélevé même après une longue coupure du serveur
                    c.echeance = maintenant + JOUR_MS;
                    change = true;
                } else {
                    c.bloqueDepuis = maintenant;
                    change = true;
                    prevenir(server, c.locataire, "§c[Location] §fLoyer impayé pour ton " + nom(c.item)
                        + " §c: l'objet est verrouillé. Renfloue ton compte ou rends-le sous 24 h, sinon il sera repris.");
                    prevenir(server, c.proprietaire, "§e[Location] §f" + c.locataire
                        + " §en'a pas pu payer le loyer de ton §f" + nom(c.item) + "§e. L'objet est verrouillé.");
                }
                continue;
            }

            // Objet verrouillé : un impayé peut encore être régularisé
            if (!c.resilie && eco.transfer(c.locataire, c.proprietaire, c.prixJour)) {
                c.bloqueDepuis = 0;
                c.echeance = maintenant + JOUR_MS;
                change = true;
                prevenir(server, c.locataire, "§a[Location] §fLoyer réglé — ton " + nom(c.item) + " §aest de nouveau utilisable.");
                continue;
            }

            long retard = maintenant - c.bloqueDepuis;
            if (retard < GRACE_MS) continue;

            ServerPlayer locataire = server.getPlayerList().getPlayerByName(c.locataire);
            ItemStack pris = locataire == null ? ItemStack.EMPTY : prendre(locataire, c.id);
            if (!pris.isEmpty()) {
                cloreAvecRetour(server, c, pris, "Repris à " + c.locataire);
            } else if (retard >= ABANDON_MS) {
                marquerPerdu(server, c);
                change = true;
            }
        }
        if (change) sauver();
    }

    private boolean reprendreChezQuiconque(MinecraftServer server, Contrat c) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ItemStack pris = prendre(p, c.id);
            if (pris.isEmpty()) continue;
            nettoyer(pris);
            Retour r = new Retour();
            r.id = donnees.prochainId++;
            r.proprietaire = c.proprietaire;
            r.item = BuiltInRegistries.ITEM.getKey(pris.getItem()).toString();
            r.nbt = ItemComponentCodec.capturer(pris, server.registryAccess());
            r.origine = "Retrouvé chez " + p.getName().getString();
            donnees.retours.add(r);
            donnees.contrats.remove(c);
            p.sendSystemMessage(Component.literal("§c[Location] §fCet objet n'avait jamais été rendu : il retourne à §f"
                + c.proprietaire + "§c."));
            prevenir(server, c.proprietaire, "§a[Location] §fTon " + nom(c.item)
                + " §aa été retrouvé. Récupère-le dans le Parchemin → Location.");
            return true;
        }
        return false;
    }

    // ── Outils ────────────────────────────────────────────────────────────────

    private Offre offre(int id) {
        for (Offre o : donnees.offres) if (o.id == id) return o;
        return null;
    }

    private Contrat contrat(int id) {
        for (Contrat c : donnees.contrats) if (c.id == id) return c;
        return null;
    }

    /** Retire de l'inventaire (sac, armure, main secondaire) la pile du contrat. */
    private static ItemStack prendre(ServerPlayer joueur, int contratId) {
        Inventory inv = joueur.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (contratDe(s) == contratId) {
                inv.setItem(i, ItemStack.EMPTY);
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack reconstruire(String itemId, String nbt, MinecraftServer server) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        // Un échec de lecture rendrait un objet vierge à la place d'un objet enchanté :
        // mieux vaut refuser l'opération et garder l'objet en dépôt.
        if (!ItemComponentCodec.appliquer(s, nbt, server.registryAccess())) return ItemStack.EMPTY;
        return s;
    }

    private static String nom(String itemId) {
        return FrenchItemNames.toDisplay(itemId);
    }

    private static void prevenir(MinecraftServer server, String pseudo, String message) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(pseudo);
        if (p != null) {
            p.sendSystemMessage(Component.literal(message));
            NouvelleTerreBridge.sendBalanceToPlayer(p);
        }
    }

    /** Pour le parcours des contrats par {@link LocationVerrou} (armures verrouillées). */
    public synchronized boolean aDesContratsBloques() {
        Iterator<Contrat> it = donnees.contrats.iterator();
        while (it.hasNext()) if (it.next().etat() != ETAT_OK) return true;
        return false;
    }
}
