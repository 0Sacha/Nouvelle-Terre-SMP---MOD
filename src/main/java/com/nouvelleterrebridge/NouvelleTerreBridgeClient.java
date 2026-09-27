package com.nouvelleterrebridge;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.client.BalanceHudOverlay;
import com.nouvelleterrebridge.client.BankScreen;
import com.nouvelleterrebridge.client.ClientConfig;
import com.nouvelleterrebridge.client.DiscordRPCManager;
import com.nouvelleterrebridge.client.HdvScreen;
import com.nouvelleterrebridge.client.HudEditorScreen;
import com.nouvelleterrebridge.client.ConflitScreen;
import com.nouvelleterrebridge.client.NotificationHud;
import com.nouvelleterrebridge.client.MarcheScreen;
import com.nouvelleterrebridge.client.ProductionScreen;
import com.nouvelleterrebridge.client.ServerAdminScreen;
import com.nouvelleterrebridge.client.RegistreScreen;
import com.nouvelleterrebridge.client.hud.BalanceWidget;
import com.nouvelleterrebridge.client.hud.BiomeWidget;
import com.nouvelleterrebridge.client.hud.CoordsWidget;
import com.nouvelleterrebridge.client.hud.DimensionWidget;
import com.nouvelleterrebridge.client.hud.EffetsWidget;
import com.nouvelleterrebridge.client.hud.FpsWidget;
import com.nouvelleterrebridge.client.hud.HudWidget;
import com.nouvelleterrebridge.client.hud.NotificationWidget;
import com.nouvelleterrebridge.client.hud.TimeWidget;
import com.nouvelleterrebridge.client.hud.QuestWidget;
import com.nouvelleterrebridge.client.QuetesScreen;
import com.nouvelleterrebridge.client.WikiScreen;
import com.nouvelleterrebridge.network.BankNetworking;
import com.nouvelleterrebridge.network.ConflitNetworking;
import com.nouvelleterrebridge.network.HdvNetworking;
import com.nouvelleterrebridge.network.ProductionNetworking;
import com.nouvelleterrebridge.network.QuestNetworking;
import com.nouvelleterrebridge.network.ServiceNetworking;
import com.nouvelleterrebridge.network.RegistreNetworking;
import com.nouvelleterrebridge.network.WikiNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NouvelleTerreBridgeClient {

    /** Mis à true par InGameHudMixin/DebugHudMixin côté Fabric — lu directement
     *  depuis l'overlay de debug natif côté NeoForge, ce champ ne sert plus qu'à
     *  la lecture depuis le rendu du HUD (voir plus bas). */
    public static volatile boolean debugHudActive = false;

    /** Cache client uuid→nom_rp, peuplé par NT_NOM_RP depuis le serveur. */
    public static final ConcurrentHashMap<UUID, String> nomsRP = new ConcurrentHashMap<>();

    /** Keybinding éditeur HUD — exposé pour affichage dans WikiScreen. */
    public static KeyMapping hudKey;

    /**
     * Appelé explicitement depuis le constructeur de {@link NouvelleTerreBridge},
     * derrière un test {@code FMLEnvironment.dist.isClient()}. ⚠ La découverte
     * automatique par annotation ({@code @EventBusSubscriber}) ne s'est pas montrée
     * fiable ici : ce mod garde un unique sourceSet client+serveur (comme côté
     * Fabric) au lieu du sourceSet client séparé que NeoForge attend pour ce
     * mécanisme, et {@code onClientSetup} n'était jamais invoqué. L'enregistrement
     * explicite, identique dans l'esprit à {@link NtNet#enregistrer}, fonctionne
     * dans tous les cas.
     */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NouvelleTerreBridgeClient::onClientSetup);
        modEventBus.addListener(NouvelleTerreBridgeClient::onRegisterGuiLayers);
        modEventBus.addListener(NouvelleTerreBridgeClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(GameEvents::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(GameEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(GameEvents::onClientTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ClientConfig.load();

        // ── HUD widgets ───────────────────────────────────────────────────────
        HudEditorScreen.WIDGETS.add(new BalanceWidget());
        HudEditorScreen.WIDGETS.add(new CoordsWidget());
        HudEditorScreen.WIDGETS.add(new TimeWidget());
        HudEditorScreen.WIDGETS.add(new FpsWidget());
        HudEditorScreen.WIDGETS.add(new BiomeWidget());
        HudEditorScreen.WIDGETS.add(new DimensionWidget());
        HudEditorScreen.WIDGETS.add(new EffetsWidget());
        HudEditorScreen.WIDGETS.add(new NotificationWidget());
        HudEditorScreen.WIDGETS.add(new QuestWidget());
        HudEditorScreen.loadAll();

        registerNetworking();
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        NotificationHud.register(event);
        event.registerAboveAll(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NouvelleTerreBridge.MOD_ID, "hud"),
            (ctx, delta) -> {
                Minecraft mc = Minecraft.getInstance();
                FpsWidget.onFrame();
                if (mc.player == null) return;

                // F3 ouvert → on cache tout
                if (mc.getDebugOverlay().showDebugScreen()) return;

                // Éditeur HUD ouvert → il rend les widgets lui-même
                if (mc.screen instanceof HudEditorScreen) return;

                boolean chatOpen = mc.screen instanceof ChatScreen;

                // Autre écran (HDV, Bank, etc.) → on cache tout
                if (mc.screen != null && !chatOpen) return;

                int sh = mc.getWindow().getGuiScaledHeight();
                for (HudWidget w : HudEditorScreen.WIDGETS) {
                    if (!w.enabled || w.isDragOnly()) continue;
                    // Chat ouvert → cacher les widgets qui chevauchent la barre de saisie
                    if (chatOpen && w.getPixelY(sh, mc) + w.getHeight(mc) > sh - 15) continue;
                    w.render(ctx, mc);
                }
            }
        );
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        hudKey = new KeyMapping(
            "key.nouvelle-terre-bridge.hud_editor",
            GLFW.GLFW_KEY_H,
            "key.categories.nouvelle-terre-bridge"
        );
        event.register(hudKey);
    }

    private static class GameEvents {

        static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            Minecraft client = Minecraft.getInstance();
            ServerData info = client.getCurrentServer();
            if (info != null) DiscordRPCManager.INSTANCE.onJoin(info.ip);
        }

        static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            DiscordRPCManager.INSTANCE.onLeave();
            nomsRP.clear();
        }

        static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            DiscordRPCManager.INSTANCE.tick();
            while (hudKey.consumeClick()) {
                if (client.screen == null)
                    client.setScreen(new HudEditorScreen());
            }
        }
    }

    private static void registerNetworking() {

        NtNet.surClient(HdvNetworking.NT_TOAST, (client, buf) -> {
            int color = buf.readInt();
            int count = buf.readInt();
            String[] lines = new String[count];
            for (int i = 0; i < count; i++) lines[i] = buf.readUtf();
            client.execute(() -> NotificationHud.push(color, lines));
        });

        NtNet.surClient(HdvNetworking.NT_BALANCE, (client, buf) -> {
            int balance = buf.readInt();
            client.execute(() -> BalanceHudOverlay.cachedBalance = balance);
        });

        NtNet.surClient(HdvNetworking.NT_NOM_RP, (client, buf) -> {
            UUID uuid  = buf.readUUID();
            String nom = buf.readUtf();
            nomsRP.put(uuid, nom);
        });

        NtNet.surClient(HdvNetworking.HDV_OPEN, (client, buf) -> {
            int balance = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            client.execute(() -> {
                BalanceHudOverlay.cachedBalance = balance;
                client.setScreen(new HdvScreen(balance, listings));
            });
        });

        NtNet.surClient(ServiceNetworking.MARCHE_OPEN, (client, buf) -> {
                // ouvrir = false : simple rafraîchissement (message reçu, commande
                // validée…). Ouvrir l'écran d'office ferait surgir LeBonCube
                // par-dessus le jeu à chaque notification.
                boolean ouvrir = buf.readBoolean();
                MarcheEtat e = lireMarche(buf);
                client.execute(() -> {
                    if (client.screen instanceof MarcheScreen ms)
                        ms.maj(e.balance, e.categories, e.annonces, e.prestations, e.commandes, e.archives);
                    else if (ouvrir)
                        client.setScreen(new MarcheScreen(e.balance, e.categories, e.annonces,
                            e.prestations, e.commandes, e.archives));
                });
            });

        NtNet.surClient(ServiceNetworking.MARCHE_RESULT, (client, buf) -> {
                boolean ok  = buf.readBoolean();
                String  msg = buf.readUtf();
                MarcheEtat e = lireMarche(buf);
                client.execute(() -> {
                    if (client.screen instanceof MarcheScreen ms)
                        ms.handleResult(ok, msg, e.balance, e.categories, e.annonces,
                                        e.prestations, e.commandes, e.archives);
                });
            });

        NtNet.surClient(ServiceNetworking.ADMIN_OPEN, (client, buf) -> {
                int soldeServeur   = buf.readInt();
                int soldeSequestre = buf.readInt();
                long masse         = buf.readLong();
                int joueursConnus  = buf.readInt();
                int median         = buf.readInt();
                int enLigne        = buf.readInt();
                int annoncesHdv    = buf.readInt();
                int annoncesMarche = buf.readInt();
                double inflation   = buf.readDouble();
                int n = buf.readInt();
                List<ServerAdminScreen.LitigeData> litiges = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    litiges.add(new ServerAdminScreen.LitigeData(buf.readInt(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(),
                        buf.readUtf()));
                client.execute(() -> {
                    if (client.screen instanceof ServerAdminScreen sa)
                        sa.maj(soldeServeur, soldeSequestre, masse, joueursConnus, median,
                               enLigne, annoncesHdv, annoncesMarche, inflation, litiges);
                    else
                        client.setScreen(new ServerAdminScreen(soldeServeur, soldeSequestre, masse,
                            joueursConnus, median, enLigne, annoncesHdv, annoncesMarche,
                            inflation, litiges));
                });
            });

        NtNet.surClient(com.nouvelleterrebridge.network.HubNetworking.HUB_OPEN, (client, buf) ->
                client.execute(() -> client.setScreen(new com.nouvelleterrebridge.client.HubScreen())));

        NtNet.surClient(com.nouvelleterrebridge.network.ShopNetworking.SHOP_OPEN, (client, buf) -> {
                int balance = buf.readInt();
                var shopEntries = readShopEntries(buf);
                client.execute(() -> {
                    BalanceHudOverlay.cachedBalance = balance;
                    client.setScreen(new com.nouvelleterrebridge.client.ServerShopScreen(balance, shopEntries));
                });
            });

        NtNet.surClient(com.nouvelleterrebridge.network.ShopNetworking.SHOP_RESULT, (client, buf) -> {
                boolean ok  = buf.readBoolean();
                String  msg = buf.readUtf();
                int balance = buf.readInt();
                var shopEntries = readShopEntries(buf);
                client.execute(() -> {
                    BalanceHudOverlay.cachedBalance = balance;
                    if (client.screen instanceof com.nouvelleterrebridge.client.ServerShopScreen s) {
                        s.handleResult(ok, msg, balance, shopEntries);
                    }
                });
            });

        NtNet.surClient(HdvNetworking.NT_VERSION, (client, buf) -> {
            String serverVer = buf.readUtf();
            String clientVer = ModList.get()
                .getModContainerById(NouvelleTerreBridge.NEOFORGE_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
            if (!serverVer.equals(clientVer)) {
                client.execute(() -> {
                    if (client.player == null) return;
                    String url = "https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD/releases/latest";
                    MutableComponent link = Component.literal("§9§n[Télécharger v" + serverVer + "]")
                        .withStyle(s -> s
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
                    client.player.displayClientMessage(Component.literal("§e[Nouvelle Terre] §cMod obsolète §7— client §f"
                        + clientVer + " §7≠ serveur §f" + serverVer + " §7— ").append(link), false);
                });
            }
        });

        NtNet.surClient(BankNetworking.BANK_OPEN, (client, buf) -> {
            BankScreen screen = readBankPacket(buf);
            client.execute(() -> client.setScreen(screen));
        });

        NtNet.surClient(BankNetworking.BANK_RESULT, (client, buf) -> {
            boolean ok       = buf.readBoolean();
            String  message  = buf.readUtf();
            int balance      = buf.readInt();
            int ticksReward  = buf.readInt();
            List<BankScreen.TxData>           txs       = readBankTxs(buf);
            int totalShards  = buf.readInt();
            int playerCount  = buf.readInt();
            BankScreen.WealthData wealth = readWealth(buf);
            List<BankScreen.LeaderboardEntry> lb        = readLeaderboard(buf);
            List<BankScreen.LoanData>         asLender  = readLoans(buf);
            List<BankScreen.LoanData>         asBorrow  = readLoans(buf);
            List<BankScreen.LoanRequestData>  reqLender = readLoanRequests(buf);
            List<BankScreen.LoanRequestData>  reqBorrow = readLoanRequests(buf);
            List<String>                      known     = readStringList(buf);
            List<BankScreen.RecurringData>    recurring = readBankRecurring(buf);
            client.execute(() -> {
                if (client.screen instanceof BankScreen screen) {
                    screen.handleResult(ok, message, balance, ticksReward, txs,
                        totalShards, playerCount, wealth, lb, asLender, asBorrow, reqLender, reqBorrow, known, recurring);
                }
            });
        });

        NtNet.surClient(HdvNetworking.HDV_RESULT, (client, buf) -> {
            boolean ok      = buf.readBoolean();
            String  message = buf.readUtf();
            int balance     = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            client.execute(() -> {
                BalanceHudOverlay.cachedBalance = balance;
                if (client.screen instanceof HdvScreen screen) {
                    screen.handleResult(ok, message, balance, listings);
                }
            });
        });

        NtNet.surClient(QuestNetworking.QUEST_OPEN, (client, buf) -> {
            // ouvrir = false pour les rafraîchissements de fond (connexion, quête de
            // groupe activée, rollover) : sans ce drapeau, l'écran des quêtes
            // s'ouvrait tout seul au lancement du jeu.
            boolean ouvrir = buf.readBoolean();
            int level = buf.readInt(), xp = buf.readInt(), xpNext = buf.readInt();
            List<QuetesScreen.QuestData>         av  = readQuestList(buf);
            List<QuetesScreen.ActiveQuestData>   ac  = readActiveQuests(buf);
            List<QuetesScreen.PendingRewardData> pe  = readPendingRewards(buf);
            Map<Integer, Integer>                gp  = readIntIntMap(buf);
            List<QuetesScreen.LeaderboardEntry>  lbC = readLb(buf);
            List<QuetesScreen.LeaderboardEntry>  lbL = readLb(buf);
            QuetesScreen.CommunityData           cm  = readCommunity(buf);
            client.execute(() -> {
                updateQuestWidget(ac);
                if (client.screen instanceof QuetesScreen s)
                    s.update(level, xp, xpNext, av, ac, pe, gp, lbC, lbL, cm);
                else if (ouvrir)
                    client.setScreen(new QuetesScreen(level, xp, xpNext, av, ac, pe, gp, lbC, lbL, cm));
            });
        });

        NtNet.surClient(ProductionNetworking.PROD_OPEN, (client, buf) -> {
            boolean isOp = buf.readBoolean();
            List<ProductionScreen.ProdEntry> list = readProdEntries(buf);
            client.execute(() -> client.setScreen(new ProductionScreen(isOp, list)));
        });

        NtNet.surClient(ProductionNetworking.PROD_RESULT, (client, buf) -> {
            boolean ok      = buf.readBoolean();
            String  message = buf.readUtf();
            boolean isOp    = buf.readBoolean();
            List<ProductionScreen.ProdEntry> list = readProdEntries(buf);
            client.execute(() -> {
                if (client.screen instanceof ProductionScreen s)
                    s.handleResult(ok, message, isOp, list);
            });
        });

        NtNet.surClient(ConflitNetworking.CONFLIT_OPEN, (client, buf) -> {
            List<String> joueurs = readStringList(buf);
            client.execute(() -> client.setScreen(new ConflitScreen(joueurs)));
        });

        NtNet.surClient(ConflitNetworking.CONFLIT_RESULT, (client, buf) -> {
            boolean ok      = buf.readBoolean();
            String  message = buf.readUtf();
            client.execute(() -> {
                if (client.screen instanceof ConflitScreen && ok) client.setScreen(null);
                NotificationHud.push(ok ? 0xFFBF2040 : 0xFFE8A838, message);
            });
        });

        NtNet.surClient(WikiNetworking.WIKI_OPEN, (client, buf) ->
            client.execute(() -> client.setScreen(new WikiScreen())));

        NtNet.surClient(RegistreNetworking.REGISTRE_OPEN, (client, buf) -> {
            int count = buf.readInt();
            List<RegistreScreen.PersonnageData> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
                list.add(new RegistreScreen.PersonnageData(buf.readUtf(), buf.readUtf(), buf.readBoolean()));
            client.execute(() -> client.setScreen(new RegistreScreen(list)));
        });

        NtNet.surClient(RegistreNetworking.REGISTRE_DETAIL, (client, buf) -> {
            boolean ok = buf.readBoolean();
            if (!ok) {
                client.execute(() -> { if (client.screen instanceof RegistreScreen s) s.onDetailError(); });
                return;
            }
            var detail = new RegistreScreen.DetailData(
                buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                buf.readUtf(), buf.readInt(),    buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
            );
            client.execute(() -> { if (client.screen instanceof RegistreScreen s) s.onDetailReceived(detail); });
        });

        NtNet.surClient(QuestNetworking.QUEST_RESULT, (client, buf) -> {
            boolean ok      = buf.readBoolean();
            String  message = buf.readUtf();
            int level = buf.readInt(), xp = buf.readInt(), xpNext = buf.readInt();
            List<QuetesScreen.QuestData>         av  = readQuestList(buf);
            List<QuetesScreen.ActiveQuestData>   ac  = readActiveQuests(buf);
            List<QuetesScreen.PendingRewardData> pe  = readPendingRewards(buf);
            Map<Integer, Integer>                gp  = readIntIntMap(buf);
            List<QuetesScreen.LeaderboardEntry>  lbC = readLb(buf);
            List<QuetesScreen.LeaderboardEntry>  lbL = readLb(buf);
            QuetesScreen.CommunityData           cm  = readCommunity(buf);
            int color = ok ? 0xFF2EAD6B : 0xFFBF2040;
            client.execute(() -> {
                updateQuestWidget(ac);
                if (client.screen instanceof QuetesScreen s)
                    s.update(level, xp, xpNext, av, ac, pe, gp, lbC, lbL, cm);
                NotificationHud.push(color, message);
            });
        });
    }

    // ── Helpers lecture paquets ───────────────────────────────────────────────

    /** État complet du LeBonCube tel qu'envoyé par le serveur. */
    private record MarcheEtat(int balance,
                              List<String> categories,
                              List<MarcheScreen.AnnonceData> annonces,
                              List<MarcheScreen.CommandeData> prestations,
                              List<MarcheScreen.CommandeData> commandes,
                              List<MarcheScreen.CommandeData> archives) {}

    private static MarcheEtat lireMarche(FriendlyByteBuf buf) {
        int balance = buf.readInt();
        int nc = buf.readInt();
        List<String> categories = new ArrayList<>(nc);
        for (int i = 0; i < nc; i++) categories.add(buf.readUtf());
        int n = buf.readInt();
        List<MarcheScreen.AnnonceData> annonces = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            annonces.add(new MarcheScreen.AnnonceData(
                buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readUtf(),
                buf.readLong(), buf.readFloat(), buf.readInt()));
        return new MarcheEtat(balance, categories, annonces,
            lireCommandes(buf), lireCommandes(buf), lireCommandes(buf));
    }

    private static List<MarcheScreen.CommandeData> lireCommandes(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<MarcheScreen.CommandeData> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = buf.readInt();
            String titre = buf.readUtf();
            String client = buf.readUtf();
            String prestataire = buf.readUtf();
            int prix = buf.readInt();
            int acompte = buf.readInt();
            int sequestre = buf.readInt();
            String statut = buf.readUtf();
            boolean vp = buf.readBoolean();
            boolean vc = buf.readBoolean();
            String annulPar = buf.readUtf();
            long creeLe = buf.readLong();
            long termineeLe = buf.readLong();
            int note = buf.readInt();
            String avis = buf.readUtf();
            int nm = buf.readInt();
            List<MarcheScreen.MessageData> msgs = new ArrayList<>(nm);
            for (int j = 0; j < nm; j++)
                msgs.add(new MarcheScreen.MessageData(buf.readUtf(), buf.readUtf(), buf.readLong()));
            out.add(new MarcheScreen.CommandeData(id, titre, client, prestataire, prix, acompte,
                sequestre, statut, vp, vc, annulPar, creeLe, termineeLe, note, avis, msgs));
        }
        return out;
    }

    private static List<HdvScreen.ListingData> readListings(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<HdvScreen.ListingData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new HdvScreen.ListingData(buf.readInt(), buf.readUtf(), buf.readUtf(),
                                               buf.readInt(), buf.readInt(), buf.readUtf()));
        return list;
    }

    private static List<com.nouvelleterrebridge.client.ServerShopScreen.ShopEntry> readShopEntries(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<com.nouvelleterrebridge.client.ServerShopScreen.ShopEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new com.nouvelleterrebridge.client.ServerShopScreen.ShopEntry(
                buf.readUtf(), buf.readInt(), buf.readInt(), buf.readLong()));
        return list;
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(buf.readUtf());
        return list;
    }

    private static BankScreen readBankPacket(FriendlyByteBuf buf) {
        int balance     = buf.readInt();
        int ticksReward = buf.readInt();
        List<BankScreen.TxData>           txs       = readBankTxs(buf);
        int totalShards  = buf.readInt();
        int playerCount  = buf.readInt();
        BankScreen.WealthData wealth = readWealth(buf);
        List<BankScreen.LeaderboardEntry> lb        = readLeaderboard(buf);
        List<BankScreen.LoanData>         asLender  = readLoans(buf);
        List<BankScreen.LoanData>         asBorrow  = readLoans(buf);
        List<BankScreen.LoanRequestData>  reqLender = readLoanRequests(buf);
        List<BankScreen.LoanRequestData>  reqBorrow = readLoanRequests(buf);
        List<String>                      known     = readStringList(buf);
        List<BankScreen.RecurringData>    recurring = readBankRecurring(buf);
        return new BankScreen(balance, ticksReward, txs, totalShards, playerCount, wealth,
            lb, asLender, asBorrow, reqLender, reqBorrow, known, recurring);
    }

    /** Répartition des richesses : 5 tranches + parts détenues + médiane. */
    private static BankScreen.WealthData readWealth(FriendlyByteBuf buf) {
        int[] tranches = new int[5];
        for (int i = 0; i < tranches.length; i++) tranches[i] = buf.readInt();
        int partBasse = buf.readInt();
        int partMoyenne = buf.readInt();
        int partHaute = buf.readInt();
        int median = buf.readInt();
        return new BankScreen.WealthData(tranches, partBasse, partMoyenne, partHaute, median);
    }

    private static List<BankScreen.LoanRequestData> readLoanRequests(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.LoanRequestData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.LoanRequestData(
                buf.readInt(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt()));
        return list;
    }

    private static List<BankScreen.RecurringData> readBankRecurring(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.RecurringData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.RecurringData(buf.readInt(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt()));
        return list;
    }

    private static List<BankScreen.TxData> readBankTxs(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.TxData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.TxData(buf.readInt(), buf.readUtf(), buf.readInt(), buf.readLong()));
        return list;
    }

    private static List<BankScreen.LeaderboardEntry> readLeaderboard(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.LeaderboardEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.LeaderboardEntry(buf.readUtf(), buf.readInt()));
        return list;
    }

    private static List<BankScreen.LoanData> readLoans(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.LoanData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.LoanData(
                buf.readInt(), buf.readUtf(), buf.readInt(), buf.readLong(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));
        return list;
    }

    private static List<ProductionScreen.ProdEntry> readProdEntries(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ProductionScreen.ProdEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new ProductionScreen.ProdEntry(
                buf.readUtf(), buf.readLong(), buf.readLong(),
                buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readInt(), buf.readBoolean()));
        return list;
    }

    private static QuetesScreen.CommunityData readCommunity(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new QuetesScreen.CommunityData(
            buf.readUtf(), buf.readUtf(), buf.readUtf(),
            buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readInt());
    }

    private static List<QuetesScreen.QuestData> readQuestList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<QuetesScreen.QuestData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(readOneQuest(buf));
        return list;
    }

    private static QuetesScreen.QuestData readOneQuest(FriendlyByteBuf buf) {
        int id = buf.readInt(); String type = buf.readUtf(); String target = buf.readUtf();
        int qty = buf.readInt(); int lvl = buf.readInt(); int maxP = buf.readInt();
        String rt = buf.readUtf(); int rSh = buf.readInt(); String rItem = buf.readUtf();
        int rQty = buf.readInt(); int rXp = buf.readInt(); int cost = buf.readInt();
        String label = buf.readUtf(); long exp = buf.readLong();
        int tc = buf.readInt(); List<String> tags = new ArrayList<>(tc);
        for (int i = 0; i < tc; i++) tags.add(buf.readUtf());
        return new QuetesScreen.QuestData(id, type, target, qty, lvl, maxP, rt, rSh, rItem, rQty, rXp, cost, label, exp, tags);
    }

    private static List<QuetesScreen.ActiveQuestData> readActiveQuests(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<QuetesScreen.ActiveQuestData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int questId       = buf.readInt();
            QuetesScreen.QuestData snap = readOneQuest(buf);
            int progress      = buf.readInt();
            boolean turnedIn  = buf.readBoolean();
            int pc            = buf.readInt();
            List<String> parts = new ArrayList<>(pc);
            for (int j = 0; j < pc; j++) parts.add(buf.readUtf());
            list.add(new QuetesScreen.ActiveQuestData(questId, snap, progress, turnedIn, parts));
        }
        return list;
    }

    private static List<QuetesScreen.PendingRewardData> readPendingRewards(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<QuetesScreen.PendingRewardData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new QuetesScreen.PendingRewardData(
                buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readLong()));
        return list;
    }

    private static Map<Integer, Integer> readIntIntMap(FriendlyByteBuf buf) {
        int count = buf.readInt();
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < count; i++) map.put(buf.readInt(), buf.readInt());
        return map;
    }

    private static List<QuetesScreen.LeaderboardEntry> readLb(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<QuetesScreen.LeaderboardEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new QuetesScreen.LeaderboardEntry(buf.readUtf(), buf.readInt()));
        return list;
    }

    private static void updateQuestWidget(List<QuetesScreen.ActiveQuestData> active) {
        QuestWidget.activeLabels.clear();
        QuestWidget.activeProgresses.clear();
        QuestWidget.activeGroups.clear();
        for (QuetesScreen.ActiveQuestData aq : active) {
            if (aq.snapshot() != null) {
                QuestWidget.activeLabels.add(
                    QuetesScreen.targetName(aq.snapshot().type(), aq.snapshot().target()));
                QuestWidget.activeProgresses.add(aq.progress() + "/" + aq.snapshot().quantity());
                QuestWidget.activeGroups.add(aq.snapshot().maxPlayers() > 1);
            }
        }
    }
}
