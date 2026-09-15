package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

public class WikiNetworking {
    public static final CustomPayload.Id<NtPayload> WIKI_OPEN = NtNet.canal("wiki_open");
}
