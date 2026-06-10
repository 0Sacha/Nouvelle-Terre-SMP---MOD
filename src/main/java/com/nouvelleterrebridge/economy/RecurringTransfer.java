package com.nouvelleterrebridge.economy;

public class RecurringTransfer {
    public int    id;
    public String from;
    public String to;
    public int    amount;
    public int    intervalTicks;
    public int    ticksSince;

    public RecurringTransfer() {}

    public RecurringTransfer(int id, String from, String to, int amount, int intervalTicks) {
        this.id            = id;
        this.from          = from;
        this.to            = to;
        this.amount        = amount;
        this.intervalTicks = intervalTicks;
        this.ticksSince    = 0;
    }
}
