package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

public final class ConflitNetworking {

    public static final CustomPayload.Id<NtPayload> CONFLIT_OPEN = NtNet.canal("conflit_open");   // S→C : liste joueurs en ligne
    public static final CustomPayload.Id<NtPayload> CONFLIT_ACTION = NtNet.canal("conflit_action"); // C→S : cible + raison
    public static final CustomPayload.Id<NtPayload> CONFLIT_RESULT = NtNet.canal("conflit_result"); // S→C : ok + message (toast)

    private ConflitNetworking() {}
}
