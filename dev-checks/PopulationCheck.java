package com.veyrion.character.race;

import java.util.*;

/** Standalone check for PopulationSnapshot, PopulationReport and the total-population overload of the calculator. */
public class PopulationCheck {
    static int fails = 0;
    static void check(boolean ok, String m) { if (!ok) { fails++; System.out.println("FAIL " + m); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    static final List<PopulationReport.Configured> CFG = List.of(
            new PopulationReport.Configured("human", true), new PopulationReport.Configured("elf", true),
            new PopulationReport.Configured("beast", true), new PopulationReport.Configured("dragon", true));

    public static void main(String[] a) {
        // 1 empty database: no NaN, nothing most/least
        var empty = PopulationReport.build(PopulationSnapshot.EMPTY, CFG);
        check(empty.total() == 0 && empty.assigned() == 0 && empty.unassigned() == 0 && empty.legacy() == 0, "empty counts");
        check(empty.mostPopulated() == null && empty.leastPopulated() == null, "empty most/least");
        for (var r : empty.races()) check(r.percentOfTotal() == 0 && r.percentOfAssigned() == 0, "empty pct " + r.id());
        check(empty.unassignedPercentOfTotal() == 0 && empty.legacyPercentOfTotal() == 0, "empty other pct");

        // 2 all four races + unassigned + legacy + mixed-case id merge
        var snap = new PopulationSnapshot(Map.of("human", 40L, "elf", 30L, "beast", 10L, "dragon", 5L,
                "orc", 3L, "ORC", 2L, "goblin", 1L), 9L);
        check(snap.assigned() == 91 && snap.total() == 100 && snap.count("Orc") == 5, "snapshot sums/merge");
        var r = PopulationReport.build(snap, CFG);
        check(r.total() == 100 && r.assigned() == 91 && r.unassigned() == 9 && r.legacy() == 6, "report counts");
        check(near(r.races().get(0).percentOfTotal(), 40.0), "human %");
        check(near(r.races().get(0).percentOfAssigned(), 40.0 * 100 / 91), "human % assigned");
        double sum = r.races().stream().mapToDouble(PopulationReport.RaceRow::percentOfTotal).sum()
                + r.legacyPercentOfTotal() + r.unassignedPercentOfTotal();
        check(near(sum, 100.0), "percent rows sum to 100, got " + sum);
        long rows = r.races().stream().mapToLong(PopulationReport.RaceRow::count).sum() + r.legacy() + r.unassigned();
        check(rows == r.total(), "no double counting");
        check(r.mostPopulated().id().equals("human") && r.leastPopulated().id().equals("dragon"), "most/least");
        check(r.legacyRaces().size() == 2 && r.legacyRaces().get(0).id().equals("orc") && r.legacyRaces().get(0).count() == 5, "legacy rows");

        // 3 disabled configured race keeps its own row, excluded from most/least, not legacy
        var cfg2 = new ArrayList<>(CFG);
        cfg2.set(3, new PopulationReport.Configured("dragon", false));
        var r2 = PopulationReport.build(new PopulationSnapshot(Map.of("dragon", 1L, "elf", 2L), 0), cfg2);
        check(r2.legacy() == 0 && r2.races().get(3).count() == 1 && !r2.races().get(3).enabled(), "disabled row");
        check(r2.leastPopulated().id().equals("human") && r2.mostPopulated().id().equals("elf"), "most/least skip disabled");

        // 4 only unassigned characters
        var r3 = PopulationReport.build(new PopulationSnapshot(Map.of(), 7L), CFG);
        check(r3.total() == 7 && r3.assigned() == 0 && r3.mostPopulated() == null && near(r3.unassignedPercentOfTotal(), 100), "only unassigned");
        check(r3.races().get(0).percentOfAssigned() == 0, "no div by zero (assigned 0)");

        // 5 ties go to the first in file order
        var r4 = PopulationReport.build(new PopulationSnapshot(Map.of("human", 5L, "elf", 5L, "beast", 5L, "dragon", 5L), 0), CFG);
        check(r4.mostPopulated().id().equals("human") && r4.leastPopulated().id().equals("human"), "ties");

        // 6 negative / zero counts ignored
        var s6 = new PopulationSnapshot(Map.of("human", 0L), 0);
        check(s6.byRace().isEmpty(), "zero ignored");

        // 7 calculator: total includes unassigned + legacy
        var entries = List.of(new RaceChanceCalculator.Entry("human", 35), new RaceChanceCalculator.Entry("elf", 30),
                new RaceChanceCalculator.Entry("beast", 25), new RaceChanceCalculator.Entry("dragon", 10));
        var p = new RaceChanceCalculator.Params(true, 5, 1.0, 0.5, 2.0, 0.05);
        Map<String, Long> pops = Map.of("human", 40L, "elf", 30L, "beast", 10L, "dragon", 5L);
        var legacyTotal = RaceChanceCalculator.compute(entries, pops, p);               // old: total = 85
        var fullTotal = RaceChanceCalculator.compute(entries, pops, 100L, p);          // new: 85 + 6 legacy + 9 unassigned
        check(near(legacyTotal.get(0).expected(), 85 * 0.35), "old expected");
        check(near(fullTotal.get(0).expected(), 100 * 0.35), "new expected uses all registered");
        check(near(fullTotal.stream().mapToDouble(RaceChanceCalculator.Chance::chance).sum(), 1.0), "sum 1");
        check(fullTotal.get(0).population() == 40, "population stays per race");
        // total lower than enabled sum is raised to it
        var low = RaceChanceCalculator.compute(entries, pops, 10L, p);
        check(near(low.get(0).expected(), 85 * 0.35), "total floor");
        // empty database
        var e = RaceChanceCalculator.compute(entries, Map.of(), 0L, p);
        check(near(e.get(0).chance(), 0.35) && near(e.get(3).chance(), 0.10), "empty = base weights");
        // many unassigned: all multipliers rise together
        var many = RaceChanceCalculator.compute(entries, Map.of(), 1000L, p);
        check(many.stream().allMatch(c -> c.multiplier() == 2.0), "unassigned-only saturates multiplier");

        System.out.println(fails == 0 ? "ALL CHECKS PASSED" : fails + " CHECK(S) FAILED");
        if (fails != 0) System.exit(1);
    }
}
