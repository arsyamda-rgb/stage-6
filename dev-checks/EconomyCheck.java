package com.veyrion.character.economy;

import com.veyrion.character.integration.CharacterCache;
import com.veyrion.character.integration.PlaceholderResolver;
import com.veyrion.character.integration.PopulationCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Standalone check of Money, Transfers and the balance placeholders (see README.txt). */
public final class EconomyCheck {

    static int checks = 0;
    static int failures = 0;

    static void eq(String what, Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) {
            failures++;
            System.out.println("FAIL " + what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** Fake provider: balances in a map, with switches to make operations fail or throw. */
    static final class FakeBridge implements EconomyBridge {
        final Map<UUID, Double> money = new HashMap<>();
        UUID failDepositFor;
        boolean refundAlsoFails;
        boolean throwOnDeposit;
        int depositCalls;

        @Override public Status status() { return Status.AVAILABLE; }
        @Override public String providerName() { return "fake"; }
        @Override public double balance(UUID p) { return money.getOrDefault(p, 0.0); }
        @Override public boolean has(UUID p, double a) { return balance(p) >= a; }
        @Override public Result withdraw(UUID p, double a) {
            if (!has(p, a)) return Result.fail("insufficient");
            money.put(p, balance(p) - a);
            return Result.ok();
        }
        @Override public Result deposit(UUID p, double a) {
            depositCalls++;
            if (throwOnDeposit) throw new IllegalStateException("provider exploded");
            if (p.equals(failDepositFor)) return Result.fail("account locked");
            if (refundAlsoFails && depositCalls > 1) return Result.fail("still down");
            money.put(p, balance(p) + a);
            return Result.ok();
        }
    }

    static double total(FakeBridge b) {
        return b.money.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public static void main(String[] args) {
        // ---------------- Money.parse
        for (String ok : new String[] {"1", "250", "12.5", "0.01", "0", "1000000000000"}) {
            eq("parse accepts " + ok, true, Money.parse(ok).isPresent());
        }
        for (String bad : new String[] {"", "-5", "+5", "1e3", "1,000", "NaN", "Infinity", "abc", "1.234", ".5", "5.",
                " 5", "5 ", "1000000000001", "0x10", "١٢٣", "99999999999999"}) {
            eq("parse rejects '" + bad + "'", false, Money.parse(bad).isPresent());
        }
        eq("parse null", false, Money.parse(null).isPresent());
        eq("parse value", 12.5, Money.parse("12.5").getAsDouble());

        // ---------------- Money.format / plain / round
        eq("format 2500", "2,500", Money.format(2500));
        eq("format 2500.5", "2,500.5", Money.format(2500.5));
        eq("format 1234567.891", "1,234,567.89", Money.format(1234567.891));
        eq("format 0", "0", Money.format(0));
        eq("format NaN", "0", Money.format(Double.NaN));
        eq("plain 2500", "2500", Money.plain(2500));
        eq("plain 2500.5", "2500.5", Money.plain(2500.5));
        eq("plain 0", "0", Money.plain(0));
        eq("round", 0.31, Money.round(0.305));
        eq("round inf", 0.0, Money.round(Double.POSITIVE_INFINITY));

        // ---------------- Transfers
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<String> severe = new ArrayList<>();

        FakeBridge bridge = new FakeBridge();
        bridge.money.put(a, 1000.0);
        eq("transfer ok", Transfers.Outcome.OK, Transfers.transfer(bridge, a, b, 250, severe::add));
        eq("sender after", 750.0, bridge.balance(a));
        eq("receiver after", 250.0, bridge.balance(b));
        eq("money conserved", 1000.0, total(bridge));

        eq("insufficient", Transfers.Outcome.INSUFFICIENT, Transfers.transfer(bridge, a, b, 750.01, severe::add));
        eq("insufficient changes nothing", 750.0, bridge.balance(a));
        eq("exact balance ok", Transfers.Outcome.OK, Transfers.transfer(bridge, a, b, 750, severe::add));
        eq("sender emptied", 0.0, bridge.balance(a));

        eq("self transfer", Transfers.Outcome.ERROR, Transfers.transfer(bridge, b, b, 1, severe::add));
        eq("zero", Transfers.Outcome.ERROR, Transfers.transfer(bridge, b, a, 0, severe::add));
        eq("negative", Transfers.Outcome.ERROR, Transfers.transfer(bridge, b, a, -5, severe::add));
        eq("NaN", Transfers.Outcome.ERROR, Transfers.transfer(bridge, b, a, Double.NaN, severe::add));
        eq("Infinity", Transfers.Outcome.ERROR, Transfers.transfer(bridge, b, a, Double.POSITIVE_INFINITY, severe::add));
        eq("invalid inputs change nothing", 1000.0, bridge.balance(b));
        eq("no severe logs so far", 0, severe.size());

        // deposit refused -> sender refunded
        FakeBridge refusing = new FakeBridge();
        refusing.money.put(a, 500.0);
        refusing.failDepositFor = b;
        eq("deposit fails, refunded", Transfers.Outcome.DEPOSIT_FAILED_REFUNDED,
                Transfers.transfer(refusing, a, b, 200, severe::add));
        eq("sender made whole", 500.0, refusing.balance(a));
        eq("receiver untouched", 0.0, refusing.balance(b));

        // deposit throws -> refunded? (refund deposit also throws here, so repair needed)
        FakeBridge exploding = new FakeBridge();
        exploding.money.put(a, 500.0);
        exploding.throwOnDeposit = true;
        eq("provider throws on deposit", Transfers.Outcome.DEPOSIT_FAILED_REFUND_FAILED,
                Transfers.transfer(exploding, a, b, 200, severe::add));
        eq("severe log written", true, severe.stream().anyMatch(s -> s.contains("MANUAL REPAIR")));

        // refund also fails
        FakeBridge broken = new FakeBridge();
        broken.money.put(a, 500.0);
        broken.failDepositFor = b;
        broken.refundAlsoFails = true;
        severe.clear();
        // first deposit (to b) fails by failDepositFor; refund is deposit #2 -> fails by refundAlsoFails
        eq("refund fails", Transfers.Outcome.DEPOSIT_FAILED_REFUND_FAILED,
                Transfers.transfer(broken, a, b, 200, severe::add));
        eq("repair message", true, severe.stream().anyMatch(s -> s.contains("MANUAL REPAIR")));

        // ---------------- Placeholders
        CharacterCache characters = new CharacterCache(id -> CompletableFuture.completedFuture(java.util.Optional.empty()),
                System::currentTimeMillis, e -> { });
        PopulationCache population = new PopulationCache(() -> new CompletableFuture<>(), e -> { });
        Map<UUID, Double> balances = new HashMap<>();
        balances.put(a, 2500.0);
        balances.put(b, 12.5);
        PlaceholderResolver resolver = new PlaceholderResolver(characters, population, id -> id,
                balances::get, v -> Money.format(v) + " VYR");
        eq("balance", "2500", resolver.resolve(a, "A", "balance"));
        eq("balance decimals", "12.5", resolver.resolve(b, "B", "balance"));
        eq("balance_formatted", "2,500 VYR", resolver.resolve(a, "A", "balance_formatted"));
        eq("balance upper-case key", "2500", resolver.resolve(a, "A", "BALANCE"));
        eq("balance unknown player", "N/A", resolver.resolve(UUID.randomUUID(), "X", "balance"));
        eq("balance null player", "N/A", resolver.resolve(null, null, "balance_formatted"));
        eq("unknown placeholder untouched", null, resolver.resolve(a, "A", "balance_x"));
        PlaceholderResolver noEconomy = new PlaceholderResolver(characters, population, id -> id);
        eq("economy unavailable", "N/A", noEconomy.resolve(a, "A", "balance"));
        eq("economy unavailable formatted", "N/A", noEconomy.resolve(a, "A", "balance_formatted"));
        eq("old placeholders still work", "0", noEconomy.resolve(a, "A", "exp"));

        System.out.println(checks + " checks, " + failures + " failures");
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : "FAILED");
        if (failures != 0) System.exit(1);
    }
}
