package com.veyrion.character.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure checks for NpcData (storage format, limits, bad input). No Bukkit needed. See README.txt. */
public final class NpcDataCheck {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static NpcData sample() {
        return new NpcData("elder_1", "<gold>Elder", "VALUE", "SIG", "Notch", "zombie_cull",
                List.of("Hello <red>traveler", "Be careful"), "world", 10.5, 64.0, -3.25, 90f, -15f, false);
    }

    public static void main(String[] args) {
        // Round trip through the plain-map storage format.
        NpcData original = sample();
        Map<String, Object> map = original.toMap();
        check(original.equals(NpcData.fromMap("elder_1", map)), "round trip keeps everything");
        check(original.equals(NpcData.fromMap("ELDER_1", map)), "id is case-insensitive");

        NpcData bare = new NpcData("a", "a", null, null, null, null, List.of(), "w", 0, 0, 0, 0f, 0f, true);
        check(bare.equals(NpcData.fromMap("a", bare.toMap())), "round trip of a bare npc");
        check(!bare.toMap().containsKey("skin-value") && !bare.toMap().containsKey("quest"),
                "absent skin and quest are not written");

        // Unusable entries are rejected, never thrown.
        Map<String, Object> noWorld = new LinkedHashMap<>(map);
        noWorld.remove("world");
        check(NpcData.fromMap("x", noWorld) == null, "missing world");
        Map<String, Object> noX = new LinkedHashMap<>(map);
        noX.remove("x");
        check(NpcData.fromMap("x", noX) == null, "missing x");
        Map<String, Object> nan = new LinkedHashMap<>(map);
        nan.put("y", Double.NaN);
        check(NpcData.fromMap("x", nan) == null, "NaN coordinate");
        Map<String, Object> inf = new LinkedHashMap<>(map);
        inf.put("z", Double.POSITIVE_INFINITY);
        check(NpcData.fromMap("x", inf) == null, "infinite coordinate");
        Map<String, Object> text = new LinkedHashMap<>(map);
        text.put("x", "12");
        check(NpcData.fromMap("x", text) == null, "coordinate as text");
        check(NpcData.fromMap("Bad Id!", map) == null, "bad id");
        check(NpcData.fromMap("", map) == null, "empty id");
        check(NpcData.fromMap(null, map) == null, "null id");
        check(NpcData.fromMap("x".repeat(33), map) == null, "id too long");

        // Soft problems are repaired.
        Map<String, Object> soft = new LinkedHashMap<>(map);
        soft.remove("name");
        soft.put("visible", "maybe");
        soft.remove("yaw");
        NpcData repaired = NpcData.fromMap("x", soft);
        check(repaired != null && repaired.displayName().equals("x"), "missing name falls back to the id");
        check(repaired != null && repaired.visible(), "unknown visible value means visible");
        check(repaired != null && repaired.yaw() == 0f, "missing yaw is 0");
        Map<String, Object> longName = new LinkedHashMap<>(map);
        longName.put("name", "n".repeat(500));
        check(NpcData.fromMap("x", longName).displayName().length() == NpcData.MAX_NAME_LENGTH, "name trimmed");
        Map<String, Object> many = new LinkedHashMap<>(map);
        List<Object> lines = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            lines.add(i % 5 == 0 ? "" : "line " + i + "y".repeat(400));
        }
        lines.add(42);
        many.put("dialogue", lines);
        NpcData trimmed = NpcData.fromMap("x", many);
        check(trimmed.dialogue().size() == NpcData.MAX_DIALOGUE_LINES, "dialogue capped at 20");
        check(trimmed.dialogue().stream().noneMatch(String::isBlank), "blank lines dropped");
        check(trimmed.dialogue().stream().allMatch(l -> l.length() <= NpcData.MAX_LINE_LENGTH), "lines trimmed");
        Map<String, Object> notList = new LinkedHashMap<>(map);
        notList.put("dialogue", "just a string");
        check(NpcData.fromMap("x", notList).dialogue().isEmpty(), "dialogue that is not a list");
        Map<String, Object> noValue = new LinkedHashMap<>(map);
        noValue.remove("skin-value");
        NpcData noSkin = NpcData.fromMap("x", noValue);
        check(noSkin.skinValue() == null && noSkin.skinSignature() == null && noSkin.skinSource() == null,
                "signature without value is ignored");

        // Immutability and withers.
        NpcData a = sample();
        check(a.withDisplayName("B").displayName().equals("B") && a.displayName().equals("<gold>Elder"),
                "wither does not change the original");
        check(a.withVisible(true).visible() && !a.visible(), "withVisible");
        check(a.withQuest(null).questId() == null, "withQuest(null)");
        check(a.withSkin(null, null, null).skinValue() == null, "withSkin(null)");
        NpcData moved = a.withLocation("nether", 1, 2, 3, 4f, 5f);
        check(moved.world().equals("nether") && moved.x() == 1 && moved.yaw() == 4f && moved.pitch() == 5f,
                "withLocation");
        List<String> external = new ArrayList<>(List.of("one"));
        NpcData copy = a.withDialogue(external);
        external.add("two");
        check(copy.dialogue().size() == 1, "dialogue is copied");
        try {
            copy.dialogue().add("x");
            check(false, "dialogue list is immutable");
        } catch (UnsupportedOperationException expected) {
            // good
        }

        // Dialogue editing.
        NpcData d = bare;
        for (int i = 0; i < NpcData.MAX_DIALOGUE_LINES; i++) {
            d = d.addLine("l" + i);
            check(d != null, "addLine " + i);
        }
        check(d.addLine("one too many") == null, "addLine refuses the 21st line");
        check(d.removeLastLine().dialogue().size() == NpcData.MAX_DIALOGUE_LINES - 1, "removeLastLine");
        check(bare.removeLastLine().dialogue().isEmpty(), "removeLastLine on empty");

        // Ids.
        check(NpcData.validId("a") && NpcData.validId("a-b_9") && NpcData.validId("x".repeat(32)), "valid ids");
        check(!NpcData.validId("A") && !NpcData.validId("a b") && !NpcData.validId("") && !NpcData.validId(null)
                && !NpcData.validId("../x") && !NpcData.validId("a.b"), "invalid ids");
        try {
            new NpcData("Bad", "n", null, null, null, null, List.of(), "w", 0, 0, 0, 0f, 0f, true);
            check(false, "constructor rejects a bad id");
        } catch (IllegalArgumentException expected) {
            // good
        }

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
