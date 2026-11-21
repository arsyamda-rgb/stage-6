#!/usr/bin/env python3
"""Checks the SQLite v2 -> v3 upgrade and the pending_vyr SQL with Python's sqlite3 (no Java, no Paper).

DDL is extracted from the Java sources, so it tests the real statements, not a copy:
  python3 dev-checks/migration_check.py <old DatabaseManager.java> <new DatabaseManager.java> <PendingVyrRepository.java>
The old file is the Stage 4/5 source before economy (schema v2); it builds a realistic v2 database.
"""
import re, sqlite3, sys, tempfile, os, shutil

old_src, new_src, repo_src = (open(p, encoding='utf-8').read() for p in sys.argv[1:4])
checks = fails = 0
def ok(what, cond):
    global checks, fails
    checks += 1
    if not cond:
        fails += 1
        print("FAIL", what)

def blocks(src, method):
    """All SQL text blocks / one-line execute() strings inside one Java method, in order."""
    m = re.search(r'(?:private|public)[^\n]*\b' + method + r'\([^)]*\)[^{]*\{(.*?)\n    \}\n', src, re.S)
    body = m.group(1)
    out = []
    for t in re.finditer(r'"""\n(.*?)"""|statement\.execute\("([^"]*)"\)', body, re.S):
        out.append((t.group(1) or t.group(2)).strip())
    return out

# ---- 1. build a REAL v2 database (old DDL) with data
d = tempfile.mkdtemp()
path = os.path.join(d, 'characters.db')
con = sqlite3.connect(path)
con.execute("PRAGMA foreign_keys = ON")
base = blocks(old_src, 'createSchema')
chars_ddl = [b for b in base if 'CREATE TABLE IF NOT EXISTS characters' in b][0]
quest_ddl = blocks(old_src, 'createQuestTables')
ok("old DDL extracted", chars_ddl and len(quest_ddl) == 2)
con.execute(chars_ddl)
for b in quest_ddl:
    con.execute(b)
con.execute("PRAGMA user_version = 2")
U = ['11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222']
con.executemany("INSERT INTO characters VALUES (?,?,?,?,?,?,?)", [
    (U[0], 'Alice', 'dragon', 1200, 'Apprentice', 1700000000000, 1),
    (U[1], 'Bob', None, 0, 'Novice', 1700000001000, 0)])
con.execute("INSERT INTO quest_progress VALUES (?,?,?,?,?,?)", (U[0], 'zombie_cull', 'COMPLETED', 1, 2, 3))
con.execute("INSERT INTO quest_progress VALUES (?,?,?,?,?,?)", (U[1], 'the_lost_relic', 'ACTIVE', 5, 0, 0))
con.execute("INSERT INTO quest_objectives VALUES (?,?,?,?)", (U[1], 'the_lost_relic', 'find_relic', 0))
con.commit(); con.close()
before = shutil.copy(path, path + '.snapshot')

def snapshot(c):
    return {t: c.execute(f"SELECT * FROM {t} ORDER BY 1,2").fetchall()
            for t in ('characters', 'quest_progress', 'quest_objectives')}

# ---- 2. run the NEW migration exactly as createSchema does
def migrate(c):
    base_new = blocks(new_src, 'createSchema')
    ok("new createSchema still creates characters", any('CREATE TABLE IF NOT EXISTS characters' in b for b in base_new))
    c.execute([b for b in base_new if 'CREATE TABLE IF NOT EXISTS characters' in b][0])
    version = c.execute("PRAGMA user_version").fetchone()[0]
    assert re.search(r'SCHEMA_VERSION = 3;', new_src)
    if version < 2:
        for b in blocks(new_src, 'createQuestTables'):
            c.execute(b)
    if version < 3:
        for b in blocks(new_src, 'createPendingVyrTable'):
            c.execute(b)
        # ensureColumn(...) as written in Java
        am = re.search(r'ensureColumn\(statement, "(\w+)", "(\w+)", "([^"]+)"\);', new_src)
        table, column, definition = am.groups()
        cols = [r[1] for r in c.execute(f"PRAGMA table_info({table})")]
        if column not in cols:
            c.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")
    c.execute("PRAGMA user_version = 3")
    c.commit()

con = sqlite3.connect(path); con.execute("PRAGMA foreign_keys = ON")
want = snapshot(con)
migrate(con)
ok("user_version is 3", con.execute("PRAGMA user_version").fetchone()[0] == 3)
ok("existing characters/quests untouched", snapshot(con) == want)
cols = {r[1] for r in con.execute("PRAGMA table_info(pending_vyr)")}
ok("pending_vyr has all columns", cols == {'id', 'uuid', 'quest_id', 'amount', 'state', 'created_at', 'claimed_at'})
ok("quest tables not duplicated", con.execute("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('quest_progress','quest_objectives','characters')").fetchone()[0] == 3)

