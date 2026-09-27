package com.nouvelleterrebridge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class WikiNetworking {
    public static final CustomPacketPayload.Type<NtPayload> WIKI_OPEN = NtNet.canal("wiki_open");
}
