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
import java.time.LocalDate;
import java.time.ZoneId;
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
        Set<Integer>        completedIds   = new HashSet<>();
    }

    /** Quête communautaire : objectif global auquel tous les joueurs contribuent sans accepter. */
    public static class CommunityState {
        public Quest                quest;
        public int                  progress;
        public boolean              completed;
        public Map<String, Integer> contributors = new HashMap<>(); // name lowercase → contribution
    }

    private static Map<String, PlayerData>      players      = new HashMap<>();
    private static List<Quest>                  globalGroup  = new ArrayList<>();
    private static List<Quest>                  dailySolo    = new ArrayList<>();
    private static CommunityState               community    = new CommunityState();
    private static String                       dailyDate    = "";
    private static int                          tickCount    = 0;

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
            Object ds = root.get("dailySolo");
            if (ds != null) {
                Type t = new TypeToken<List<Quest>>(){}.getType();
                List<Quest> loaded = GSON.fromJson(GSON.toJson(ds), t);
                if (loaded != null) dailySolo = loaded;
            }
            Object cm = root.get("community");
            if (cm != null) {
                CommunityState loaded = GSON.fromJson(GSON.toJson(cm), CommunityState.class);
                if (loaded != null) community = loaded;
            }
            Object dd = root.get("dailyDate");
            if (dd != null) dailyDate = String.valueOf(dd);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[QuestManager] Erreur lecture : {}", e.getMessage());
        }
    }

    private static void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("players", players);
            root.put("globalGroup", globalGroup);
            root.put("dailySolo", dailySolo);
            root.put("community", community);
            root.put("dailyDate", dailyDate);
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

    // ── Quêtes journalières — reset à 00h heure réelle ────────────────────────

    /** Appelé chaque tick serveur (vérifie toutes les ~1 min). */
    public static synchronized void tick(MinecraftServer server) {
        if (++tickCount % 1200 != 0) return;
        checkDailyRollover(server);
    }

    private static void checkDailyRollover(MinecraftServer server) {
        String today = LocalDate.now().toString();
        if (today.equals(dailyDate)) return;

        // 1. Livre automatiquement toutes les récompenses en attente
        deliverAllPending(server);

        // 2. Retire les journalières expirées des quêtes actives de tous les joueurs + reset their completedIds
        Set<Integer> oldDailyIds = new HashSet<>();
        for (Quest q : dailySolo) oldDailyIds.add(q.id);
        int oldCommunityId = community.quest != null ? community.quest.id : -1;
        for (PlayerData pd : players.values()) {
            pd.active.removeIf(a -> oldDailyIds.contains(a.questId));
            pd.completedIds.removeIf(id -> oldDailyIds.contains(id) || id == oldCommunityId);
        }

        // 3. Régénère les journalières + la quête communautaire
        dailySolo = QuestGenerator.generateDailies();
        community = new CommunityState();
        community.quest = QuestGenerator.generateCommunity();
        dailyDate = today;
        save();

        NouvelleTerreBridge.LOGGER.info("[QuestManager] Quêtes journalières régénérées ({}).", today);
        if (server != null) server.execute(() ->
            server.getPlayerManager().broadcast(net.minecraft.text.Text.literal(
                "§6[Quêtes] §eNouvelles quêtes journalières et quête communautaire disponibles ! §f/quetes"), false));
    }

    /**
     * Livre toutes les récompenses en attente de tous les joueurs.
     * Joueur en ligne avec de la place → items donnés. Sinon → converties en shards
     * (valeur = prix shop de l'item × quantité), argent créé via addShards.
     */
    private static void deliverAllPending(MinecraftServer server) {
        for (Map.Entry<String, PlayerData> e : players.entrySet()) {
            PlayerData d = e.getValue();
            if (d.pendingRewards.isEmpty()) continue;
            String name = e.getKey();
            ServerPlayerEntity sp = server != null ? server.getPlayerManager().getPlayer(name) : null;
            for (PendingReward pr : new ArrayList<>(d.pendingRewards)) {
                boolean delivered = false;
                if (sp != null && pr.rewardItem != null && !pr.rewardItem.isEmpty()) {
                    try {
                        var item = Registries.ITEM.get(new Identifier(pr.rewardItem));
                        ItemStack stack = new ItemStack(item, pr.rewardItemQty);
                        if (sp.getInventory().insertStack(stack) && stack.isEmpty()) {
                            delivered = true;
                            sp.sendMessage(net.minecraft.text.Text.literal(
                                "§6[Quêtes] §fRécompense livrée automatiquement : §a"
                                + pr.rewardItemQty + "× " + pr.rewardItem + " §7(" + pr.questLabel + ")"), false);
                        } else if (!stack.isEmpty() && stack.getCount() < pr.rewardItemQty) {
                            // Livraison partielle : le reste est converti en shards
                            int remaining = stack.getCount();
                            int shards = shardsValueOf(pr.rewardItem, remaining);
                            LocalEconomy.getInstance().addShards(name, shards);
                            delivered = true;
                            sp.sendMessage(net.minecraft.text.Text.literal(
                                "§6[Quêtes] §fRécompense livrée, inventaire plein : le reste converti en §a+"
                                + shards + " ◆ §7(" + pr.questLabel + ")"), false);
                        }
                    } catch (Exception ignored) {}
                }
                if (!delivered) {
                    // Hors ligne ou inventaire plein : conversion complète en shards (argent créé)
                    int shards = shardsValueOf(pr.rewardItem, pr.rewardItemQty);
                    LocalEconomy.getInstance().addShards(name, shards);
                    if (sp != null) sp.sendMessage(net.minecraft.text.Text.literal(
                        "§6[Quêtes] §fInventaire plein — récompense convertie : §a+" + shards
                        + " ◆ §7(" + pr.questLabel + ")"), false);
                }
            }
            d.pendingRewards.clear();
            if (sp != null) NouvelleTerreBridge.sendBalanceToPlayer(sp);
        }
    }

    /** Valeur en shards d'un item (prix du shop auto × quantité, minimum 1). */
    private static int shardsValueOf(String itemId, int qty) {
        if (itemId == null || itemId.isEmpty() || qty <= 0) return Math.max(1, qty);
        ShopThresholds.Entry entry = ShopThresholds.getOrCreate(itemId);
        int perUnit = entry != null ? Math.max(1, entry.prix) : 5;
        return perUnit * qty;
    }

    public static synchronized CommunityState getCommunity() { return community; }

    public static synchronized int getCommunityContribution(String player) {
        return community.contributors.getOrDefault(player.toLowerCase(), 0);
    }

    // ── API publique ──────────────────────────────────────────────────────────

    public static synchronized List<Quest> getAvailable(String player) {
        PlayerData d = data(player);
        List<Quest> list = new ArrayList<>(d.available);
        long now = System.currentTimeMillis();
        list.removeIf(q -> q.expiresAt > 0 && q.expiresAt < now);
        list.removeIf(q -> d.completedIds.contains(q.id));
        // Quêtes journalières (accessibles à tous, expirent à minuit)
        for (Quest dq : dailySolo) {
            if (dq.expiresAt > 0 && dq.expiresAt < now) continue;
            if (d.completedIds.contains(dq.id)) continue;
            list.add(0, dq);
        }
        // Ajouter les quêtes groupe globales éligibles
        int level = PlayerLevelManager.getLevel(player);
        for (Quest gq : globalGroup) {
            if (gq.expiresAt > 0 && gq.expiresAt < now) continue;
            if (gq.levelRequired > level) continue;
            if (d.completedIds.contains(gq.id)) continue;
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
            d.completedIds.add(questId);
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
        d.completedIds.add(questId);
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

    public static synchronized void onMobKilled(String player, String entityTypeId, MinecraftServer server) {
        advanceActive(player, "KILL", entityTypeId, 1, server);
    }

    public static synchronized void onItemHarvested(String player, String itemId, int count, MinecraftServer server) {
        advanceActive(player, "HARVEST", itemId, count, server);
    }

    private static void advanceActive(String player, String type, String target, int amount, MinecraftServer server) {
        PlayerData d = data(player);
        boolean changed = false;
        List<ActiveQuest> soloCompleted = new ArrayList<>();
        Map<String, List<ActiveQuest>> groupCompleted = new LinkedHashMap<>();

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
                        .ifPresent(a -> {
                            if (a.progress >= q.quantity) return;
                            a.progress = Math.min(a.progress + amount, q.quantity);
                            if (a.progress >= q.quantity)
                                groupCompleted.computeIfAbsent(p, k -> new ArrayList<>()).add(a);
                        });
                }
            } else {
                aq.progress = Math.min(aq.progress + amount, q.quantity);
                if (aq.progress >= q.quantity) soloCompleted.add(aq);
            }
            changed = true;
        }

        // Auto-réclamation : les quêtes KILL/HARVEST terminées sont récompensées
        // immédiatement (shards) ou envoyées dans "À Réclamer" (items)
        for (ActiveQuest aq : soloCompleted) autoClaim(player, aq, server);
        for (Map.Entry<String, List<ActiveQuest>> e : groupCompleted.entrySet())
            for (ActiveQuest aq : e.getValue()) autoClaim(e.getKey(), aq, server);

        changed |= advanceCommunity(player, type, target, amount, server);
        if (changed) save();
    }

    /** Termine automatiquement une quête KILL/HARVEST arrivée à 100 %. */
    private static void autoClaim(String player, ActiveQuest aq, MinecraftServer server) {
        Quest q = aq.snapshot;
        PlayerData d = data(player);
        d.active.remove(aq);
        d.totalCompleted++;
        d.completedIds.add(aq.questId);
        PlayerLevelManager.addXp(player, q.rewardXp, server);
        final String msg;
        if ("SHARDS".equals(q.rewardType)) {
            giveReward(player, q, server);
            msg = "§6[Quêtes] §fQuête terminée : §e" + q.label + "§f — §a+" + q.rewardShards
                + " ◆ §7(+" + q.rewardXp + " XP)";
        } else {
            d.pendingRewards.add(buildPending(q));
            msg = "§6[Quêtes] §fQuête terminée : §e" + q.label
                + "§f — récompense dans §a/quetes §f→ À Réclamer §7(+" + q.rewardXp + " XP)";
        }
        if (server != null) server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(player);
            if (sp != null) sp.sendMessage(net.minecraft.text.Text.literal(msg), false);
        });
    }

    /** Avance la quête communautaire ; distribue la récompense à tous les contributeurs quand l'objectif est atteint. */
    private static boolean advanceCommunity(String player, String type, String target, int amount, MinecraftServer server) {
        if (community.quest == null || community.completed) return false;
        Quest q = community.quest;
        if (!type.equals(q.type) || !q.target.equals(target)) return false;

        community.progress = Math.min(community.progress + amount, q.quantity);
        community.contributors.merge(player.toLowerCase(), amount, Integer::sum);

        if (community.progress >= q.quantity) {
            community.completed = true;
            int reward = q.rewardShards;
            for (String p : community.contributors.keySet()) {
                LocalEconomy.getInstance().addShards(p, reward);
                TransactionLog.log(p, TransactionLog.TYPE_REWARD, "Quête communautaire : " + q.label, reward);
            }
            int count = community.contributors.size();
            if (server != null) server.execute(() -> {
                server.getPlayerManager().broadcast(net.minecraft.text.Text.literal(
                    "§6[Quêtes] §eQuête communautaire accomplie : §f" + q.label + "§e ! §a+"
                    + reward + " ◆§e pour chacun des " + count + " participant(s)."), false);
                for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList())
                    if (community.contributors.containsKey(sp.getName().getString().toLowerCase()))
                        NouvelleTerreBridge.sendBalanceToPlayer(sp);
            });
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Quest findAvailable(String player, int questId) {
        for (Quest q : data(player).available) if (q.id == questId) return q;
        for (Quest q : dailySolo)              if (q.id == questId) return q;
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
        for (PlayerData pd : players.values()) {
            pd.completedIds.clear();
        }
        globalGroup.clear();
        dailySolo.clear();
        community = new CommunityState();
        dailyDate = "";
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