# ---- 3. idempotent: run again (crash before user_version bump / second start)
con.execute("PRAGMA user_version = 2"); con.commit()
migrate(con)
ok("re-running migration keeps data", snapshot(con) == want)
ok("re-running migration keeps one pending_vyr", con.execute("SELECT COUNT(*) FROM sqlite_master WHERE name='pending_vyr'").fetchone()[0] == 1)

# ---- 4. table made by the EARLIER build (no claimed_at) gets the column, rows kept
con.execute("DROP TABLE pending_vyr")
con.execute("""CREATE TABLE pending_vyr (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, quest_id TEXT NOT NULL,
  amount REAL NOT NULL CHECK (amount > 0), state TEXT NOT NULL CHECK (state IN ('PENDING','PAYING')), created_at INTEGER NOT NULL)""")
con.execute("INSERT INTO pending_vyr (uuid, quest_id, amount, state, created_at) VALUES (?,?,?,?,?)", (U[0], 'zombie_cull', 25, 'PAYING', 9))
con.execute("PRAGMA user_version = 2"); con.commit()
migrate(con)
row = con.execute("SELECT state, claimed_at FROM pending_vyr").fetchone()
ok("old-build table upgraded, row kept, claimed_at default 0", row == ('PAYING', 0))
con.execute("DELETE FROM pending_vyr"); con.commit()

# ---- 5. quest-completion transaction + ticket lifecycle SQL (from QuestRepository / PendingVyrRepository)
def sql(src, start):
    m = re.search(re.escape(start) + r'[^"]*"([^"]*)"', src)
    return m.group(1)
ins = sql(repo_src, 'prepareStatement(\n                "INSERT INTO pending_vyr')
ins = re.search(r'"(INSERT INTO pending_vyr[^"]*)"', repo_src).group(1)
claim = re.search(r'"(UPDATE pending_vyr SET state = \'PAYING\'[^"]*)"', repo_src).group(1)
release = re.search(r'"(UPDATE pending_vyr SET state = \'PENDING\'[^"]*)"', repo_src).group(1)
settle = re.search(r'"(DELETE FROM pending_vyr WHERE id = \? AND state = \'PAYING\')"', repo_src).group(1)
delete = re.search(r'"(DELETE FROM pending_vyr WHERE id = \?)"', repo_src).group(1)
lst = re.search(r'"(SELECT id, uuid, quest_id, amount, created_at, claimed_at FROM pending_vyr "\s*\+\s*"WHERE state = \'PAYING\' ORDER BY id)"', repo_src, re.S).group(1)
lst = re.sub(r'"\s*\+\s*"', '', lst)

con.execute(ins, (U[0], 'zombie_cull', 25.0, 100))
tid = con.execute("SELECT last_insert_rowid()").fetchone()[0]
ok("ticket id from last_insert_rowid is the new row", tid == con.execute("SELECT MAX(id) FROM pending_vyr").fetchone()[0] and tid > 0)
ok("insert starts PENDING", con.execute("SELECT state FROM pending_vyr WHERE id=?", (tid,)).fetchone()[0] == 'PENDING')
c1 = con.execute(claim, (555, tid)).rowcount
c2 = con.execute(claim, (556, tid)).rowcount
ok("only first claim wins", (c1, c2) == (1, 0))
ok("claimed_at stored", con.execute("SELECT claimed_at FROM pending_vyr").fetchone()[0] == 555)
ok("PAYING appears in review list", len(con.execute(lst).fetchall()) == 1)
ok("settle deletes PAYING", con.execute(settle, (tid,)).rowcount == 1)
con.execute(ins, (U[1], 'zombie_cull', 5.5, 101)); t2 = con.execute("SELECT last_insert_rowid()").fetchone()[0]
ok("settle ignores PENDING (cannot erase an unpaid ticket)", con.execute(settle, (t2,)).rowcount == 0)
con.execute(claim, (1, t2))
ok("release PAYING->PENDING", con.execute(release, (t2,)).rowcount == 1)
ok("release ignores PENDING", con.execute(release, (t2,)).rowcount == 0)
try:
    con.execute(ins, (U[0], 'q', 0.0, 1)); ok("zero amount rejected", False)
except sqlite3.IntegrityError:
    ok("zero amount rejected", True)
ok("delete paid ticket", con.execute(delete, (t2,)).rowcount == 1)
# character deleted -> ticket stays (no FK on purpose, so an unpaid reward is never silently erased)
con.execute(ins, (U[1], 'q', 3.0, 1)); con.execute("DELETE FROM characters WHERE uuid=?", (U[1],))
ok("ticket survives character delete", con.execute("SELECT COUNT(*) FROM pending_vyr").fetchone()[0] == 1)

print(f"{checks} checks, {fails} failures")
print("ALL CHECKS PASSED" if not fails else "FAILED")
sys.exit(1 if fails else 0)
