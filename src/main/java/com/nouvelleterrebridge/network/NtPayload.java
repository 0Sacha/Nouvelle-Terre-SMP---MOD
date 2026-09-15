package com.nouvelleterrebridge.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Paquet générique porteur d'octets bruts.
 *
 * <p>Depuis la 1.20.5, Fabric API impose des paquets typés ({@link CustomPayload} +
 * {@code PacketCodec} enregistrés via {@code PayloadTypeRegistry}) là où la 1.20.1
 * acceptait un simple tampon d'octets passé au récepteur. Les 34 canaux du mod ne
 * sérialisent que des primitives — entiers, chaînes, booléens — et les convertir en
 * records typés aurait voulu dire réécrire à la main {@code BANK_OPEN} et ses douze
 * sous-listes, pour un résultat identique sur le fil.
 *
 * <p>Ce paquet unique transporte donc le tampon tel quel, et {@link NtNet} le réexpose
 * des deux côtés sous forme de {@link PacketByteBuf}. Les quelque 530 appels de
 * lecture/écriture répartis dans le mod restent valables mot pour mot.
 *
 * <p>Chaque canal a son propre {@link CustomPayload.Id} — c'est lui qui porte
 * l'identifiant sur le réseau — mais tous partagent cette classe et ce codec.
 */
public record NtPayload(CustomPayload.Id<NtPayload> id, byte[] data) implements CustomPayload {

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return id;
    }

    /**
     * Codec d'un canal donné : écrit les octets tels quels, et relit tout le reste du
     * tampon. Le type n'est pas encodé dans la charge utile — Fabric l'a déjà lu pour
     * choisir ce codec, il est donc réinjecté à la décompression.
     */
    public static PacketCodec<PacketByteBuf, NtPayload> codec(CustomPayload.Id<NtPayload> id) {
        return PacketCodec.of(
            (paquet, tampon) -> tampon.writeBytes(paquet.data()),
            tampon -> {
                byte[] octets = new byte[tampon.readableBytes()];
                tampon.readBytes(octets);
                return new NtPayload(id, octets);
            }
        );
    }
}
