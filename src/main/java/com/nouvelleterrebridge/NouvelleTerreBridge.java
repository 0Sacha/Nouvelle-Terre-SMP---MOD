package com.nouvelleterrebridge;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.commands.BankCommand;
import com.nouvelleterrebridge.commands.ConflitCommand;
import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.commands.EventNarratifCommand;
import com.nouvelleterrebridge.commands.HdvCommand;
import com.nouvelleterrebridge.commands.LierCommand;
import com.nouvelleterrebridge.commands.PayCommand;
import com.nouvelleterrebridge.commands.ProductionCommand;
import com.nouvelleterrebridge.commands.QuetesCommand;
import com.nouvelleterrebridge.commands.RegistreCommand;
import com.nouvelleterrebridge.commands.ShopCommand;
import com.nouvelleterrebridge.commands.MarcheCommand;
import com.nouvelleterrebridge.commands.ServerAdminCommand;
import com.nouvelleterrebridge.network.RegistreNetworking;
import com.nouvelleterrebridge.commands.WikiCommand;
import com.nouvelleterrebridge.economy.FirstJoinTracker;
import com.nouvelleterrebridge.economy.PlayerLevelManager;
import com.nouvelleterrebridge.economy.QuestManager;
import com.nouvelleterrebridge.network.QuestNetworking;
import com.nouvelleterrebridge.economy.Loan;
import com.nouvelleterrebridge.economy.LoanManager;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.KillRewards;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.economy.PlacedBlockTracker;
import com.nouvelleterrebridge.economy.ShardDenominations;
import com.nouvelleterrebridge.economy.ProductionShopManager;
import com.nouvelleterrebridge.economy.ProductionTracker;
import com.nouvelleterrebridge.economy.RecurringTransfer;
import com.nouvelleterrebridge.economy.RecurringTransferManager;
import com.nouvelleterrebridge.economy.ShopThresholds;
import com.nouvelleterrebridge.economy.ServerShopActions;
import com.nouvelleterrebridge.economy.ServerShopPriceManager;
import com.nouvelleterrebridge.economy.TransactionLog;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import com.nouvelleterrebridge.events.PlayerEvents;
import com.nouvelleterrebridge.events.ServerEvents;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.http.EventQueue;
import com.nouvelleterrebridge.network.BankNetworking;
import com.nouvelleterrebridge.network.ConflitNetworking;
import com.nouvelleterrebridge.network.HdvNetworking;
import com.nouvelleterrebridge.network.HubNetworking;
import com.nouvelleterrebridge.network.ShopNetworking;
import com.nouvelleterrebridge.network.ProductionNetworking;
import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.market.MarketActions;
import com.nouvelleterrebridge.market.MarketListing;
import com.nouvelleterrebridge.market.MarketManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Mod(NouvelleTerreBridge.NEOFORGE_ID)
public class NouvelleTerreBridge {

    // ⚠ Namespace de ressources (items, canaux réseau) — distinct du modId NeoForge
    // technique ci-dessous. NeoForge impose un modId sans tiret ([a-z0-9_]) pour
    // @Mod/neoforge.mods.toml/ModList, mais le namespace des ressources doit rester
    // "nouvelle-terre-bridge" : le changer ferait disparaître tous les Shards et
    // Parchemins déjà en circulation chez les joueurs. Les deux sont indépendants,
    // ne jamais les unifier.
    public static final String MOD_ID = "nouvelle-terre-bridge";
    /** modId technique NeoForge — @Mod, neoforge.mods.toml, ModList uniquement. */
    public static final String NEOFORGE_ID = "nouvelle_terre_bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ModConfig config;

    /** Cache uuid → nom RP, partagé entre PlayerEvents et le mixin de nommage. */
    public static final ConcurrentHashMap<String, String> nomsRP = new ConcurrentHashMap<>();

    /**
     * Serveur courant, pour les notifications émises depuis du code sans contexte
     * (compteurs de production, par exemple). Null tant que le serveur n'a pas démarré.
     */
    public static volatile MinecraftServer serveur;

    /** Bornes basses des tranches de richesse affichées dans /bank → Economie. */
    public static final int[] TRANCHES_MIN = {0, 100, 1_000, 10_000, 100_000};

