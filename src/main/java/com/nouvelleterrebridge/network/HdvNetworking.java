package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

public final class HdvNetworking {

    public static final CustomPayload.Id<NtPayload> HDV_OPEN   = NtNet.canal("hdv_open");
    public static final CustomPayload.Id<NtPayload> HDV_ACTION = NtNet.canal("hdv_action");
    public static final CustomPayload.Id<NtPayload> HDV_RESULT = NtNet.canal("hdv_result");
    public static final CustomPayload.Id<NtPayload> NT_VERSION = NtNet.canal("nt_version");
    public static final CustomPayload.Id<NtPayload> NT_BALANCE = NtNet.canal("nt_balance");
    public static final CustomPayload.Id<NtPayload> NT_TOAST   = NtNet.canal("nt_toast");
    public static final CustomPayload.Id<NtPayload> NT_NOM_RP  = NtNet.canal("nt_nom_rp");

    // Virements et récurrents vivent sur BANK_ACTION depuis la création de BankScreen —
    // les anciennes valeurs 3/4/5 de ce canal ont été retirées avec leurs handlers.
    public static final int ACTION_BUY      = 0;
    public static final int ACTION_SELL     = 1;
    public static final int ACTION_WITHDRAW = 2;

    private HdvNetworking() {}
}
