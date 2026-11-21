package com.veyrion.character.character;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stale-rank checks: the rank always follows the EXP under the current rank table. See README.txt. */
public final class RankCheck {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static CharacterData data(long exp, String rank) {
        return new CharacterData(UUID.randomUUID(), "p", null, exp, rank, Instant.EPOCH, false);
    }

    public static void main(String[] args) {
        RankTable table = RankTable.defaults();

        // Right rank stays the same instance.
        CharacterData good = data(1200, "Apprentice");
        check(CharacterRepository.withCurrentRank(good, table) == good, "correct rank is untouched");

        // Stale: more EXP than the stored rank says (e.g. thresholds lowered in config).
        check(CharacterRepository.withCurrentRank(data(6000, "Novice"), table).adventureRank().equals("Adventurer"),
                "stale low rank is raised");
        // Stale: stored rank higher than the EXP allows (thresholds raised).
        check(CharacterRepository.withCurrentRank(data(10, "Hero"), table).adventureRank().equals("Novice"),
                "stale high rank is lowered");
        // Stored rank no longer exists in the table (renamed or removed).
        check(CharacterRepository.withCurrentRank(data(1000, "Legacy"), table).adventureRank().equals("Apprentice"),
                "rank missing from the table");
        // Only the rank changes.
        CharacterData fixed = CharacterRepository.withCurrentRank(data(5000, "Novice"), table);
        check(fixed.adventureExp() == 5000 && fixed.playerName().equals("p"), "other fields kept");

        // New table with other thresholds: every stored value is recomputed from EXP.
        RankTable custom = new RankTable(List.of(new RankTable.Rank("Bronze", 0), new RankTable.Rank("Silver", 100),
                new RankTable.Rank("Gold", 500)));
        check(CharacterRepository.withCurrentRank(data(99, "Hero"), custom).adventureRank().equals("Bronze"), "custom 99");
        check(CharacterRepository.withCurrentRank(data(100, "Novice"), custom).adventureRank().equals("Silver"), "custom 100");
        check(CharacterRepository.withCurrentRank(data(10_000_000, "Novice"), custom).adventureRank().equals("Gold"), "custom top");
        check(CharacterRepository.withCurrentRank(data(0, "Gold"), custom).adventureRank().equals("Bronze"), "custom 0");

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
