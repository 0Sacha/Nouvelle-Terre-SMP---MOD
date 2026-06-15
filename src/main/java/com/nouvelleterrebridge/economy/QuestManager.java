package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class QuestManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("quetes.json");

    private static final int SOLO_POOL_SIZE  = 5;
    private static final int GROUP_POOL_SIZE = 3;
    private static final long REFRESH_MS     = 24L * 3600 * 1000; // 24 h

    // ── Structures de données ─────────────────────────────────────────────────

    public static class ActiveQuest {
        public int         questId;
        public Quest       snapshot;          // copie de la quête au moment de l'acceptation
        public int         progress;
        public boolean     turnedIn;          // DELIVERY: items consommés, en attente de claim
        public List<String> groupParticipants = new ArrayList<>();
        public long        acceptedAt;
    }

    public static class PendingReward {
        public String questLabel;
        public String rewardItem;
        public int    rewardItemQty;
        public long   completedAt;
    }

    private static class PlayerData {
        long                lastRefresh    = 0;
        int                 totalCompleted = 0;
        List<Quest>         available      = new ArrayList<>();
        List<ActiveQuest>   active         = new ArrayList<>();
        List<PendingReward> pendingRewards = new ArrayList<>();
    }

    private static Map<String, PlayerData>      players      = new HashMap<>();
    private static List<Quest>                  globalGroup  = new ArrayList<>();

    // Acceptations en attente pour les quêtes groupe (in-memory)
    private static final Map<Integer, List<String>> GROUP_PENDING = new HashMap<>();

    // ── Persistance ───────────────────────────────────────────────────────────

    public static synchronized void load() {
        loadData();
    }

    @SuppressWarnings("unchecked")
    private static void loadData() {
        File f = FILE.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Map<String, Object> root = GSON.fromJson(r, Map.class);
            if (root == null) return;
            Object pd = root.get("players");
            if (pd != null) {
                Type t = new TypeToken<Map<String, PlayerData>>(){}.getType();
                String json = GSON.toJson(pd);
                Map<String, PlayerData> loaded = GSON.fromJson(json, t);
                if (loaded != null) players = loaded;
            }
            Object gg = root.get("globalGroup");
            if (gg != null) {
                Type t = new TypeToken<List<Quest>>(){}.getType();
                List<Quest> loaded = GSON.fromJson(GSON.toJson(gg), t);
                if (loaded != null) globalGroup = loaded;
            }
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[QuestManager] Erreur lecture : {}", e.getMessage());
        }
    }

    private static void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("players", players);
            root.put("globalGroup", globalGroup);
            GSON.toJson(root, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[QuestManager] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    private static PlayerData data(String player) {
        return players.computeIfAbsent(player.toLowerCase(), k -> new PlayerData());
    }

    // ── Pool management ───────────────────────────────────────────────────────

    /** Appel à la connexion ou lors du level-up. Régénère si le pool est expiré. */
    public static synchronized void refreshPlayerPool(String player, MinecraftServer server) {
        PlayerData d   = data(player);
        long       now = System.currentTimeMillis();
        if (now - d.lastRefresh < REFRESH_MS) return;

        int level      = PlayerLevelManager.getLevel(player);
        d.available    = QuestGenerator.generateSolo(level, SOLO_POOL_SIZE);
        d.lastRefresh  = now;

        // Régénérer le pool groupe si besoin
        if (now - globalGroupLastRefresh >= REFRESH_MS) {
            globalGroup          = QuestGenerator.generateGroup(level, GROUP_POOL_SIZE);
            globalGroupLastRefresh = now;
            GROUP_PENDING.clear();
        }

        save();

        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(player);
            if (sp != null) NouvelleTerreBridge.sendQuestOpen(sp);
        });
    }

    private static long globalGroupLastRefresh = 0;

    /** Forcé par /quetes refresh (admin). */
    public static synchronized void forceRefresh(String player, MinecraftServer server) {
        PlayerData d  = data(player);
        d.lastRefresh = 0;
        globalGroupLastRefresh = 0;
        refreshPlayerPool(player, server);
    }

    // ── API publique ──────────────────────────────────────────────────────────

    public static synchronized List<Quest> getAvailable(String player) {
        List<Quest> list = new ArrayList<>(data(player).available);
        long now = System.currentTimeMillis();
        list.removeIf(q -> q.expiresAt > 0 && q.expiresAt < now);
        // Ajouter les quêtes groupe globales éligibles
        int level = PlayerLevelManager.getLevel(player);
        for (Quest gq : globalGroup) {
            if (gq.expiresAt > 0 && gq.expiresAt < now) continue;
            if (gq.levelRequired > level) continue;
            list.add(gq);
        }
        return list;
    }

    public static synchronized List<ActiveQuest> getActive(String player) {
        return new ArrayList<>(data(player).active);
    }

    public static synchronized List<PendingReward> getPending(String player) {
        return new ArrayList<>(data(player).pendingRewards);
    }

    public static synchronized Map<Integer, Integer> getGroupPendingCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        GROUP_PENDING.forEach((id, list) -> counts.put(id, list.size()));
        return counts;
    }

    /** Accepte ou rejoint une quête. @return null=OK, sinon message d'erreur. */
    public static synchronized String accept(String player, int questId, MinecraftServer server) {
        Quest q = findAvailable(player, questId);
        if (q == null) return "Quête introuvable ou expirée.";

        PlayerData d = data(player);
        if (d.active.stream().anyMatch(a -> a.questId == questId))
            return "Quête déjà acceptée.";
        if (d.pendingRewards.stream().anyMatch(p -> p.questLabel.equals(q.label)))
            return "Une récompense identique est déjà en attente.";

        // Coût en shards
        if (q.costShards > 0) {
            if (LocalEconomy.getInstance().getBalance(player) < q.costShards)
                return "Shards insuffisants (" + q.costShards + " ◆ requis).";
            LocalEconomy.getInstance().removeShards(player, q.costShards);
        }

        if (q.maxPlayers > 1) {
            // Quête groupe
            List<String> pending = GROUP_PENDING.computeIfAbsent(questId, k -> new ArrayList<>());
            if (pending.contains(player.toLowerCase()))
                return "Vous avez déjà rejoint cette quête.";
            pending.add(player.toLowerCase());

            if (pending.size() >= q.maxPlayers) {
                // Activer pour tous les participants
                List<String> participants = new ArrayList<>(pending);
                GROUP_PENDING.remove(questId);
                for (String p : participants) {
                    activateQuest(p, q, participants);
                    server.execute(() -> {
                        ServerPlayerEntity sp = server.getPlayerManager().getPlayer(p);
                        if (sp != null) {
                            NouvelleTerreBridge.sendQuestOpen(sp);
                            sp.sendMessage(net.minecraft.text.Text.literal(
                                "§a[Quêtes] La quête groupe \"" + q.label + "\" est maintenant active !"), false);
                        }
                    });
                }
            }
            save();
            return null;
        }

        activateQuest(player, q, List.of(player));
        save();
        return null;
    }

    private static void activateQuest(String player, Quest q, List<String> participants) {
        ActiveQuest aq       = new ActiveQuest();
        aq.questId           = q.id;
        aq.snapshot          = q;
        aq.progress          = 0;
        aq.turnedIn          = false;
        aq.groupParticipants = new ArrayList<>(participants);
        aq.acceptedAt        = System.currentTimeMillis();
        data(player).active.add(aq);
    }

    /** Annule une quête active. @return null=OK, sinon erreur. */
    public static synchronized String cancel(String player, int questId) {
        PlayerData d = data(player);
        ActiveQuest aq = d.active.stream().filter(a -> a.questId == questId).findFirst().orElse(null);
        if (aq == null) return "Quête non trouvée dans vos quêtes actives.";
        d.active.remove(aq);
        save();
        return null;
    }

    /** Annule une récompense en attente. @return null=OK, sinon erreur. */
    public static synchronized String cancelPending(String player, int index) {
        PlayerData d = data(player);
        if (index < 0 || index >= d.pendingRewards.size()) return "Index invalide.";
        d.pendingRewards.remove(index);
        save();
        return null;
    }

    /**
     * Valide et réclame une quête (KILL/HARVEST) ou remet les items (DELIVERY).
     * @return null=OK, sinon message d'erreur.
     */
    public static synchronized String claim(String player, int questId, ServerPlayerEntity serverPlayer, MinecraftServer server) {
        PlayerData  d  = data(player);
        ActiveQuest aq = d.active.stream().filter(a -> a.questId == questId).findFirst().orElse(null);
        if (aq == null || aq.snapshot == null) return "Quête non trouvée.";

        Quest q = aq.snapshot;

        if ("DELIVERY".equals(q.type)) {
            // Vérifier et consommer les items dans l'inventaire
            if (aq.turnedIn) return "Objets déjà remis. Allez dans 'À Réclamer'.";
            String itemErr = consumeItems(serverPlayer, q.target, q.quantity);
            if (itemErr != null) return itemErr;
            aq.turnedIn = true;

            if ("SHARDS".equals(q.rewardType)) {
                giveReward(player, q, server);
                d.active.remove(aq);
            } else {
                d.pendingRewards.add(buildPending(q));
                d.active.remove(aq);
            }
            d.totalCompleted++;
            PlayerLevelManager.addXp(player, q.rewardXp, server);
            save();
            return null;
        }

        // KILL / HARVEST
        if (aq.progress < q.quantity) return "Objectif non atteint (" + aq.progress + "/" + q.quantity + ").";

        if ("SHARDS".equals(q.rewardType)) {
            giveReward(player, q, server);
            d.active.remove(aq);
        } else {
            d.pendingRewards.add(buildPending(q));
            d.active.remove(aq);
        }
        d.totalCompleted++;
        PlayerLevelManager.addXp(player, q.rewardXp, server);
        save();
        return null;
    }

    /** Récupère une récompense item depuis l'onglet "À Réclamer". */
    public static synchronized String collectReward(String player, int index, ServerPlayerEntity serverPlayer) {
        PlayerData d = data(player);
        if (index < 0 || index >= d.pendingRewards.size()) return "Récompense introuvable.";
        PendingReward pr = d.pendingRewards.get(index);

        ItemStack reward = new ItemStack(Registries.ITEM.get(new Identifier(pr.rewardItem)), pr.rewardItemQty);
        serverPlayer.getInventory().insertStack(reward);
        if (!reward.isEmpty()) {
            serverPlayer.dropItem(reward, false);
        }
        d.pendingRewards.remove(index);
        save();
        return null;
    }

    // ── Progression passive ───────────────────────────────────────────────────

    public static synchronized void onMobKilled(String player, String entityTypeId) {
        advanceActive(player, "KILL", entityTypeId, 1);
    }

    public static synchronized void onItemHarvested(String player, String itemId, int count) {
        advanceActive(player, "HARVEST", itemId, count);
    }

    private static void advanceActive(String player, String type, String target, int amount) {
        PlayerData d = data(player);
        boolean changed = false;
        for (ActiveQuest aq : d.active) {
            if (aq.snapshot == null) continue;
            Quest q = aq.snapshot;
            if (!type.equals(q.type))       continue;
            if (!q.target.equals(target))   continue;
            if (aq.progress >= q.quantity)  continue;

            if (q.maxPlayers > 1) {
                // Quête groupe : avancer TOUS les participants
                for (String p : aq.groupParticipants) {
                    PlayerData pd = data(p);
                    pd.active.stream()
                        .filter(a -> a.questId == aq.questId)
                        .findFirst()
                        .ifPresent(a -> a.progress = Math.min(a.progress + amount, q.quantity));
                }
            } else {
                aq.progress = Math.min(aq.progress + amount, q.quantity);
            }
            changed = true;
        }
        if (changed) save();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Quest findAvailable(String player, int questId) {
        for (Quest q : data(player).available) if (q.id == questId) return q;
        for (Quest q : globalGroup)            if (q.id == questId) return q;
        return null;
    }

    private static void giveReward(String player, Quest q, MinecraftServer server) {
        LocalEconomy.getInstance().addShards(player, q.rewardShards);
        TransactionLog.log(player, TransactionLog.TYPE_REWARD, "Quête : " + q.label, q.rewardShards);
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(player);
            if (sp != null) NouvelleTerreBridge.sendBalanceToPlayer(sp);
        });
    }

    private static PendingReward buildPending(Quest q) {
        PendingReward pr  = new PendingReward();
        pr.questLabel     = q.label;
        pr.rewardItem     = q.rewardItem;
        pr.rewardItemQty  = q.rewardItemQty;
        pr.completedAt    = System.currentTimeMillis();
        return pr;
    }

    /** Consomme `qty` items de type `itemId` depuis l'inventaire du joueur. */
    private static String consumeItems(ServerPlayerEntity player, String itemId, int qty) {
        int remaining = qty;
        var inv = player.getInventory();
        for (int i = 0; i < inv.main.size() && remaining > 0; i++) {
            ItemStack s = inv.main.get(i);
            if (s.isEmpty()) continue;
            if (!Registries.ITEM.getId(s.getItem()).toString().equals(itemId)) continue;
            int take = Math.min(remaining, s.getCount());
            s.decrement(take);
            remaining -= take;
        }
        if (remaining > 0) return "Objets insuffisants dans l'inventaire (" + (qty - remaining) + "/" + qty + ").";
        return null;
    }

    public static synchronized void reset() {
        players.clear();
        globalGroup.clear();
        GROUP_PENDING.clear();
        globalGroupLastRefresh = 0;
        save();
        NouvelleTerreBridge.LOGGER.info("[QuestManager] Progression réinitialisée.");
    }

    /** Top N joueurs par quêtes complétées (name lowercase → count). */
    public static synchronized List<Map.Entry<String, Integer>> getLeaderboardByCompleted(int limit) {
        return players.entrySet().stream()
            .filter(e -> e.getValue().totalCompleted > 0)
            .sorted((a, b) -> b.getValue().totalCompleted - a.getValue().totalCompleted)
            .limit(limit)
            .map(e -> Map.entry(e.getKey(), e.getValue().totalCompleted))
            .collect(java.util.stream.Collectors.toList());
    }
}
