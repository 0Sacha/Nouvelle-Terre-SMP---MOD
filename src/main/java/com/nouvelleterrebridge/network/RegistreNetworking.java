package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

public class RegistreNetworking {
    public static final CustomPayload.Id<NtPayload> REGISTRE_OPEN = NtNet.canal("registre_open");
    public static final CustomPayload.Id<NtPayload> REGISTRE_DETAIL_REQUEST = NtNet.canal("registre_detail_req");
    public static final CustomPayload.Id<NtPayload> REGISTRE_DETAIL = NtNet.canal("registre_detail");
}
