package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

public final class BankNetworking {

    public static final Identifier BANK_OPEN   = new Identifier("nouvelle-terre-bridge", "bank_open");
    public static final Identifier BANK_ACTION = new Identifier("nouvelle-terre-bridge", "bank_action");
    public static final Identifier BANK_RESULT = new Identifier("nouvelle-terre-bridge", "bank_result");

    public static final Identifier BANK_REQUEST = new Identifier("nouvelle-terre-bridge", "bank_request");

    public static final int ACTION_LOAN_CREATE       = 0;
    public static final int ACTION_LOAN_REPAY        = 1;
    public static final int ACTION_LOAN_FORGIVE      = 2;
    public static final int ACTION_TRANSFER          = 3;
    public static final int ACTION_RECURRING_CREATE  = 4;
    public static final int ACTION_RECURRING_CANCEL  = 5;

    private BankNetworking() {}
}
