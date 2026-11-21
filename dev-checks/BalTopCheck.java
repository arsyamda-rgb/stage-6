package com.veyrion.character.economy;

import com.veyrion.character.character.CharacterRepository;
import com.veyrion.character.character.CharacterRepository.Identities;
import com.veyrion.character.character.CharacterRepository.Identity;
import com.veyrion.character.config.PluginSettings;
import com.veyrion.character.core.VeyrionCharacterPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Standalone check of /topuang (BalTop), identity validation and the pay cooldown (see README.txt). */
public final class BalTopCheck {

    static int checks = 0, failures = 0;

    static void eq(String what, Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) {
            failures++;
            System.out.println("FAIL " + what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static final class Bridge implements EconomyBridge {
        final Map<UUID, Double> money = new HashMap<>();
        Status status = Status.AVAILABLE;
        int reads;
        UUID throwFor;
        @Override public Status status() { return status; }
        @Override public String providerName() { return "fake"; }
        @Override public double balance(UUID p) {
            reads++;
            if (p.equals(throwFor)) throw new IllegalStateException("corrupt account");
            return money.getOrDefault(p, 0.0);
        }
        @Override public boolean has(UUID p, double a) { return balance(p) >= a; }
        @Override public Result deposit(UUID p, double a) { money.merge(p, a, Double::sum); return Result.ok(); }
        @Override public Result withdraw(UUID p, double a) { money.merge(p, -a, Double::sum); return Result.ok(); }
    }

    static PluginSettings.Economy settings(int cooldownMs) {
        return new PluginSettings.Economy(true, "Veyra", "VYR", true, true, true, 1, 120, cooldownMs);
    }

    static UUID id(int n) { return new UUID(0xABCDL, n); }

    static BalTop.Result run(VeyrionCharacterPlugin plugin, BalTop top) {
        BalTop.Result[] out = new BalTop.Result[1];
        top.request(r -> out[0] = r);
        for (int i = 0; i < 100_000 && out[0] == null && !plugin.tasks.isEmpty(); i++) plugin.tick();
        return out[0];
    }

    public static void main(String[] args) {
        // ---- identity validation (pure)
        String good = id(1).toString();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {good, "Alice"});
        rows.add(new String[] {good.toUpperCase(), "AliceDup"});                  // same uuid, other case
        rows.add(new String[] {"not-a-uuid", "Broken"});
        rows.add(new String[] {"", "Empty"});
        rows.add(new String[] {null, "Null"});
        rows.add(new String[] {new UUID(0, 0).toString(), "Nil"});
        rows.add(new String[] {"  " + id(2) + "  ", " "});                         // padded, blank name
        rows.add(new String[] {id(3).toString(), null});                           // null name
        Identities ids = CharacterRepository.identitiesFrom(rows);
        eq("valid count", 3, ids.valid().size());
        eq("invalid count", 5, ids.invalid());
        eq("first row wins", "Alice", ids.valid().get(0).name());
        eq("blank name falls back to uuid prefix", id(2).toString().substring(0, 8), ids.valid().get(1).name());
        eq("null name falls back", id(3).toString().substring(0, 8), ids.valid().get(2).name());
        eq("padded uuid trimmed", id(2), ids.valid().get(1).uuid());

        // ---- BalTop
        VeyrionCharacterPlugin plugin = new VeyrionCharacterPlugin();
        Bridge bridge = new Bridge();
        EconomyService economy = new EconomyService(plugin.getLogger(), () -> settings(3000), bridge);
        List<Identity> players = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            players.add(new Identity(id(i), "P" + i));
            bridge.money.put(id(i), (double) (i * 100));   // P30 richest
        }
        players.add(new Identity(id(100), "Zero"));      // balance 0 -> excluded
        bridge.money.put(id(101), -50.0);
        players.add(new Identity(id(101), "Debt"));      // negative -> excluded
        players.add(new Identity(id(102), "Ghost"));     // provider has no account -> 0
        players.add(new Identity(id(103), "Corrupt"));   // provider throws -> skipped, not fatal
        bridge.throwFor = id(103);
        int[] sourceCalls = {0};
        BalTop top = new BalTop(plugin, economy, () -> {
            sourceCalls[0]++;
            return CompletableFuture.completedFuture(new Identities(players, 2));
        });

        BalTop.Result first = run(plugin, top);
        eq("ok", true, first.ok());
        eq("top size 10", 10, first.entries().size());
        eq("richest first", "P30", first.entries().get(0).name());
        eq("richest amount", 3000.0, first.entries().get(0).balance());
        eq("tenth", "P21", first.entries().get(9).name());
        boolean sorted = true;
        for (int i = 1; i < 10; i++) sorted &= first.entries().get(i - 1).balance() >= first.entries().get(i).balance();
        eq("sorted descending", true, sorted);
        eq("zero/negative/ghost/corrupt excluded", true, first.entries().stream()
                .noneMatch(e -> List.of("Zero", "Debt", "Ghost", "Corrupt").contains(e.name())));

        int reads = bridge.reads;
        BalTop.Result second = run(plugin, top);
        eq("cached: no second scan", reads, bridge.reads);
        eq("cached: source not asked again", 1, sourceCalls[0]);
        eq("cached result identical", first.entries(), second.entries());
        eq("isFresh", true, top.isFresh());

        // ties are ordered by name, not by database order
        Bridge tieBridge = new Bridge();
        EconomyService tieEconomy = new EconomyService(plugin.getLogger(), () -> settings(3000), tieBridge);
        List<Identity> tied = List.of(new Identity(id(1), "bob"), new Identity(id(2), "Alice"), new Identity(id(3), "carl"));
        tied.forEach(i -> tieBridge.money.put(i.uuid(), 500.0));
        BalTop tieTop = new BalTop(plugin, tieEconomy, () -> CompletableFuture.completedFuture(new Identities(tied, 0)));
        eq("tie order", List.of("Alice", "bob", "carl"), run(plugin, tieTop).entries().stream().map(BalTop.Entry::name).toList());

        // many players are spread over several ticks (never one long tick)
        Bridge bigBridge = new Bridge();
        EconomyService bigEconomy = new EconomyService(plugin.getLogger(), () -> settings(3000), bigBridge);
        List<Identity> big = new ArrayList<>();
        for (int i = 1; i <= 200_000; i++) { big.add(new Identity(id(i), "N" + i)); bigBridge.money.put(id(i), (double) i); }
        BalTop bigTop = new BalTop(plugin, bigEconomy, () -> CompletableFuture.completedFuture(new Identities(big, 0)));
        int before = plugin.ticks;
        BalTop.Result bigResult = run(plugin, bigTop);
        eq("big scan finished", true, bigResult.ok());
        eq("big scan top", "N200000", bigResult.entries().get(0).name());
        eq("big scan used several ticks", true, plugin.ticks - before > 1);

        // economy goes away mid-scan -> failure result, not an exception, not a stale cache
        Bridge dying = new Bridge();
        EconomyService dyingEconomy = new EconomyService(plugin.getLogger(), () -> settings(3000), dying);
        List<Identity> many = new ArrayList<>();
        for (int i = 1; i <= 200_000; i++) { many.add(new Identity(id(i), "M" + i)); dying.money.put(id(i), 1.0); }
        BalTop dyingTop = new BalTop(plugin, dyingEconomy, () -> CompletableFuture.completedFuture(new Identities(many, 0)));
        BalTop.Result[] out = new BalTop.Result[1];
        dyingTop.request(r -> out[0] = r);
        plugin.tick();
        dying.status = EconomyBridge.Status.NO_PROVIDER;
        for (int i = 0; i < 100_000 && out[0] == null && !plugin.tasks.isEmpty(); i++) plugin.tick();
        eq("provider vanished: not ok", false, out[0] != null && out[0].ok());
        eq("provider vanished: answered", true, out[0] != null);
        eq("no cache after failure", false, dyingTop.isFresh());

        // source failure
        BalTop failing = new BalTop(plugin, economy, () -> CompletableFuture.failedFuture(new RuntimeException("db")));
        eq("source failure -> not ok", false, run(plugin, failing).ok());

        // two requests during one scan share it
        int[] calls = {0};
        BalTop shared = new BalTop(plugin, economy, () -> { calls[0]++; return CompletableFuture.completedFuture(new Identities(players, 0)); });
        List<BalTop.Result> got = new ArrayList<>();
        shared.request(got::add);
        shared.request(got::add);
        while (!plugin.tasks.isEmpty() && got.size() < 2) plugin.tick();
        eq("both waiters answered", 2, got.size());
        eq("one scan for both", 1, calls[0]);

        // ---- pay cooldown
        EconomyService svc = new EconomyService(plugin.getLogger(), () -> settings(3000), new Bridge());
        UUID p = id(1);
        eq("first pay allowed", true, svc.tryAcquirePay(p));
        eq("immediate second pay blocked", false, svc.tryAcquirePay(p));
        eq("other player unaffected", true, svc.tryAcquirePay(id(2)));
        svc.forget(p);
        eq("forget clears cooldown", true, svc.tryAcquirePay(p));

        System.out.println(checks + " checks, " + failures + " failures");
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : "FAILED");
        if (failures != 0) System.exit(1);
    }
}
