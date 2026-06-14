package com.nouvelleterrebridge;

import com.nouvelleterrebridge.client.BalanceHudOverlay;
import com.nouvelleterrebridge.client.BankScreen;
import com.nouvelleterrebridge.client.ClientConfig;
import com.nouvelleterrebridge.client.DiscordRPCManager;
import com.nouvelleterrebridge.client.HdvScreen;
import com.nouvelleterrebridge.client.HudEditorScreen;
import com.nouvelleterrebridge.client.NotificationHud;
import com.nouvelleterrebridge.client.hud.ArmureWidget;
import com.nouvelleterrebridge.client.hud.BalanceWidget;
import com.nouvelleterrebridge.client.hud.BiomeWidget;
import com.nouvelleterrebridge.client.hud.CoordsWidget;
import com.nouvelleterrebridge.client.hud.DimensionWidget;
import com.nouvelleterrebridge.client.hud.EffetsWidget;
import com.nouvelleterrebridge.client.hud.FpsWidget;
import com.nouvelleterrebridge.client.hud.HudWidget;
import com.nouvelleterrebridge.client.hud.NotificationWidget;
import com.nouvelleterrebridge.client.hud.NourritureWidget;
import com.nouvelleterrebridge.client.hud.SanteWidget;
import com.nouvelleterrebridge.client.hud.TimeWidget;
import com.nouvelleterrebridge.client.hud.XpWidget;
import com.nouvelleterrebridge.client.QuetesScreen;
import com.nouvelleterrebridge.network.BankNetworking;
import com.nouvelleterrebridge.network.HdvNetworking;
import com.nouvelleterrebridge.network.QuestNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class NouvelleTerreBridgeClient implements ClientModInitializer {

    /** Set to true by DebugHudMixin when F3 debug screen is rendering this frame. */
    public static volatile boolean debugHudActive = false;

    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        NotificationHud.register();

        // ── HUD widgets ───────────────────────────────────────────────────────
        HudEditorScreen.WIDGETS.add(new BalanceWidget());
        HudEditorScreen.WIDGETS.add(new CoordsWidget());
        HudEditorScreen.WIDGETS.add(new TimeWidget());
        HudEditorScreen.WIDGETS.add(new SanteWidget());
        HudEditorScreen.WIDGETS.add(new NourritureWidget());
        HudEditorScreen.WIDGETS.add(new ArmureWidget());
        HudEditorScreen.WIDGETS.add(new XpWidget());
        HudEditorScreen.WIDGETS.add(new FpsWidget());
        HudEditorScreen.WIDGETS.add(new BiomeWidget());
        HudEditorScreen.WIDGETS.add(new DimensionWidget());
        HudEditorScreen.WIDGETS.add(new EffetsWidget());
        HudEditorScreen.WIDGETS.add(new NotificationWidget());
        HudEditorScreen.loadAll();

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            FpsWidget.onFrame();
            if (mc.player == null) return;

            // F3 ouvert → on cache tout (flag mis par DebugHudMixin / InGameHudMixin)
            if (NouvelleTerreBridgeClient.debugHudActive) return;

            // Éditeur HUD ouvert → il rend les widgets lui-même
            if (mc.currentScreen instanceof HudEditorScreen) return;

            boolean chatOpen = mc.currentScreen instanceof ChatScreen;

            // Autre écran (HDV, Bank, etc.) → on cache tout
            if (mc.currentScreen != null && !chatOpen) return;

            int sh = mc.getWindow().getScaledHeight();
            for (HudWidget w : HudEditorScreen.WIDGETS) {
                if (!w.enabled || w.isDragOnly()) continue;
                // Chat ouvert → cacher les widgets qui chevauchent la barre de saisie
                if (chatOpen && w.getPixelY(sh, mc) + w.getHeight(mc) > sh - 15) continue;
                w.render(ctx, mc);
            }
        });

        // ── Touche éditeur HUD ────────────────────────────────────────────────
        KeyBinding hudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.nouvelle-terre-bridge.hud_editor",
            GLFW.GLFW_KEY_H,
            "key.categories.nouvelle-terre-bridge"
        ));

        // ── Events ────────────────────────────────────────────────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo info = client.getCurrentServerEntry();
            if (info != null) DiscordRPCManager.INSTANCE.onJoin(info.address);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            DiscordRPCManager.INSTANCE.onLeave());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DiscordRPCManager.INSTANCE.tick();
            while (hudKey.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new HudEditorScreen());
            }
        });

        // ── Réseau ────────────────────────────────────────────────────────────

        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.NT_TOAST, (client, handler, buf, responseSender) -> {
            int color = buf.readInt();
            int count = buf.readInt();
            String[] lines = new String[count];
            for (int i = 0; i < count; i++) lines[i] = buf.readString();
            client.execute(() -> NotificationHud.push(color, lines));
        });

        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.NT_BALANCE, (client, handler, buf, responseSender) -> {
            int balance = buf.readInt();
            client.execute(() -> BalanceHudOverlay.cachedBalance = balance);
        });

        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_OPEN, (client, handler, buf, responseSender) -> {
            int balance = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            client.execute(() -> {
                BalanceHudOverlay.cachedBalance = balance;
                client.setScreen(new HdvScreen(balance, listings));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.NT_VERSION, (client, handler, buf, responseSender) -> {
            String serverVer = buf.readString();
            String clientVer = FabricLoader.getInstance()
                .getModContainer(NouvelleTerreBridge.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
            if (!serverVer.equals(clientVer)) {
                client.execute(() -> {
                    if (client.player == null) return;
                    String url = "https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD/releases/latest";
                    MutableText link = Text.literal("§9§n[Télécharger v" + serverVer + "]")
                        .styled(s -> s
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(url))));
                    client.player.sendMessage(Text.literal("§e[Nouvelle Terre] §cMod obsolète §7— client §f"
                        + clientVer + " §7≠ serveur §f" + serverVer + " §7— ").append(link), false);
                });
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(BankNetworking.BANK_OPEN, (client, handler, buf, responseSender) -> {
            BankScreen screen = readBankPacket(buf);
            client.execute(() -> client.setScreen(screen));
        });

        ClientPlayNetworking.registerGlobalReceiver(BankNetworking.BANK_RESULT, (client, handler, buf, responseSender) -> {
            boolean ok       = buf.readBoolean();
            String  message  = buf.readString();
            int balance      = buf.readInt();
            int ticksReward  = buf.readInt();
            List<BankScreen.TxData>           txs       = readBankTxs(buf);
            int totalShards  = buf.readInt();
            int playerCount  = buf.readInt();
            List<BankScreen.LeaderboardEntry> lb        = readLeaderboard(buf);
            List<BankScreen.LoanData>         asLender  = readLoans(buf);
            List<BankScreen.LoanData>         asBorrow  = readLoans(buf);
            List<String>                      known     = readStringList(buf);
            List<BankScreen.RecurringData>    recurring = readBankRecurring(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof BankScreen screen) {
                    screen.handleResult(ok, message, balance, ticksReward, txs,
                        totalShards, playerCount, lb, asLender, asBorrow, known, recurring);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_RESULT, (client, handler, buf, responseSender) -> {
            boolean ok      = buf.readBoolean();
            String  message = buf.readString();
            int balance     = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            client.execute(() -> {
                BalanceHudOverlay.cachedBalance = balance;
                if (client.currentScreen instanceof HdvScreen screen) {
                    screen.handleResult(ok, message, balance, listings);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(QuestNetworking.QUEST_OPEN, (client, handler, buf, responseSender) -> {
            List<QuetesScreen.QuestData> quests     = readQuests(buf);
            Map<Integer, Integer>        inProgress = readQuestProgress(buf);
            Set<Integer>                 completed  = readQuestCompleted(buf);
            client.execute(() -> client.setScreen(new QuetesScreen(quests, inProgress, completed)));
        });

        ClientPlayNetworking.registerGlobalReceiver(QuestNetworking.QUEST_RESULT, (client, handler, buf, responseSender) -> {
            boolean ok                           = buf.readBoolean();
            String  message                      = buf.readString();
            List<QuetesScreen.QuestData> quests   = readQuests(buf);
            Map<Integer, Integer>        inProgress = readQuestProgress(buf);
            Set<Integer>                 completed  = readQuestCompleted(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof QuetesScreen screen) {
                    screen.update(quests, inProgress, completed);
                }
                int color = ok ? 0xFF2EAD6B : 0xFFBF2040;
                NotificationHud.push(color, message);
            });
        });
    }

    // ── Helpers lecture paquets ───────────────────────────────────────────────

    private static List<HdvScreen.ListingData> readListings(PacketByteBuf buf) {
        int count = buf.readInt();
        List<HdvScreen.ListingData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new HdvScreen.ListingData(buf.readInt(), buf.readString(), buf.readString(), buf.readInt(), buf.readInt()));
        return list;
    }

    private static List<String> readStringList(PacketByteBuf buf) {
        int count = buf.readInt();
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(buf.readString());
        return list;
    }

    private static BankScreen readBankPacket(PacketByteBuf buf) {
        int balance     = buf.readInt();
        int ticksReward = buf.readInt();
        List<BankScreen.TxData>           txs       = readBankTxs(buf);
        int totalShards  = buf.readInt();
        int playerCount  = buf.readInt();
        List<BankScreen.LeaderboardEntry> lb        = readLeaderboard(buf);
        List<BankScreen.LoanData>         asLender  = readLoans(buf);
        List<BankScreen.LoanData>         asBorrow  = readLoans(buf);
        List<String>                      known     = readStringList(buf);
        List<BankScreen.RecurringData>    recurring = readBankRecurring(buf);
        return new BankScreen(balance, ticksReward, txs, totalShards, playerCount,
            lb, asLender, asBorrow, known, recurring);
    }

    private static List<BankScreen.RecurringData> readBankRecurring(PacketByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.RecurringData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.RecurringData(buf.readInt(), buf.readString(), buf.readInt(), buf.readInt(), buf.readInt()));
        return list;
    }

    private static List<BankScreen.TxData> readBankTxs(PacketByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.TxData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.TxData(buf.readInt(), buf.readString(), buf.readInt(), buf.readLong()));
        return list;
    }

    private static List<BankScreen.LeaderboardEntry> readLeaderboard(PacketByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.LeaderboardEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.LeaderboardEntry(buf.readString(), buf.readInt()));
        return list;
    }

    private static List<BankScreen.LoanData> readLoans(PacketByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.LoanData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.LoanData(
                buf.readInt(), buf.readString(), buf.readInt(), buf.readLong(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));
        return list;
    }

    private static List<QuetesScreen.QuestData> readQuests(PacketByteBuf buf) {
        int count = buf.readInt();
        List<QuetesScreen.QuestData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new QuetesScreen.QuestData(
                buf.readInt(), buf.readString(), buf.readString(), buf.readInt(), buf.readInt(), buf.readString()));
        return list;
    }

    private static Map<Integer, Integer> readQuestProgress(PacketByteBuf buf) {
        int count = buf.readInt();
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < count; i++) map.put(buf.readInt(), buf.readInt());
        return map;
    }

    private static Set<Integer> readQuestCompleted(PacketByteBuf buf) {
        int count = buf.readInt();
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < count; i++) set.add(buf.readInt());
        return set;
    }
}
