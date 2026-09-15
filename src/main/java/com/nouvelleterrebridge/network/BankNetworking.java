package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

public final class BankNetworking {

    public static final CustomPayload.Id<NtPayload> BANK_OPEN = NtNet.canal("bank_open");
    public static final CustomPayload.Id<NtPayload> BANK_ACTION = NtNet.canal("bank_action");
    public static final CustomPayload.Id<NtPayload> BANK_RESULT = NtNet.canal("bank_result");

    public static final CustomPayload.Id<NtPayload> BANK_REQUEST = NtNet.canal("bank_request");

    public static final int ACTION_LOAN_REQUEST      = 0;  // prêteur → propose un crédit à l'emprunteur
    public static final int ACTION_LOAN_REPAY        = 1;
    public static final int ACTION_LOAN_FORGIVE      = 2;
    public static final int ACTION_TRANSFER          = 3;
    public static final int ACTION_RECURRING_CREATE  = 4;
    public static final int ACTION_RECURRING_CANCEL  = 5;
    public static final int ACTION_LOAN_ACCEPT       = 6;  // emprunteur accepte → crédit créé + fonds transférés
    public static final int ACTION_LOAN_DECLINE      = 7;  // emprunteur refuse OU prêteur annule sa proposition
    public static final int ACTION_WITHDRAW_SHARDS   = 8;  // retire N ◆ du compte en items Shard physiques
    public static final int ACTION_DEPOSIT_SHARDS    = 9;  // dépose N ◆ de Shards physiques (0 = tout)

    private BankNetworking() {}
}
