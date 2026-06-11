package com.nouvelleterrebridge.economy;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class RecurringTransferManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RecurringTransferManager instance;

    private final Path fichier;
    private final List<RecurringTransfer> transfers = new ArrayList<>();
    private int nextId = 1;

    private RecurringTransferManager() {
        fichier = FabricLoader.getInstance().getGameDir().resolve("nouvelle-terre-virements.json");
        charger();
    }

    public static synchronized RecurringTransferManager getInstance() {
        if (instance == null) instance = new RecurringTransferManager();
        return instance;
    }

    public static void register() {
        getInstance(); // force init
        ServerTickEvents.END_SERVER_TICK.register(server -> getInstance().tick(server));
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public synchronized int add(String from, String to, int amount, int intervalTicks) {
        RecurringTransfer rt = new RecurringTransfer(nextId++, from, to, amount, intervalTicks);
        transfers.add(rt);
        sauvegarder();
        return rt.id;
    }

    public synchronized boolean cancel(int id, String requester) {
        boolean removed = transfers.removeIf(rt -> rt.id == id && rt.from.equalsIgnoreCase(requester));
        if (removed) sauvegarder();
        return removed;
    }

    public synchronized List<RecurringTransfer> getForPlayer(String from) {
        return transfers.stream()
            .filter(rt -> rt.from.equalsIgnoreCase(from))
            .collect(Collectors.toList());
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    private synchronized void tick(MinecraftServer server) {
        for (RecurringTransfer rt : transfers) {
            rt.ticksSince++;
            if (rt.ticksSince >= rt.intervalTicks) {
                rt.ticksSince = 0;
                boolean ok = LocalEconomy.getInstance().transfer(rt.from, rt.to, rt.amount);
                if (ok) {
                    ServerPlayerEntity dest = server.getPlayerManager().getPlayer(rt.to);
                    if (dest != null) {
                        dest.sendMessage(Text.literal(
                            "§a[Nouvelle Terre] §fVirement de §f" + rt.from + " §a: +§f" + rt.amount + " ◆"));
                        com.nouvelleterrebridge.NouvelleTerreBridge.sendBalanceToPlayer(dest);
                    }
                    ServerPlayerEntity src = server.getPlayerManager().getPlayer(rt.from);
                    if (src != null) com.nouvelleterrebridge.NouvelleTerreBridge.sendBalanceToPlayer(src);
                } else {
                    ServerPlayerEntity src = server.getPlayerManager().getPlayer(rt.from);
                    if (src != null) src.sendMessage(Text.literal(
                        "§c[Nouvelle Terre] Virement récurrent vers §f" + rt.to + " §céché : solde insuffisant."));
                }
            }
        }
    }

    // ── Persistance ───────────────────────────────────────────────────────────

    private void charger() {
        File f = fichier.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            nextId = root.has("nextId") ? root.get("nextId").getAsInt() : 1;
            Type type = new TypeToken<List<RecurringTransfer>>() {}.getType();
            List<RecurringTransfer> loaded = GSON.fromJson(root.getAsJsonArray("transfers"), type);
            if (loaded != null) transfers.addAll(loaded);
            NouvelleTerreBridge.LOGGER.info("[RecurringTransfer] {} virement(s) chargé(s).", transfers.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[RecurringTransfer] Erreur chargement : {}", e.getMessage());
        }
    }

    private void sauvegarder() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fichier.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();
            root.addProperty("nextId", nextId);
            root.add("transfers", GSON.toJsonTree(transfers));
            GSON.toJson(root, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[RecurringTransfer] Erreur sauvegarde : {}", e.getMessage());
        }
    }
}
