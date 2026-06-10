package com.nouvelleterrebridge.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TransactionLog {

    public static final int TYPE_BUY            = 0;
    public static final int TYPE_SELL           = 1;
    public static final int TYPE_TRANSFER_IN    = 2;
    public static final int TYPE_TRANSFER_OUT   = 3;
    public static final int TYPE_REWARD         = 4;
    public static final int TYPE_LOAN_OUT       = 5;  // prêteur : fonds versés
    public static final int TYPE_LOAN_IN        = 6;  // emprunteur : fonds reçus
    public static final int TYPE_LOAN_REPAY_OUT = 7;  // emprunteur : remboursement envoyé
    public static final int TYPE_LOAN_REPAY_IN  = 8;  // prêteur : remboursement reçu
    public static final int TYPE_LOAN_PENALTY   = 9;  // emprunteur : pénalité déduite

    public record Entry(int type, String label, int amount, long timestamp) {}

    private static final int MAX = 50;
    private static final Map<String, Deque<Entry>> logs = new ConcurrentHashMap<>();

    private TransactionLog() {}

    public static void log(String player, int type, String label, int amount) {
        Deque<Entry> q = logs.computeIfAbsent(player.toLowerCase(), k -> new ArrayDeque<>());
        synchronized (q) {
            q.addFirst(new Entry(type, label, amount, System.currentTimeMillis()));
            while (q.size() > MAX) q.removeLast();
        }
    }

    public static List<Entry> getLast(String player, int n) {
        Deque<Entry> q = logs.get(player.toLowerCase());
        if (q == null) return List.of();
        synchronized (q) {
            List<Entry> all = new ArrayList<>(q);
            return all.subList(0, Math.min(n, all.size()));
        }
    }
}
