package com.nouvelleterrebridge.network;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routeur réseau du mod.
 *
 * <p>Les 34 canaux du mod ne sérialisent que des primitives — entiers, chaînes,
 * booléens — et les convertir en records typés aurait voulu dire réécrire à la
 * main {@code BANK_OPEN} et ses douze sous-listes, pour un résultat identique sur
 * le fil. {@link NtPayload} transporte donc le tampon brut, et ce routeur réexpose
 * exactement la même forme d'appel que côté Fabric : un canal se déclare avec
 * {@link #canal}, s'écoute avec {@link #surServeur}/{@link #surClient}, s'émet
 * avec {@link #versServeur}/{@link #versClient}. Les quelque 530 appels
 * {@code readInt}/{@code writeString}/… du mod n'ont pas à changer.
 *
 * <p>⚠ NeoForge exige d'enregistrer type + codec + handler en un seul appel
 * ({@link PayloadRegistrar#playBidirectional}), au moment de
 * {@link RegisterPayloadHandlersEvent} — alors que {@link #canal} (appelé à
 * l'initialisation statique des classes {@code *Networking.java}) et
 * {@link #surServeur}/{@link #surClient} (appelés depuis l'entrypoint, à un autre
 * moment) sont découplés dans la conception du mod. On comble l'écart avec une
 * case mutable par canal, remplie par {@code surServeur}/{@code surClient} et lue
 * à chaque paquet reçu — l'ordre entre la déclaration, le branchement du handler
 * et {@link #enregistrer} n'a alors plus d'importance, tant que le handler est
 * posé avant qu'un vrai paquet n'arrive (toujours vrai en pratique : aucun joueur
 * n'est encore connecté à ce stade du chargement du mod).
 */
public final class NtNet {

    private NtNet() {}

    @FunctionalInterface
    public interface RecepteurServeur {
        void accepter(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf);
    }

    @FunctionalInterface
    public interface RecepteurClient {
        void accepter(Minecraft client, FriendlyByteBuf buf);
    }

    private static final class Case {
        volatile RecepteurServeur serveur;
        volatile RecepteurClient client;
    }

    private static final Map<CustomPacketPayload.Type<NtPayload>, Case> CANAUX = new LinkedHashMap<>();

    /** Déclare un canal du mod. Le namespace est celui des ressources, pas le modId. */
    public static CustomPacketPayload.Type<NtPayload> canal(String chemin) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
            com.nouvelleterrebridge.NouvelleTerreBridge.MOD_ID, chemin);
        CustomPacketPayload.Type<NtPayload> type = new CustomPacketPayload.Type<>(id);
        CANAUX.put(type, new Case());
        return type;
    }

    public static void surServeur(CustomPacketPayload.Type<NtPayload> canal, RecepteurServeur recepteur) {
        CANAUX.get(canal).serveur = recepteur;
    }

    public static void surClient(CustomPacketPayload.Type<NtPayload> canal, RecepteurClient recepteur) {
        CANAUX.get(canal).client = recepteur;
    }

    public static void versClient(ServerPlayer joueur, CustomPacketPayload.Type<NtPayload> canal, FriendlyByteBuf tampon) {
        PacketDistributor.sendToPlayer(joueur, new NtPayload(canal, octets(tampon)));
    }

    public static void versServeur(CustomPacketPayload.Type<NtPayload> canal, FriendlyByteBuf tampon) {
        PacketDistributor.sendToServer(new NtPayload(canal, octets(tampon)));
    }

    /** Remplace {@code PacketByteBufs.create()} (Fabric) — un seul endroit à changer. */
    public static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    /**
     * Force le chargement des dix classes {@code *Networking} — et donc l'appel à
     * {@link #canal} pour chacun des 34 canaux — avant que quoi que ce soit d'autre
     * ne s'exécute. Sans ça, un canal seulement référencé depuis l'intérieur d'un
     * lambda (donc jamais évalué avant l'exécution effective de ce lambda, par ex.
     * {@code WikiNetworking.WIKI_OPEN} dans le corps de {@code registerHubNetworking})
     * ne serait chargé qu'après le passage de {@link RegisterPayloadHandlersEvent} —
     * trop tard pour être enregistré auprès de NeoForge.
     */
    public static void precharger() {
        var classes = new Class<?>[] {
            com.nouvelleterrebridge.network.HdvNetworking.class,
            com.nouvelleterrebridge.network.BankNetworking.class,
            com.nouvelleterrebridge.network.QuestNetworking.class,
            com.nouvelleterrebridge.network.RegistreNetworking.class,
            com.nouvelleterrebridge.network.ProductionNetworking.class,
            com.nouvelleterrebridge.network.ConflitNetworking.class,
            com.nouvelleterrebridge.network.HubNetworking.class,
            com.nouvelleterrebridge.network.ShopNetworking.class,
            com.nouvelleterrebridge.network.ServiceNetworking.class,
            com.nouvelleterrebridge.network.WikiNetworking.class,
        };
        for (Class<?> c : classes) {
            try {
                Class.forName(c.getName(), true, c.getClassLoader());
            } catch (ClassNotFoundException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    /** À appeler depuis le listener {@link RegisterPayloadHandlersEvent}, une fois tous les canaux déclarés. */
    public static void enregistrer(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (Map.Entry<CustomPacketPayload.Type<NtPayload>, Case> entry : CANAUX.entrySet()) {
            CustomPacketPayload.Type<NtPayload> type = entry.getKey();
            Case etat = entry.getValue();
            registrar.playBidirectional(
                type,
                NtPayload.codec(type),
                new DirectionalPayloadHandler<>(
                    (payload, context) -> {
                        RecepteurClient r = etat.client;
                        if (r != null) r.accepter(Minecraft.getInstance(), tamponDe(payload));
                    },
                    (payload, context) -> {
                        RecepteurServeur r = etat.serveur;
                        if (r != null) {
                            ServerPlayer joueur = (ServerPlayer) context.player();
                            r.accepter(joueur.getServer(), joueur, tamponDe(payload));
                        }
                    }
                )
            );
        }
    }

    private static FriendlyByteBuf tamponDe(NtPayload payload) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
    }

    private static byte[] octets(FriendlyByteBuf tampon) {
        byte[] donnees = new byte[tampon.readableBytes()];
        tampon.getBytes(tampon.readerIndex(), donnees);
        return donnees;
    }
}
