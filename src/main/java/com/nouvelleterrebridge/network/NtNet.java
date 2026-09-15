package com.nouvelleterrebridge.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Routeur réseau du mod.
 *
 * <p>Depuis la 1.20.5, Fabric API impose des paquets typés ({@link CustomPayload})
 * là où la 1.20.1 acceptait de passer directement un {@link PacketByteBuf} à
 * {@code registerGlobalReceiver}. Réécrire les 34 canaux du mod en records typés
 * — un par forme de paquet, {@code BANK_OPEN} et ses douze sous-listes compris —
 * n'aurait rien changé sur le fil : ils ne sérialisent que des primitives.
 *
 * <p>{@link NtPayload} transporte donc le tampon brut, et ce routeur réexpose la
 * même forme d'appel qu'avant migration : un canal se déclare avec {@link #canal},
 * s'écoute avec {@link #surServeur}/{@link #surClient}, s'émet avec
 * {@link #versServeur}/{@link #versClient}. Les quelque 530 appels
 * {@code readInt}/{@code writeString}/… du mod restent inchangés — seule la ligne
 * d'enregistrement autour de chaque corps de handler change.
 */
public final class NtNet {

    private NtNet() {}

    @FunctionalInterface
    public interface RecepteurServeur {
        void accepter(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf);
    }

    @FunctionalInterface
    public interface RecepteurClient {
        void accepter(MinecraftClient client, PacketByteBuf buf);
    }

    /** Déclare un canal du mod. Le namespace est celui des ressources, pas le modId. */
    public static CustomPayload.Id<NtPayload> canal(String chemin) {
        Identifier id = Identifier.of(com.nouvelleterrebridge.NouvelleTerreBridge.MOD_ID, chemin);
        CustomPayload.Id<NtPayload> type = new CustomPayload.Id<>(id);
        // Un canal peut circuler dans les deux sens selon le contexte (ex. NT_BALANCE
        // n'est envoyé que S→C, HDV_ACTION que C→S) : les déclarer tous les deux évite
        // d'entretenir une table des directions, qui se désynchroniserait du code un
        // jour ou l'autre.
        PayloadTypeRegistry.playS2C().register(type, NtPayload.codec(type));
        PayloadTypeRegistry.playC2S().register(type, NtPayload.codec(type));
        return type;
    }

    public static void surServeur(CustomPayload.Id<NtPayload> canal, RecepteurServeur recepteur) {
        ServerPlayNetworking.registerGlobalReceiver(canal, (payload, context) ->
            recepteur.accepter(context.server(), context.player(), tamponDe(payload)));
    }

    public static void surClient(CustomPayload.Id<NtPayload> canal, RecepteurClient recepteur) {
        ClientPlayNetworking.registerGlobalReceiver(canal, (payload, context) ->
            recepteur.accepter(context.client(), tamponDe(payload)));
    }

    public static void versClient(ServerPlayerEntity joueur, CustomPayload.Id<NtPayload> canal, PacketByteBuf tampon) {
        ServerPlayNetworking.send(joueur, new NtPayload(canal, octets(tampon)));
    }

    public static void versServeur(CustomPayload.Id<NtPayload> canal, PacketByteBuf tampon) {
        ClientPlayNetworking.send(new NtPayload(canal, octets(tampon)));
    }

    private static PacketByteBuf tamponDe(NtPayload payload) {
        return new PacketByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload.data()));
    }

    private static byte[] octets(PacketByteBuf tampon) {
        byte[] donnees = new byte[tampon.readableBytes()];
        tampon.getBytes(tampon.readerIndex(), donnees);
        return donnees;
    }
}
