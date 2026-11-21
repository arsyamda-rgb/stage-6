package com.veyrion.character.integration;

import com.veyrion.character.character.CharacterData;
import com.veyrion.character.race.PopulationReport;
import com.veyrion.character.race.PopulationSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Standalone check of CharacterCache, PopulationCache and PlaceholderResolver (see README.txt). */
public final class PlaceholderCheck {

    static int failures = 0;
    static int checks = 0;

    static void eq(String what, Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) {
            failures++;
            System.out.println("FAIL " + what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** Fake "database": rows + a loader that completes immediately (like the DB thread would, in order). */
    static final Map<UUID, CharacterData> DB = new HashMap<>();
    static final AtomicInteger LOADS = new AtomicInteger();
    static boolean failLoads = false;

    static CompletableFuture<Optional<CharacterData>> load(UUID id) {
        LOADS.incrementAndGet();
        if (failLoads) {
            return CompletableFuture.failedFuture(new RuntimeException("db down"));
        }
        return CompletableFuture.completedFuture(Optional.ofNullable(DB.get(id)));
    }

    static CharacterData ch(UUID id, String name, String race, long exp, String rank) {
        return new CharacterData(id, name, race, exp, rank, Instant.EPOCH, race != null);
    }

    static PopulationReport report(Map<String, Long> byRace, long unassigned, List<PopulationReport.Configured> cfg) {
        return PopulationReport.build(new PopulationSnapshot(byRace, unassigned), cfg);
    }

    public static void main(String[] args) {
        AtomicLong now = new AtomicLong(1_000);
        List<Throwable> errors = new ArrayList<>();
        CharacterCache cache = new CharacterCache(PlaceholderCheck::load, now::get, errors::add);

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID offl = UUID.randomUUID();
        DB.put(alice, ch(alice, "Alice", null, 0, "Novice"));
        DB.put(offl, ch(offl, "Olly", "elf", 1500, "Apprentice"));

        var configured = List.of(new PopulationReport.Configured("human", true), new PopulationReport.Configured("elf", true),
                new PopulationReport.Configured("beast", true), new PopulationReport.Configured("dragon", true));
        AtomicReference_<PopulationReport> src = new AtomicReference_<>(report(Map.of(), 0, configured));
        AtomicInteger popCalls = new AtomicInteger();
        PopulationCache pop = new PopulationCache(() -> {
            popCalls.incrementAndGet();
            return CompletableFuture.completedFuture(src.v);
        }, errors::add);

        PlaceholderResolver r = new PlaceholderResolver(cache, pop, id -> switch (id) {
            case "dragon" -> "Dragon";
            case "human" -> "Human";
            case "elf" -> "Elf";
            default -> id;
        });

        // --- before anything is cached / no population yet
        eq("no uuid name", "", r.resolve(null, null, "name"));
        eq("no uuid race", "None", r.resolve(null, null, "race"));
        eq("no uuid exp", "0", r.resolve(null, null, "exp"));
        eq("no uuid rank", "None", r.resolve(null, null, "rank"));
        eq("no uuid has_race", "false", r.resolve(null, null, "has_race"));
        eq("pop before first report", "0", r.resolve(null, null, "population_total"));
        eq("pop race before first report", "0", r.resolve(null, null, "population_dragon"));

        // --- online player without race
        cache.track(alice);
        eq("online not loaded yet -> fallback race", "None", r.resolve(alice, "Alice", "race"));
        eq("online not loaded yet -> fallback name", "Alice", r.resolve(alice, "Alice", "name"));
        cache.refresh(alice);
        int loadsAfter = LOADS.get();
        eq("name", "Alice", r.resolve(alice, "x", "name"));
        eq("no race -> None", "None", r.resolve(alice, "x", "race"));
        eq("exp", "0", r.resolve(alice, "x", "exp"));
        eq("rank", "Novice", r.resolve(alice, "x", "rank"));
        eq("has_race false", "false", r.resolve(alice, "x", "has_race"));
        eq("placeholders never hit the loader", loadsAfter, LOADS.get());

        // --- player without a character row (loaded as absent)
        cache.track(bob);
        cache.refresh(bob);
        eq("no character name fallback", "Bob", r.resolve(bob, "Bob", "name"));
        eq("no character race", "None", r.resolve(bob, "Bob", "race"));
        eq("no character exp", "0", r.resolve(bob, "Bob", "exp"));
        eq("no character has_race", "false", r.resolve(bob, "Bob", "has_race"));

        // --- race change + EXP/rank change followed by refresh
        DB.put(alice, ch(alice, "Alice", "dragon", 1500, "Apprentice"));
        eq("stale until refresh (race)", "None", r.resolve(alice, "x", "race"));
        cache.refresh(alice);
        eq("race after refresh", "Dragon", r.resolve(alice, "x", "race"));
        eq("exp after refresh", "1500", r.resolve(alice, "x", "exp"));
        eq("rank after refresh", "Apprentice", r.resolve(alice, "x", "rank"));
        eq("has_race true", "true", r.resolve(alice, "x", "has_race"));
        DB.put(alice, ch(alice, "Alice", "dragon", 5200, "Adventurer"));
        cache.refresh(alice);
        eq("exp update", "5200", r.resolve(alice, "x", "exp"));
        eq("rank update", "Adventurer", r.resolve(alice, "x", "rank"));
        DB.put(alice, ch(alice, "Alice", null, 5200, "Adventurer"));
        cache.refresh(alice);
        eq("race reset", "None", r.resolve(alice, "x", "race"));
        eq("has_race after reset", "false", r.resolve(alice, "x", "has_race"));
        DB.put(alice, ch(alice, "Alice", "olddragon", 5200, "Adventurer"));
        cache.refresh(alice);
        eq("legacy race id shown as-is", "olddragon", r.resolve(alice, "x", "race"));

        // --- put() from the join listener
        cache.put(ch(bob, "Bob", null, 0, "Novice"));
        eq("put -> name", "Bob", r.resolve(bob, "?", "name"));

        // --- offline player: first answer unknown, then loaded in background, TTL reload
        int before = LOADS.get();
        eq("offline first lookup falls back", "None", r.resolve(offl, "Olly", "race"));
        eq("offline background load started", before + 1, LOADS.get());
        eq("offline now cached race", "Elf", r.resolve(offl, "Olly", "race"));
        eq("offline cached exp", "1500", r.resolve(offl, "Olly", "exp"));
        eq("offline cached lookups do not reload", before + 1, LOADS.get());
        DB.put(offl, ch(offl, "Olly", "elf", 1600, "Apprentice"));
        now.addAndGet(CharacterCache.OFFLINE_TTL_MS + 1);
        eq("offline stale value served while reloading", "1500", r.resolve(offl, "Olly", "exp"));
        eq("offline reloaded after TTL", "1600", r.resolve(offl, "Olly", "exp"));
        eq("unknown offline player", "None", r.resolve(ghost, "Ghost", "race"));
        eq("unknown offline player name fallback", "Ghost", r.resolve(ghost, "Ghost", "name"));

        // --- quit: online entry dropped, no leak
        cache.untrack(alice);
        int beforeQuit = LOADS.get();
        r.resolve(alice, "x", "race");
        eq("after quit the player is looked up like an offline player (background load)", beforeQuit + 1, LOADS.get());
        eq("after quit the offline load fills the cache", "olddragon", r.resolve(alice, "x", "race"));

        // --- failed load keeps previous data
        cache.track(bob);
        cache.put(ch(bob, "Bob", "human", 10, "Novice"));
        failLoads = true;
        cache.refresh(bob);
        failLoads = false;
        eq("failed refresh keeps old race", "Human", r.resolve(bob, "Bob", "race"));
        eq("failed refresh reported", true, !errors.isEmpty());

        // --- population via the tracker report
        src.v = report(Map.of("human", 4L, "elf", 3L, "beast", 2L, "dragon", 1L, "oldrace", 5L), 7, configured);
        pop.refresh();
        eq("total", "22", r.resolve(null, null, "population_total"));
        eq("human", "4", r.resolve(null, null, "population_human"));
        eq("elf", "3", r.resolve(null, null, "population_elf"));
        eq("beast", "2", r.resolve(null, null, "population_beast"));
        eq("dragon", "1", r.resolve(null, null, "population_dragon"));
        eq("unassigned", "7", r.resolve(null, null, "population_unassigned"));
        eq("assigned", "15", r.resolve(null, null, "population_assigned"));
        eq("legacy", "5", r.resolve(null, null, "population_legacy"));
        eq("legacy id", "5", r.resolve(null, null, "population_oldrace"));
        eq("sum check", 22L, 4L + 3 + 2 + 1 + 5 + 7);
        eq("unheld valid id -> 0", "0", r.resolve(null, null, "population_goblin"));
        eq("invalid id -> null", null, r.resolve(null, null, "population_bad id!"));
        eq("population_ alone -> null", null, r.resolve(null, null, "population_"));
        // population update after a race change
        src.v = report(Map.of("human", 4L, "elf", 3L, "beast", 2L, "dragon", 2L, "oldrace", 5L), 6, configured);
        eq("stale until refresh", "1", r.resolve(null, null, "population_dragon"));
        pop.refresh();
        eq("dragon after refresh", "2", r.resolve(null, null, "population_dragon"));
        eq("unassigned after refresh", "6", r.resolve(null, null, "population_unassigned"));
        int calls = popCalls.get();
        for (int i = 0; i < 1000; i++) {
            r.resolve(null, null, "population_total");
        }
        eq("reading population never calls the tracker", calls, popCalls.get());

        // --- population refresh coalescing + failure
        CompletableFuture<PopulationReport> slow = new CompletableFuture<>();
        AtomicInteger started = new AtomicInteger();
        PopulationCache p2 = new PopulationCache(() -> {
            started.incrementAndGet();
            return started.get() == 1 ? slow : CompletableFuture.completedFuture(src.v);
        }, errors::add);
        p2.refresh();
        p2.refresh();
        p2.refresh();
        eq("requests while running are merged", 1, started.get());
        slow.complete(report(Map.of(), 0, configured));
        eq("one follow-up run after completion", 2, started.get());
        PopulationCache p3 = new PopulationCache(() -> CompletableFuture.failedFuture(new RuntimeException("x")), errors::add);
        p3.refresh();
        eq("failure leaves null report", null, p3.report());
        p3.refresh();

        // --- unknown placeholders, case-insensitivity
        eq("unknown -> null", null, r.resolve(bob, "Bob", "nonsense"));
        eq("case-insensitive", "Human", r.resolve(bob, "Bob", "RACE"));
        eq("case-insensitive population", "22", r.resolve(null, null, "Population_Total"));

        System.out.println(checks + " checks, " + failures + " failures");
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : "CHECKS FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }

    static final class AtomicReference_<T> {
        T v;
        AtomicReference_(T v) { this.v = v; }
    }
}