    // ── Monnaie physique : coupures de 1 à 100 ◆ ──────────────────────────────
    // Purement du rangement : retirer 5 000 ◆ en pièces de 1 remplissait 78 piles.
    // `shard` garde son identifiant d'origine — le renommer aurait fait disparaître
    // tous les Shards déjà en circulation chez les joueurs.
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    /** Shard ◆ — 1 ◆. Retrait via /bank, dépôt par clic droit. */
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SHARD     = ITEMS.register("shard",     () -> coupure(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SHARD_5   = ITEMS.register("shard_5",   () -> coupure(5));
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SHARD_10  = ITEMS.register("shard_10",  () -> coupure(10));
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SHARD_20  = ITEMS.register("shard_20",  () -> coupure(20));
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SHARD_50  = ITEMS.register("shard_50",  () -> coupure(50));
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SHARD_100 = ITEMS.register("shard_100", () -> coupure(100));

    private static Item coupure(int valeur) {
        return new com.nouvelleterrebridge.item.ShardItem(new Item.Properties().rarity(Rarity.UNCOMMON), valeur);
    }

    /** Parchemin — terminal portatif ouvrant le hub des fenêtres du mod. */
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> PARCHEMIN = ITEMS.register("parchemin",
        () -> new com.nouvelleterrebridge.item.ParcheminItem(new Item.Properties()
            .stacksTo(1)
            .fireResistant()
            .rarity(Rarity.RARE)));

    public NouvelleTerreBridge(IEventBus modEventBus) {
        LOGGER.info("[NouvelleTerreBridge] Initialisation du mod...");

        // Doit être fait avant tout le reste : force le chargement des 34 canaux,
        // y compris ceux (comme WikiNetworking) qui ne sont sinon référencés que
        // depuis l'intérieur d'un lambda jamais évalué à ce stade.
        NtNet.precharger();

        ITEMS.register(modEventBus);
        modEventBus.addListener(this::onBuildCreativeTabs);
        modEventBus.addListener(NtNet::enregistrer);
        NeoForge.EVENT_BUS.register(this);

        // Enregistrement explicite plutôt que @EventBusSubscriber : ce mod garde un
        // seul sourceSet client+serveur (comme côté Fabric), et la découverte par
        // annotation ne s'y est pas montrée fiable — onClientSetup n'était jamais
        // appelé. La classe cliente n'est chargée que si on est bien sur le client :
        // sans ce test, un serveur dédié planterait en touchant des classes qui
        // référencent Minecraft/KeyMapping.
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.nouvelleterrebridge.NouvelleTerreBridgeClient.init(modEventBus);
        }

        config = ModConfig.charger();
        MaintenanceMode.load();
        LOGGER.info("[NouvelleTerreBridge] Configuration chargée : url={}", config.getBotUrl());

        EventQueue.getInstance().charger();
        EventDispatcher.init(config);

        // Référence serveur partagée — mise à jour dans onServerStarted/onServerStopped
        // (ci-dessous) et non dans ServerEvents, qui se désactive entièrement si les
        // événements bot sont coupés en config.

        ServerEvents.register();
        PlayerEvents.register();
        KillRewards.register();
        PlaytimeTracker.register();
        RecurringTransferManager.register();
        LoanManager.register();

        ShopThresholds.load();
        ServerShopPriceManager.load();
        ProductionTracker.load();
        ProductionShopManager.purgerAnnoncesLegacy();
        PlayerLevelManager.load();
        QuestManager.load();
        FirstJoinTracker.getInstance().load();
        com.nouvelleterrebridge.economy.DailyBonusTracker.load();

        com.nouvelleterrebridge.service.ServiceNetworkHandler.register();
        registerHdvNetworking();
        registerBankNetworking();
        registerQuestNetworking();
        registerRegistreNetworking();
        registerProductionNetworking();
        registerConflitNetworking();
        registerHubNetworking();
        registerShopNetworking();

        LOGGER.info("[NouvelleTerreBridge] Mod initialisé avec succès.");
    }

    private void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        // Une entrée par coupure : entries.add() n'a pas de variante varargs
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SHARD.get());
            event.accept(SHARD_5.get());
            event.accept(SHARD_10.get());
            event.accept(SHARD_20.get());
            event.accept(SHARD_50.get());
            event.accept(SHARD_100.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PARCHEMIN.get());
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        serveur = event.getServer();
        // Les recettes ne sont chargées qu'au démarrage du serveur : c'est
        // le seul moment où les prix dérivés peuvent être calculés.
        ShopThresholds.deriverEtMigrer(event.getServer());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        serveur = null;
    }

    // Blocs cassés → drops réels (fortune/silk touch inclus)
    @net.neoforged.bus.api.SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        ServerLevel sw = event.getLevel();
        var pos = event.getPos();

        // Un bloc posé par un joueur puis recassé n'est pas de la production :
        // sans ce garde-fou, poser/casser le même bloc en boucle débloquait
        // n'importe quel item au Shop Serveur.
        if (PlacedBlockTracker.estPoseParJoueur(sw, pos)) return;
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        String pName = player.getName().getString();
        for (var itemEntity : event.getDrops()) {
            ItemStack drop = itemEntity.getItem();
            String itemId = BuiltInRegistries.ITEM.getKey(drop.getItem()).toString();
            ProductionTracker.add(itemId, drop.getCount());
            QuestManager.onItemHarvested(pName, itemId, drop.getCount(), sw.getServer());
        }
    }

    // Mobs tués par un joueur → quêtes KILL
    @net.neoforged.bus.api.SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        QuestManager.onMobKilled(player.getName().getString(), typeId, player.getServer());
    }

    // Mort d'un joueur → webhook Discord (anciennement LivingEntityMixin)
    @net.neoforged.bus.api.SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
        String pseudo = joueur.getName().getString();
        String causeTexte = event.getSource().getLocalizedDeathMessage(joueur).getString();

        Map<String, Object> data = new HashMap<>();
        data.put("player",  pseudo);
        data.put("uuid",    joueur.getStringUUID());
        data.put("message", causeTexte);
        data.put("cause",   event.getSource().getMsgId());
        EventDispatcher.envoyer("PLAYER_DEATH", data);
    }

    // Drops d'un mob tué par un joueur → production (anciennement MobDropMixin+EntityDropMixin)
    @net.neoforged.bus.api.SubscribeEvent
    public void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) return;
        for (net.minecraft.world.entity.item.ItemEntity itemEntity : event.getDrops()) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;
            ProductionTracker.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
        }
    }

    // Pose de bloc par un joueur → marquage anti-exploit (anciennement BlockItemMixin)
    @net.neoforged.bus.api.SubscribeEvent
    public void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        PlacedBlockTracker.marquer((net.minecraft.world.level.Level) event.getLevel(), event.getPos());
    }

    // Craft d'un item → production + quêtes (anciennement CraftingResultSlotMixin)
    @net.neoforged.bus.api.SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getCrafting();
        if (stack.isEmpty()) return;

        // Décompactage (1 bloc → 4/9 unités) : ne crédite rien et retire 1 du
        // compteur du bloc source, sinon compacter/décompacter en boucle gonflait
        // le compteur à l'infini. Le compactage (9 → 1) reste compté.
        net.minecraft.world.item.Item decompacte = ingredientDecompacte(event.getInventory(), stack);
        if (decompacte != null) {
            ProductionTracker.remove(BuiltInRegistries.ITEM.getKey(decompacte).toString(), 1);
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ProductionTracker.add(itemId, stack.getCount());
        QuestManager.onItemHarvested(player.getName().getString(), itemId, stack.getCount(), player.getServer());
    }

    /**
     * Si la recette est un décompactage (un seul bloc en entrée, 4 ou 9 unités en
     * sortie), retourne l'item d'entrée ; sinon null.
     */
    private static net.minecraft.world.item.Item ingredientDecompacte(net.minecraft.world.Container input, ItemStack resultat) {
        net.minecraft.world.item.Item ingredient = null;
        int total = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (ingredient == null) ingredient = s.getItem();
            else if (ingredient != s.getItem()) return null;   // recette composite
            total += s.getCount();
        }
        if (ingredient == null || ingredient == resultat.getItem()) return null;
        if (total != 1) return null;
        int sortie = resultat.getCount();
        return (sortie == 9 || sortie == 4) ? ingredient : null;
    }

    // Bloque le jet du Parchemin, touche lâcher ou glisser hors inventaire
    // (anciennement ParcheminDropMixin — couvre en prime le glisser, une limite
    // documentée du mixin d'origine)
    @net.neoforged.bus.api.SubscribeEvent
    public void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (event.getEntity().getItem().is(PARCHEMIN.get())) {
            event.setCanceled(true);
        }
    }

    // Rollover des quêtes journalières (00h heure réelle, vérifié chaque minute)
    @net.neoforged.bus.api.SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        QuestManager.tick(event.getServer());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        HdvCommand.register(dispatcher);
        ShopCommand.register(dispatcher);
        BankCommand.register(dispatcher);
        EconomieCommand.register(dispatcher);
        PayCommand.register(dispatcher);
        LierCommand.register(dispatcher);
        ConflitCommand.register(dispatcher);
        EventNarratifCommand.register(dispatcher);
        ProductionCommand.register(dispatcher);
        QuetesCommand.register(dispatcher);
        RegistreCommand.register(dispatcher);
        WikiCommand.register(dispatcher);
        MarcheCommand.register(dispatcher);
        ServerAdminCommand.register(dispatcher);
        com.nouvelleterrebridge.commands.SauvegardeCommand.register(dispatcher);
        com.nouvelleterrebridge.commands.MaintenanceCommand.register(dispatcher);
    }

    // Envoie le solde au joueur dès qu'il est en jeu + refresh pool quêtes
    // + garantit qu'il possède son Parchemin
    @net.neoforged.bus.api.SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
        MinecraftServer server = joueur.getServer();
        server.execute(() -> {
            sendBalanceToPlayer(joueur);
            QuestManager.refreshPlayerPool(joueur.getName().getString(), server);
            donnerParcheminSiAbsent(joueur);
        });
    }

    // Le Parchemin est rendu après une mort, même sans keepInventory
    @net.neoforged.bus.api.SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) donnerParcheminSiAbsent(joueur);
    }

    private void registerHdvNetworking() {
        NtNet.surServeur(HdvNetworking.HDV_ACTION, (server, player, buf) -> {
            // ── Lecture du paquet : obligatoirement ici ──
            // Le FriendlyByteBuf est libéré dès le retour de ce callback : tout doit être
            // extrait maintenant, et rien de ce qui suit ne doit retoucher au buffer.
            int type = buf.readInt();
            final String sItemId;
            final String sNbt;
            final int    sQty, sPrice, sListingId;

            switch (type) {
                case HdvNetworking.ACTION_BUY -> {
                    sItemId = buf.readUtf(); sQty = buf.readInt(); sNbt = buf.readUtf();
                    sPrice = 0; sListingId = 0;
                }
                case HdvNetworking.ACTION_SELL -> {
                    sItemId = buf.readUtf(); sQty = buf.readInt(); sPrice = buf.readInt(); sNbt = buf.readUtf();
                    sListingId = 0;
                }
                case HdvNetworking.ACTION_WITHDRAW -> {
                    sListingId = buf.readInt();
                    sItemId = ""; sNbt = ""; sQty = 0; sPrice = 0;
                }
                default -> {
                    sItemId = ""; sNbt = ""; sQty = 0; sPrice = 0; sListingId = 0;
                }
            }

            // ── Exécution : obligatoirement sur le thread serveur ──
            // buy/sell/withdraw touchent l'inventaire du joueur. Le faire depuis le thread
            // réseau court-circuite la synchronisation faite au tick : le serveur avait bien
            // l'item enchanté, mais le client se retrouvait avec une pile vierge.
            server.execute(() -> {
                final String result;
                switch (type) {
                    case HdvNetworking.ACTION_BUY -> result = MarketActions.buy(player, sItemId, sQty, sNbt);
                    case HdvNetworking.ACTION_SELL -> {
                        String err = MarketActions.sellByItemId(player, sItemId, sQty, sPrice, sNbt);
                        result = err != null ? err : "§a✅ Annonce publiée avec succès !";
                    }
                    case HdvNetworking.ACTION_WITHDRAW -> result = MarketActions.withdraw(player, sListingId);
                    default -> result = "§cAction inconnue.";
                }
                sendHdvResult(player, result, server);
            });
        });
    }

    public static void sendBalanceToPlayer(ServerPlayer player) {
        int balance = LocalEconomy.getInstance().getBalance(player.getName().getString());
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeInt(balance);
        NtNet.versClient(player, HdvNetworking.NT_BALANCE, buf);
    }

    // Couleurs des toasts NT_TOAST — dupliquées de NotificationHud exprès : le code
    // serveur ne doit pas référencer une classe cliente, même pour des constantes
    // (l'inlining de javac masquait le problème, jusqu'au jour où il ne le fera plus).
    public static final int TOAST_VERT  = 0xFF2EAD6B;
    public static final int TOAST_OR    = 0xFFE8A838;
    public static final int TOAST_ROUGE = 0xFFBF2040;

    public static void sendToast(ServerPlayer player, int color, String... lines) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeInt(color);
        buf.writeInt(lines.length);
        for (String line : lines) buf.writeUtf(line);
        NtNet.versClient(player, HdvNetworking.NT_TOAST, buf);
    }

    public static void sendHdvResult(ServerPlayer player, String message, MinecraftServer server) {
        boolean ok = !message.contains("§c");
        FriendlyByteBuf resp = com.nouvelleterrebridge.network.NtNet.buffer();
        resp.writeBoolean(ok);
        resp.writeUtf(message);
        resp.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeListings(resp);
        NtNet.versClient(player, HdvNetworking.HDV_RESULT, resp);
    }

    public static FriendlyByteBuf buildHdvOpenPacket(ServerPlayer player, MinecraftServer server) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeListings(buf);
        return buf;
    }

    private static void writeListings(FriendlyByteBuf buf) {
        List<MarketListing> listings = MarketManager.getInstance().getAll();
        buf.writeInt(listings.size());
        for (MarketListing l : listings) {
            buf.writeInt(l.id);
            buf.writeUtf(l.seller);
            buf.writeUtf(l.item);
            buf.writeInt(l.quantity);
            buf.writeInt(l.pricePerUnit);
            buf.writeUtf(l.itemNBT != null ? l.itemNBT : "");
        }
    }

    // ── Hub (Parchemin) ──────────────────────────────────────────────────────

    /** Donne le Parchemin au joueur s'il ne l'a pas déjà (connexion, respawn). */
    public static void donnerParcheminSiAbsent(ServerPlayer player) {
        if (player == null) return;
        for (net.minecraft.world.item.ItemStack s : player.getInventory().items)
            if (s.is(PARCHEMIN.get())) return;
        if (player.getInventory().offhand.stream().anyMatch(s -> s.is(PARCHEMIN.get()))) return;

        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(PARCHEMIN.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** Catalogue du Shop Serveur : tous les items connus des seuils, avec prix d'achat et de rachat. */
    public static FriendlyByteBuf buildShopOpenPacket(ServerPlayer player) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeShopEntries(buf, player.getName().getString());
        return buf;
    }

    private static void writeShopEntries(FriendlyByteBuf buf, String pseudo) {
        // Seuls les items dont la production naturelle a atteint le seuil sont
        // au catalogue : c'est ce qui rend le shop dépendant de l'activité du serveur.
        var debloques = ShopThresholds.all().entrySet().stream()
            .filter(e -> ServerShopActions.estDebloque(e.getKey()))
            .map(java.util.Map.Entry::getKey)
            .sorted()
            .toList();

        buf.writeInt(debloques.size());
        for (String itemId : debloques) {
            var pe = ServerShopPriceManager.getOrCreate(itemId);
            buf.writeUtf(itemId);
            // Prix taxe de fortune comprise : le client doit afficher ce que ce
            // joueur-là paiera réellement, pas un tarif théorique.
            buf.writeInt(ServerShopPriceManager.getPricePour(itemId, pseudo));
            buf.writeInt(ServerShopPriceManager.getBuybackPrice(itemId));
            buf.writeLong(pe.unitsSold - pe.unitsBought);
        }
    }

    private void registerShopNetworking() {
        NtNet.surServeur(ShopNetworking.SHOP_ACTION, (server, player, buf) -> {
            int action    = buf.readInt();
            String itemId = buf.readUtf();
            int quantity  = buf.readInt();

            server.execute(() -> {
                String result = switch (action) {
                    case ShopNetworking.ACTION_BUY  -> ServerShopActions.buy(player, itemId, quantity);
                    case ShopNetworking.ACTION_SELL -> ServerShopActions.sell(player, itemId, quantity);
                    case ShopNetworking.ACTION_CLAIM_PARCHEMIN -> ServerShopActions.claimParchemin(player);
                    default -> "§cAction inconnue.";
                };

                FriendlyByteBuf resp = com.nouvelleterrebridge.network.NtNet.buffer();
                resp.writeBoolean(!result.contains("§c"));
                resp.writeUtf(result);
                resp.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
                writeShopEntries(resp, player.getName().getString());
                NtNet.versClient(player, ShopNetworking.SHOP_RESULT, resp);
                sendBalanceToPlayer(player);
            });
        });
    }

    private void registerHubNetworking() {
        NtNet.surServeur(HubNetworking.HUB_ACTION, (server, player, buf) -> {
            int action = buf.readInt();
            server.execute(() -> {
                switch (action) {
                    case HubNetworking.ACTION_HDV ->
                        NtNet.versClient(player, HdvNetworking.HDV_OPEN, buildHdvOpenPacket(player, server));
                    case HubNetworking.ACTION_BANK ->
                        NtNet.versClient(player, BankNetworking.BANK_OPEN, buildBankOpenPacket(player, server));
                    case HubNetworking.ACTION_SHOP ->
                        NtNet.versClient(player, ShopNetworking.SHOP_OPEN, buildShopOpenPacket(player));
                    case HubNetworking.ACTION_MARCHE ->
                        com.nouvelleterrebridge.service.ServiceNetworkHandler.ouvrir(player);
                    case HubNetworking.ACTION_QUETES     -> sendQuestOpen(player);
                    case HubNetworking.ACTION_PRODUCTION -> sendProductionOpen(player);
                    case HubNetworking.ACTION_REGISTRE   -> RegistreCommand.open(player);
                    case HubNetworking.ACTION_CONFLIT    -> ConflitCommand.open(player);
                    case HubNetworking.ACTION_WIKI ->
                        NtNet.versClient(player, com.nouvelleterrebridge.network.WikiNetworking.WIKI_OPEN, com.nouvelleterrebridge.network.NtNet.buffer());
                    default -> LOGGER.warn("[Hub] Action inconnue : {}", action);
                }
            });
        });
    }

    // ── Bank networking ──────────────────────────────────────────────────────

    private void registerBankNetworking() {
        NtNet.surServeur(BankNetworking.BANK_REQUEST, (server, player, buf) -> {
            server.execute(() -> NtNet.versClient(player, BankNetworking.BANK_OPEN, buildBankOpenPacket(player, server)));
        });

        NtNet.surServeur(BankNetworking.BANK_ACTION, (server, player, buf) -> {
            int type = buf.readInt();
            final String result;
            switch (type) {
                case BankNetworking.ACTION_LOAN_REQUEST -> {
                    String borrowerName = buf.readUtf();
                    int amount          = buf.readInt();
                    int durationDays    = buf.readInt();
                    int penaltyBase     = buf.readInt();
                    int penaltyIncrease = buf.readInt();
                    String lender = player.getName().getString();
                    if (lender.equalsIgnoreCase(borrowerName)) {
                        result = "§cVous ne pouvez pas vous preter a vous-meme.";
                    } else if (!LocalEconomy.getInstance().estConnu(borrowerName)) {
                        result = "§cJoueur inconnu.";
                    } else if (amount <= 0 || durationDays <= 0 || penaltyBase <= 0) {
                        result = "§cValeurs invalides.";
                    } else {
                        String err = LoanManager.getInstance().request(borrowerName, lender, amount, durationDays, penaltyBase, penaltyIncrease);
                        if (err != null) {
                            result = "§c" + err;
                        } else {
                            result = "§a✅ Proposition envoyee a §f" + borrowerName + "§a — en attente de son accord.";
                            server.execute(() -> {
                                ServerPlayer bp = server.getPlayerList().getPlayerByName(borrowerName);
                                if (bp != null) bp.sendSystemMessage(Component.literal(
                                    "§e[Banque] §f" + lender + " §evous propose un credit de §f" + amount
                                    + " ◆§e (duree " + durationDays + " j, penalite " + penaltyBase
                                    + " ◆/j de retard). §7Ouvre /bank → Credits pour accepter ou refuser."));
                            });
                        }
                    }
                }
                case BankNetworking.ACTION_LOAN_ACCEPT -> {
                    int requestId = buf.readInt();
                    String borrowerName = player.getName().getString();
                    LoanManager.LoanRequest req = LoanManager.getInstance().getRequest(requestId);
                    String err = LoanManager.getInstance().acceptRequest(borrowerName, requestId);
                    if (err != null) {
                        result = "§c" + err;
                    } else {
                        result = "§a✅ Credit accepte — §f" + req.principal + " ◆§a recus de §f" + req.lender
                            + "§a ! A rembourser sous " + req.durationDays + " j.";
                        server.execute(() -> {
                            sendBalanceToPlayer(player);
                            ServerPlayer lp = server.getPlayerList().getPlayerByName(req.lender);
                            if (lp != null) {
                                lp.sendSystemMessage(Component.literal(
                                    "§a[Banque] §f" + borrowerName + " §aa accepte votre credit — §f"
                                    + req.principal + " ◆§a transferes."));
                                sendBalanceToPlayer(lp);
                            }
                        });
                    }
                }
                case BankNetworking.ACTION_LOAN_DECLINE -> {
                    int requestId = buf.readInt();
                    String who = player.getName().getString();
                    LoanManager.LoanRequest req = LoanManager.getInstance().getRequest(requestId);
                    String err = LoanManager.getInstance().declineRequest(who, requestId);
                    if (err != null) {
                        result = "§c" + err;
                    } else {
                        boolean estPreteur = req.lender.equalsIgnoreCase(who);
                        result = estPreteur ? "§a✅ Proposition annulee." : "§a✅ Proposition refusee.";
                        String autre = estPreteur ? req.borrower : req.lender;
                        String msg = estPreteur
                            ? "§e[Banque] §f" + who + " §ea annule sa proposition de credit (" + req.principal + " ◆)."
                            : "§c[Banque] §f" + who + " §ca refuse votre proposition de credit (" + req.principal + " ◆).";
                        server.execute(() -> {
                            ServerPlayer op = server.getPlayerList().getPlayerByName(autre);
                            if (op != null) op.sendSystemMessage(Component.literal(msg));
                        });
                    }
                }
                case BankNetworking.ACTION_LOAN_REPAY -> {
                    int loanId = buf.readInt();
                    String borrowerName = player.getName().getString();
                    Loan loan = LoanManager.getInstance().getLoan(loanId);
                    String err = LoanManager.getInstance().repay(borrowerName, loanId);
                    if (err != null) {
                        result = "§c" + err;
                    } else {
                        result = "§a✅ Credit rembourse !";
                        if (loan != null) {
                            server.execute(() -> {
                                ServerPlayer lp = server.getPlayerList().getPlayerByName(loan.lender);
                                if (lp != null) lp.sendSystemMessage(Component.literal(
                                    "§a[Nouvelle Terre] §f" + borrowerName + " §aa rembourse son credit de §f" + loan.principal + " ◆§a !"));
                            });
                        }
                    }
                }
                case BankNetworking.ACTION_LOAN_FORGIVE -> {
                    int loanId = buf.readInt();
                    String err = LoanManager.getInstance().forgive(player.getName().getString(), loanId);
                    result = err != null ? "§c" + err : "§a✅ Credit pardonne.";
                }
                case BankNetworking.ACTION_TRANSFER -> {
                    String target = buf.readUtf();
                    int amount = buf.readInt();
                    String sender = player.getName().getString();
                    if (sender.equalsIgnoreCase(target)) {
                        result = "§cVous ne pouvez pas vous envoyer des fonds.";
                    } else {
                        boolean ok = LocalEconomy.getInstance().transfer(sender, target, amount);
                        if (ok) {
                            result = "§a✅ " + amount + " ◆ envoyés à §f" + target + "§a.";
                            server.execute(() -> {
                                ServerPlayer t = server.getPlayerList().getPlayerByName(target);
                                if (t != null) t.sendSystemMessage(Component.literal(
                                    "§a[Nouvelle Terre] §f" + sender + " §avous a envoyé §f" + amount + " ◆§a !"));
                            });
                        } else {
                            result = "§cSolde insuffisant ou joueur inconnu.";
                        }
                    }
                }
                case BankNetworking.ACTION_RECURRING_CREATE -> {
                    String to = buf.readUtf();
                    int amount = buf.readInt();
                    int intervalTicks = buf.readInt();
                    String from = player.getName().getString();
                    if (from.equalsIgnoreCase(to)) {
                        result = "§cVous ne pouvez pas vous faire de virement récurrent.";
                    } else if (!LocalEconomy.getInstance().estConnu(to)) {
                        result = "§cJoueur inconnu.";
                    } else if (amount <= 0) {
                        result = "§cMontant invalide.";
                    } else if (intervalTicks < 1200) {
                        result = "§cIntervalle minimum : 1 minute.";
                    } else {
                        RecurringTransferManager.getInstance().add(from, to, amount, intervalTicks);
                        result = "§a✅ Virement récurrent créé vers §f" + to + "§a !";
                    }
                }
                case BankNetworking.ACTION_RECURRING_CANCEL -> {
                    int id = buf.readInt();
                    boolean ok = RecurringTransferManager.getInstance().cancel(id, player.getName().getString());
                    result = ok ? "§a✅ Virement récurrent annulé." : "§cVirement introuvable.";
                }
                case BankNetworking.ACTION_WITHDRAW_SHARDS -> {
                    int amount = buf.readInt();
                    String name = player.getName().getString();
                    if (amount <= 0) {
                        result = "§cMontant invalide.";
                    } else if (LocalEconomy.getInstance().getBalance(name) < amount) {
                        result = "§cSolde insuffisant.";
                    } else {
                        LocalEconomy.getInstance().removeShards(name, amount);
                        TransactionLog.log(name, TransactionLog.TYPE_TRANSFER_OUT, "Retrait en Shards physiques", amount);
                        result = "§a✅ " + amount + " ◆ retirés en coupures — clic droit dessus pour les redéposer.";
                        // Rendu en grosses coupures d'abord : 5 000 ◆ en pièces de 1
                        // remplissaient 78 piles d'inventaire.
                        server.execute(() -> {
                            ShardDenominations.donner(player, amount);
                            sendBalanceToPlayer(player);
                        });
                    }
                }
                case BankNetworking.ACTION_DEPOSIT_SHARDS -> {
                    int demande = buf.readInt();   // 0 = tout ce qu'il y a dans l'inventaire
                    // Comptage et retrait sur le thread serveur : l'inventaire n'est pas
                    // thread-safe, et le montant réellement déposable en dépend.
                    server.execute(() -> {
                        String name = player.getName().getString();

                        // Valeur réelle de la monnaie portée, toutes coupures confondues
                        int dispo = ShardDenominations.totalEnPoche(player);

                        if (dispo <= 0) {
                            sendBankResult(player, "§cAucun Shard dans votre inventaire.", server);
                            return;
                        }
                        if (demande < 0) {
                            sendBankResult(player, "§cMontant invalide.", server);
                            return;
                        }
                        // Le client borne déjà la saisie, mais il ne fait pas autorité :
                        // on re-plafonne à ce qui est réellement en poche.
                        int aDeposer = demande == 0 ? dispo : Math.min(demande, dispo);

                        // Une coupure ne se coupe pas en deux : si le prélèvement dépasse
                        // le montant voulu, la différence est rendue en petite monnaie.
                        int prelevé = ShardDenominations.retirer(player, aDeposer);
                        int appoint = prelevé - aDeposer;
                        if (appoint > 0) ShardDenominations.donner(player, appoint);

                        LocalEconomy.getInstance().depositShards(name, aDeposer);
                        TransactionLog.log(name, TransactionLog.TYPE_TRANSFER_IN, "Dépôt de Shards physiques", aDeposer);
                        sendBalanceToPlayer(player);
                        sendBankResult(player, "§a✅ " + aDeposer + " ◆ déposés"
                            + (appoint > 0 ? " §7(" + appoint + " ◆ rendus en monnaie)" : "")
                            + " §a— solde : §e" + LocalEconomy.getInstance().getBalance(name) + " ◆", server);
                    });
                    return;
                }
                default -> result = "§cAction inconnue.";
            }
            server.execute(() -> sendBankResult(player, result, server));
        });
    }

    public static void sendBankResult(ServerPlayer player, String message, MinecraftServer server) {
        boolean ok = !message.contains("§c");
        FriendlyByteBuf resp = com.nouvelleterrebridge.network.NtNet.buffer();
        resp.writeBoolean(ok);
        resp.writeUtf(message);
        writeBankData(resp, player, server);
        NtNet.versClient(player, BankNetworking.BANK_RESULT, resp);
    }

    public static FriendlyByteBuf buildBankOpenPacket(ServerPlayer player, MinecraftServer server) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        writeBankData(buf, player, server);
        return buf;
    }

    private static void writeBankData(FriendlyByteBuf buf, ServerPlayer player, MinecraftServer server) {
        String name = player.getName().getString();
        LocalEconomy eco = LocalEconomy.getInstance();

        buf.writeInt(eco.getBalance(name));
        buf.writeInt(PlaytimeTracker.getTicksUntilReward(player.getUUID()));

        // Transactions
        List<TransactionLog.Entry> txs = TransactionLog.getLast(name, 20);
        buf.writeInt(txs.size());
        for (TransactionLog.Entry e : txs) {
            buf.writeInt(e.type()); buf.writeUtf(e.label()); buf.writeInt(e.amount()); buf.writeLong(e.timestamp());
        }

        // Stats économiques — les comptes système ($Serveur) sont exclus
        Map<String, Integer> allBalances = eco.getAllBalances();
        int totalShards = allBalances.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("$"))
            .mapToInt(Map.Entry::getValue).filter(v -> v > 0).sum();
        buf.writeInt(totalShards);
        buf.writeInt((int) allBalances.keySet().stream().filter(k -> !k.startsWith("$")).count());

        // ── Répartition des richesses ──
        // Calculée serveur : le classement envoyé au client est limité au top 10,
        // il ne permettrait pas de reconstituer une distribution.
        List<Integer> soldes = allBalances.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("$"))
            .map(Map.Entry::getValue)
            .map(v -> Math.max(0, v))
            .sorted()
            .collect(Collectors.toList());

        int[] tranches = new int[TRANCHES_MIN.length];
        for (int solde : soldes) {
            for (int i = TRANCHES_MIN.length - 1; i >= 0; i--) {
                if (solde >= TRANCHES_MIN[i]) { tranches[i]++; break; }
            }
        }
        for (int t : tranches) buf.writeInt(t);

        // Part du patrimoine détenue par les 50 % les plus pauvres / 40 % du milieu / 10 % les plus riches
        long somme = soldes.stream().mapToLong(Integer::longValue).sum();
        int n = soldes.size();
        long partBasse = 0, partHaute = 0;
        int iBas = n / 2, iTop = (int) Math.ceil(n * 0.9);
        for (int i = 0; i < n; i++) {
            if (i < iBas)       partBasse += soldes.get(i);
            else if (i >= iTop) partHaute += soldes.get(i);
        }
        long milieu = somme - partBasse - partHaute;
        buf.writeInt(somme > 0 ? (int) Math.round(partBasse * 100.0 / somme) : 0);
        buf.writeInt(somme > 0 ? (int) Math.round(milieu    * 100.0 / somme) : 0);
        buf.writeInt(somme > 0 ? (int) Math.round(partHaute * 100.0 / somme) : 0);
        buf.writeInt(eco.soldeMedian());

        // Classement top 10 (hors comptes système)
        Map<String, String> casing = buildCasingMap(server, eco);
        List<Map.Entry<String, Integer>> top = allBalances.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("$"))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        buf.writeInt(top.size());
        for (Map.Entry<String, Integer> e : top) {
            buf.writeUtf(casing.getOrDefault(e.getKey(), e.getKey()));
            buf.writeInt(e.getValue());
        }

        // Crédits en tant que prêteur
        List<Loan> asLender = LoanManager.getInstance().getLoansAsLender(name);
        buf.writeInt(asLender.size());
        for (Loan l : asLender) writeLoanData(buf, l.borrower, l);

        // Crédits en tant qu'emprunteur
        List<Loan> asBorrower = LoanManager.getInstance().getLoansAsBorrower(name);
        buf.writeInt(asBorrower.size());
        for (Loan l : asBorrower) writeLoanData(buf, l.lender, l);

        // Demandes de crédit reçues (en tant que prêteur, à accepter/refuser)
        List<LoanManager.LoanRequest> reqAsLender = LoanManager.getInstance().getRequestsAsLender(name);
        buf.writeInt(reqAsLender.size());
        for (LoanManager.LoanRequest r : reqAsLender) writeLoanRequest(buf, r.borrower, r);

        // Demandes de crédit envoyées (en tant qu'emprunteur, en attente)
        List<LoanManager.LoanRequest> reqAsBorrower = LoanManager.getInstance().getRequestsAsBorrower(name);
        buf.writeInt(reqAsBorrower.size());
        for (LoanManager.LoanRequest r : reqAsBorrower) writeLoanRequest(buf, r.lender, r);

        // Joueurs connus (dropdown) — comptes système exclus
        List<String> known = eco.getSoldesKeys().stream()
            .filter(k -> !k.equalsIgnoreCase(name) && !k.startsWith("$"))
            .map(k -> casing.getOrDefault(k, k))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        buf.writeInt(known.size());
        for (String p : known) buf.writeUtf(p);

        // Virements récurrents du joueur
        List<RecurringTransfer> recurring = RecurringTransferManager.getInstance().getForPlayer(name);
        buf.writeInt(recurring.size());
        for (RecurringTransfer rt : recurring) {
            buf.writeInt(rt.id);
            buf.writeUtf(rt.to);
            buf.writeInt(rt.amount);
            buf.writeInt(rt.intervalTicks);
            buf.writeInt(rt.intervalTicks - rt.ticksSince);
        }
    }

    private static void writeLoanData(FriendlyByteBuf buf, String other, Loan l) {
        buf.writeInt(l.id);
        buf.writeUtf(other);
        buf.writeInt(l.principal);
        buf.writeLong(l.dueTimestamp);
        buf.writeInt(l.daysOverdue);
        buf.writeInt(l.totalPenalty);
        buf.writeInt(l.nextPenalty());
        buf.writeBoolean(l.repaid);
    }

    private static void writeLoanRequest(FriendlyByteBuf buf, String other, LoanManager.LoanRequest r) {
        buf.writeInt(r.id);
        buf.writeUtf(other);
        buf.writeInt(r.principal);
        buf.writeInt(r.durationDays);
        buf.writeInt(r.penaltyBase);
    }

    private static Map<String, String> buildCasingMap(MinecraftServer server, LocalEconomy eco) {
        Map<String, String> casing = new HashMap<>();
        server.getPlayerList().getPlayers().forEach(p ->
            casing.putIfAbsent(p.getName().getString().toLowerCase(), p.getName().getString()));
        MarketManager.getInstance().getAll().forEach(l ->
            casing.putIfAbsent(l.seller.toLowerCase(), l.seller));
        return casing;
    }

    // ── Quest networking ─────────────────────────────────────────────────────

    /** Ouvre le GUI Quêtes chez le joueur (/quetes, hub du Parchemin). */
    public static void sendQuestOpen(ServerPlayer player) {
        sendQuestData(player, true);
    }

    /**
     * Rafraîchit les données de quêtes sans ouvrir le GUI.
     *
     * Indispensable : ces envois partent en arrière-plan (connexion, activation
     * d'une quête de groupe, rollover de minuit). Avec l'ancien paquet unique, le
     * client ouvrait l'écran à chaque fois — les quêtes s'ouvraient toutes seules
     * au lancement du jeu.
     */
    public static void sendQuestUpdate(ServerPlayer player) {
        sendQuestData(player, false);
    }

    private static void sendQuestData(ServerPlayer player, boolean ouvrir) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeBoolean(ouvrir);
        writeFullQuestData(buf, player.getName().getString());
        NtNet.versClient(player, QuestNetworking.QUEST_OPEN, buf);
    }

    private void registerQuestNetworking() {
        NtNet.surServeur(QuestNetworking.QUEST_ACTION, (server, player, buf) -> {
            int action = buf.readInt();
            int param  = buf.readInt();   // questId or index depending on action
            String pName = player.getName().getString();
            server.execute(() -> {
                String err = switch (action) {
                    case QuestNetworking.ACTION_ACCEPT         -> QuestManager.accept(pName, param, server);
                    case QuestNetworking.ACTION_CLAIM          -> QuestManager.claim(pName, param, player, server);
                    case QuestNetworking.ACTION_CANCEL         -> QuestManager.cancel(pName, param);
                    case QuestNetworking.ACTION_COLLECT        -> QuestManager.collectReward(pName, param, player);
                    case QuestNetworking.ACTION_CANCEL_PENDING -> QuestManager.cancelPending(pName, param);
                    default                                    -> "Action inconnue.";
                };
                boolean ok = err == null;
                sendQuestResult(player, ok, ok ? "§a✅ Mis à jour !" : "§c" + err, server);
            });
        });
    }

    public static void sendQuestResult(ServerPlayer player, boolean ok, String message, MinecraftServer server) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeBoolean(ok);
        buf.writeUtf(message);
        writeFullQuestData(buf, player.getName().getString());
        NtNet.versClient(player, QuestNetworking.QUEST_RESULT, buf);
    }

    private static void writeFullQuestData(FriendlyByteBuf buf, String playerName) {
        int level = PlayerLevelManager.getLevel(playerName);
        int xp    = PlayerLevelManager.getXp(playerName);
        buf.writeInt(level);
        buf.writeInt(xp);
        buf.writeInt(PlayerLevelManager.xpToNextLevel(level));

        // Quêtes disponibles
        List<com.nouvelleterrebridge.economy.Quest> available = QuestManager.getAvailable(playerName);
        buf.writeInt(available.size());
        for (var q : available) writeQuest(buf, q);

        // Quêtes actives
        List<QuestManager.ActiveQuest> active = QuestManager.getActive(playerName);
        buf.writeInt(active.size());
        for (QuestManager.ActiveQuest aq : active) {
            buf.writeInt(aq.questId);
            writeQuest(buf, aq.snapshot != null ? aq.snapshot : new com.nouvelleterrebridge.economy.Quest());
            buf.writeInt(aq.progress);
            buf.writeBoolean(aq.turnedIn);
            buf.writeInt(aq.groupParticipants.size());
            for (String p : aq.groupParticipants) buf.writeUtf(p);
        }

        // Récompenses en attente (items à récupérer)
        List<QuestManager.PendingReward> pending = QuestManager.getPending(playerName);
        buf.writeInt(pending.size());
        for (QuestManager.PendingReward pr : pending) {
            buf.writeUtf(pr.questLabel);
            buf.writeUtf(pr.rewardItem != null ? pr.rewardItem : "");
            buf.writeInt(pr.rewardItemQty);
            buf.writeLong(pr.completedAt);
        }

        // Acceptations en attente pour les quêtes groupe
        Map<Integer, Integer> gpc = QuestManager.getGroupPendingCounts();
        buf.writeInt(gpc.size());
        for (var e : gpc.entrySet()) {
            buf.writeInt(e.getKey());
            buf.writeInt(e.getValue());
        }

        // Classements
        var topCompleted = QuestManager.getLeaderboardByCompleted(10);
        buf.writeInt(topCompleted.size());
        for (var e : topCompleted) { buf.writeUtf(e.getKey()); buf.writeInt(e.getValue()); }

        var topLevel = PlayerLevelManager.getLeaderboardByLevel(10);
        buf.writeInt(topLevel.size());
        for (var e : topLevel) { buf.writeUtf(e.getKey()); buf.writeInt(e.getValue()); }

        // Quête communautaire du jour
        QuestManager.CommunityState cs = QuestManager.getCommunity();
        boolean hasCommunity = cs != null && cs.quest != null;
        buf.writeBoolean(hasCommunity);
        if (hasCommunity) {
            buf.writeUtf(cs.quest.label);
            buf.writeUtf(cs.quest.type != null ? cs.quest.type : "");
            buf.writeUtf(cs.quest.target != null ? cs.quest.target : "");
            buf.writeInt(cs.quest.quantity);
            buf.writeInt(cs.progress);
            buf.writeInt(cs.quest.rewardShards);
            buf.writeBoolean(cs.completed);
            buf.writeInt(QuestManager.getCommunityContribution(playerName));
        }
    }

    // ── Production networking ────────────────────────────────────────────────

    /** Envoie l'état de la production au joueur (ouvre le GUI côté client). */
    public static void sendProductionOpen(ServerPlayer player) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        writeProductionData(buf, player);
        NtNet.versClient(player, ProductionNetworking.PROD_OPEN, buf);
    }

    private static void writeProductionData(FriendlyByteBuf buf, ServerPlayer player) {
        buf.writeBoolean(player.hasPermissions(2));
        Map<String, ShopThresholds.Entry> all = ShopThresholds.all();
        buf.writeInt(all.size());
        for (Map.Entry<String, ShopThresholds.Entry> e : all.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeLong(ProductionTracker.get(e.getKey()));
            buf.writeLong(e.getValue().seuil);
            buf.writeInt(e.getValue().prix);
            buf.writeInt(e.getValue().quantite);
            // Le shop ne passe plus par des annonces HDV depuis la 1.3.0 : la mise en
            // vente effective est celle du Shop Serveur, seuil et désactivation compris.
            buf.writeBoolean(ServerShopActions.estDebloque(e.getKey()));
            buf.writeBoolean(e.getValue().desactive);
            // Rachat effectif (plafonné au prix de vente) + drapeau « imposé par un
            // admin », pour que l'écran distingue une valeur choisie d'un calcul.
            buf.writeInt(ServerShopPriceManager.getBuybackPrice(e.getKey()));
            buf.writeBoolean(ServerShopPriceManager.rachatImpose(e.getKey()));
        }
    }

    private void registerProductionNetworking() {
        NtNet.surServeur(ProductionNetworking.PROD_ACTION, (server, player, buf) -> {
            int action    = buf.readInt();
            String itemId = buf.readUtf();
            int valeur    = buf.readInt();
            server.execute(() -> {
                boolean ok;
                String msg;
                String nomItem = itemId.isEmpty() ? "" : FrenchItemNames.toDisplay(itemId);
                if (!player.hasPermissions(2)) {
                    ok = false; msg = "§cRéservé aux opérateurs.";
                } else if (action == ProductionNetworking.ACTION_RESET) {
                    ProductionTracker.reset();
                    ShopThresholds.resetAll();
                    ok = true; msg = "§a✅ Production remise à zéro : compteurs, seuils et annonces auto.";
                } else if (action == ProductionNetworking.ACTION_RECHECK) {
                    // Le shop lit les seuils en direct depuis la 1.3.0 : il n'y a plus
                    // rien à « re-vérifier », l'action ne sert qu'à renvoyer un état frais.
                    ProductionShopManager.purgerAnnoncesLegacy();
                    ok = true; msg = "§a✅ Données rafraîchies.";
                } else if (action == ProductionNetworking.ACTION_RELOAD) {
                    ShopThresholds.load();
                    ProductionShopManager.purgerAnnoncesLegacy();
                    ok = true; msg = "§a✅ seuils-shop.json rechargé.";
                } else if (action == ProductionNetworking.ACTION_SET_PRICE) {
                    if (valeur <= 0) {
                        ok = false; msg = "§cPrix invalide.";
                    } else if (ShopThresholds.setPrix(itemId, valeur)) {
                        // Le prix de base du shop est une copie figée : sans resync,
                        // la correction resterait sans effet sur un item déjà échangé.
                        ServerShopPriceManager.resyncBasePrices();
                        ok = true; msg = "§a✅ " + nomItem + " : prix fixé à " + valeur + " ◆.";
                    } else {
                        ok = false; msg = "§cItem absent du catalogue.";
                    }
                } else if (action == ProductionNetworking.ACTION_PURGE_MARCHE) {
                    // Remet les compteurs de transactions a zero. Le flux cumule
                    // n'a aucun amortissement : apres un exploit, il resterait au
                    // plafond indefiniment sans cette purge.
                    ServerShopPriceManager.reset();
                    ServerShopPriceManager.resyncBasePrices();
                    ok = true;
                    msg = "§a✅ Marché purgé — flux et demande remis à zéro. "
                        + "§7Soldes, production et seuils intacts. Sauvegarde conservée.";
                } else if (action == ProductionNetworking.ACTION_SET_RACHAT) {
                    if (valeur < 0) {
                        ok = false; msg = "§cPrix invalide.";
                    } else if (ShopThresholds.setPrixRachat(itemId, valeur)) {
                        int effectif = ServerShopPriceManager.getBuybackPrice(itemId);
                        ok = true;
                        if (valeur == 0) {
                            msg = "§a✅ " + nomItem + " : rachat repassé en automatique ("
                                + effectif + " ◆).";
                        } else if (effectif < valeur) {
                            // Plafonné : le dire, sinon l'admin croirait sa valeur retenue
                            msg = "§e⚠ " + nomItem + " : rachat ramené à " + effectif
                                + " ◆ (plafonné au prix de vente).";
                        } else {
                            msg = "§a✅ " + nomItem + " : rachat fixé à " + effectif + " ◆.";
                        }
                    } else {
                        ok = false; msg = "§cItem absent du catalogue.";
                    }
                } else if (action == ProductionNetworking.ACTION_TOGGLE) {
                    Boolean desactive = ShopThresholds.toggleDesactive(itemId);
                    if (desactive == null) {
                        ok = false; msg = "§cItem absent du catalogue.";
                    } else {
                        ok = true;
                        msg = desactive ? "§e⏸ " + nomItem + " retiré de la vente."
                                        : "§a✅ " + nomItem + " remis en vente.";
                    }
                } else if (action == ProductionNetworking.ACTION_DELETE) {
                    if (ShopThresholds.supprimer(itemId)) {
                        ok = true; msg = "§a✅ " + nomItem + " supprimé du catalogue.";
                    } else {
                        ok = false; msg = "§cItem absent du catalogue.";
                    }
                } else {
                    ok = false; msg = "§cAction inconnue.";
                }
                FriendlyByteBuf resp = com.nouvelleterrebridge.network.NtNet.buffer();
                resp.writeBoolean(ok);
                resp.writeUtf(msg);
                writeProductionData(resp, player);
                NtNet.versClient(player, ProductionNetworking.PROD_RESULT, resp);
            });
        });
    }

    // ── Conflit networking ───────────────────────────────────────────────────

    private void registerConflitNetworking() {
        NtNet.surServeur(ConflitNetworking.CONFLIT_ACTION, (server, player, buf) -> {
            String cible  = buf.readUtf();
            String raison = buf.readUtf();
            server.execute(() -> {
                String pseudo = player.getName().getString();
                boolean ok;
                String msg;
                if (pseudo.equalsIgnoreCase(cible)) {
                    ok = false; msg = "Vous ne pouvez pas vous déclarer conflit à vous-même.";
                } else if (raison.trim().length() < 3) {
                    ok = false; msg = "Raison trop courte.";
                } else {
                    ok = true;
                    msg = "⚔ Conflit déclaré contre " + cible + " — le Conseil des Fondateurs est alerté.";
                    Map<String, Object> data = new HashMap<>();
                    data.put("player", pseudo);
                    data.put("target", cible);
                    data.put("reason", raison.trim());
                    EventDispatcher.envoyer("CONFLICT_DECLARED", data);
                }
                FriendlyByteBuf resp = com.nouvelleterrebridge.network.NtNet.buffer();
                resp.writeBoolean(ok);
                resp.writeUtf(msg);
                NtNet.versClient(player, ConflitNetworking.CONFLIT_RESULT, resp);
            });
        });
    }

    // ── Registre networking ──────────────────────────────────────────────────

    private void registerRegistreNetworking() {
        NtNet.surServeur(RegistreNetworking.REGISTRE_DETAIL_REQUEST, (server, player, buf) -> {
                String pseudo = buf.readUtf();
                EventDispatcher.fetchPersonnageDetail(pseudo, server, detail -> {
                    FriendlyByteBuf resp = com.nouvelleterrebridge.network.NtNet.buffer();
                    if (detail == null) {
                        resp.writeBoolean(false);
                        NtNet.versClient(player, RegistreNetworking.REGISTRE_DETAIL, resp);
                        return;
                    }
                    resp.writeBoolean(true);
                    resp.writeUtf(sVal(detail, "nom_rp"));
                    resp.writeUtf(sVal(detail, "pseudo_mc"));
                    resp.writeBoolean(bVal(detail, "en_ligne"));
                    resp.writeUtf(sVal(detail, "metier"));
                    resp.writeInt(iVal(detail, "age"));
                    resp.writeUtf(sVal(detail, "origine"));
                    resp.writeUtf(sVal(detail, "specialite"));
                    resp.writeUtf(sVal(detail, "traits"));
                    resp.writeUtf(sVal(detail, "passe"));
                    resp.writeUtf(sVal(detail, "description_physique"));
                    resp.writeUtf(sVal(detail, "description_personnage"));
                    resp.writeUtf(sVal(detail, "objectifs"));
                    resp.writeUtf(sVal(detail, "citation"));
                    NtNet.versClient(player, RegistreNetworking.REGISTRE_DETAIL, resp);
                });
            });
    }

    private static String sVal(Map<String, Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : "";
    }
    private static boolean bVal(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Boolean b && b;
    }
    private static int iVal(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Number n ? n.intValue() : 0;
    }

    private static void writeQuest(FriendlyByteBuf buf, com.nouvelleterrebridge.economy.Quest q) {
        buf.writeInt(q.id);
        buf.writeUtf(q.type       != null ? q.type       : "");
        buf.writeUtf(q.target     != null ? q.target     : "");
        buf.writeInt(q.quantity);
        buf.writeInt(q.levelRequired);
        buf.writeInt(q.maxPlayers);
        buf.writeUtf(q.rewardType != null ? q.rewardType : "SHARDS");
        buf.writeInt(q.rewardShards);
        buf.writeUtf(q.rewardItem != null ? q.rewardItem : "");
        buf.writeInt(q.rewardItemQty);
        buf.writeInt(q.rewardXp);
        buf.writeInt(q.costShards);
        buf.writeUtf(q.label      != null ? q.label      : "");
        buf.writeLong(q.expiresAt);
        List<String> tags = q.tags != null ? q.tags : List.of();
        buf.writeInt(tags.size());
        for (String t : tags) buf.writeUtf(t);
    }

}
