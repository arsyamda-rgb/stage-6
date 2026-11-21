"""Runs the SQL of CharacterRepository.reconcileRanks / applyExp against SQLite (Python's sqlite3).
Usage: python3 dev-checks/rank_reconcile_check.py src/main/java/com/veyrion/character/character/CharacterRepository.java
Checks that the statements are present, valid, and only touch adventure_rank."""
import re, sqlite3, sys

src = open(sys.argv[1], encoding="utf-8").read()
fails = 0

def check(ok, what):
    global fails
    if not ok:
        fails += 1
        print("FAIL:", what)

select = re.search(r'"(SELECT uuid, adventure_exp, adventure_rank FROM characters)"', src)
update = re.search(r'"(UPDATE characters SET adventure_rank = \? WHERE uuid = \?)"', src)
check(select is not None, "reconcile SELECT present")
check(update is not None, "reconcile UPDATE present")

db = sqlite3.connect(":memory:")
db.execute("""CREATE TABLE characters (uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, race TEXT,
  adventure_exp INTEGER NOT NULL, adventure_rank TEXT NOT NULL, created_at INTEGER NOT NULL,
  has_completed_gacha INTEGER NOT NULL DEFAULT 0)""")
rows = [("u1", "a", None, 6000, "Novice", 1, 0), ("u2", "b", "elf", 10, "Hero", 1, 0),
        ("u3", "c", None, 1000, "Apprentice", 1, 1)]
db.executemany("INSERT INTO characters VALUES (?,?,?,?,?,?,?)", rows)

ranks = [("Novice", 0), ("Apprentice", 1000), ("Adventurer", 5000)]
def rank_for(exp):
    return [n for n, r in ranks if exp >= r][-1]

fixes = [(rank_for(e), u) for u, e, r in db.execute(select.group(1)) if rank_for(e) != r]
check(len(fixes) == 2, "two stale rows found, got %d" % len(fixes))
for fix in fixes:
    db.execute(update.group(1), fix)
got = {u: (race, rk, e) for u, race, e, rk in db.execute("SELECT uuid, race, adventure_exp, adventure_rank FROM characters")}
check(got["u1"][1] == "Adventurer", "u1 raised")
check(got["u2"][1] == "Novice", "u2 lowered")
check(got["u3"][1] == "Apprentice", "u3 untouched")
check(got["u2"][0] == "elf" and got["u1"][2] == 6000, "race and exp untouched")
check(db.execute(select.group(1)).fetchall() and not [1 for u, e, r in db.execute(select.group(1)) if rank_for(e) != r],
      "second run finds nothing to fix")

check("oldRank = ranks.rankFor(oldExp).name()" in src, "applyExp derives oldRank from the old EXP")
check("map(this::fresh)" in src, "find() normalises the rank")
print("ALL CHECKS PASSED" if fails == 0 else "%d CHECK(S) FAILED" % fails)
sys.exit(1 if fails else 0)
