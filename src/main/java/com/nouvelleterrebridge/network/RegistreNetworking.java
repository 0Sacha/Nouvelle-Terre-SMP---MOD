package com.nouvelleterrebridge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class RegistreNetworking {
    public static final CustomPacketPayload.Type<NtPayload> REGISTRE_OPEN = NtNet.canal("registre_open");
    public static final CustomPacketPayload.Type<NtPayload> REGISTRE_DETAIL_REQUEST = NtNet.canal("registre_detail_req");
    public static final CustomPacketPayload.Type<NtPayload> REGISTRE_DETAIL = NtNet.canal("registre_detail");
}
