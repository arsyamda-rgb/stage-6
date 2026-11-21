package com.veyrion.character.awakening;

/** Standalone check of the altar animation clock (no Bukkit). See dev-checks/README.txt. */
public final class AltarProgressCheck {

    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    public static void main(String[] args) {
        // Fresh state is idle at 0.
        AltarProgress p = new AltarProgress();
        check(near(p.valueAt(0), 0) && p.phaseAt(0) == AltarProgress.Phase.IDLE && !p.targetsUp(), "fresh idle");

        // Full rise: 70 ticks, smooth, monotonic, ends at 1 and is ACTIVE afterwards.
        double d = p.retarget(1.0, 100, 70);
        check(near(d, 70), "full rise duration");
        check(p.phaseAt(100) == AltarProgress.Phase.RISING && p.targetsUp(), "rising phase");
        double last = -1;
        for (int t = 100; t <= 170; t++) {
            double v = p.valueAt(t);
            check(v >= last - 1e-12, "monotonic rise at " + t);
            last = v;
        }
        check(near(p.valueAt(170), 1) && p.phaseAt(170) == AltarProgress.Phase.ACTIVE, "rise ends active");
        check(near(p.valueAt(135), 0.5), "smoothstep midpoint");

        // Reverse in the middle of the rise: no jump, and the way back is proportional to what was reached.
        double before = p.valueAt(120);
        double back = p.retarget(0.0, 120, 60);
        double after = p.valueAt(120);
        check(near(before, after), "no jump on reversal");
        check(near(back, before * 60), "reverse duration proportional");
        check(p.phaseAt(120) == AltarProgress.Phase.LOWERING && !p.targetsUp(), "lowering phase");
        check(near(p.valueAt(120 + back + 1), 0) && p.phaseAt(120 + back + 1) == AltarProgress.Phase.IDLE,
                "lowering ends idle");

        // Reverse again during the descent (player came back).
        double mid = p.valueAt(120 + back / 2);
        p.retarget(1.0, 120 + back / 2, 70);
        check(near(p.valueAt(120 + back / 2), mid), "no jump on second reversal");
        check(p.phaseAt(120 + back / 2) == AltarProgress.Phase.RISING, "rising again");

        // Retarget to where it already is: zero duration.
        AltarProgress q = new AltarProgress();
        check(near(q.retarget(0.0, 5, 60), 0), "already there");
        q.retarget(1.0, 5, 70);
        check(near(q.retarget(1.0, 500, 70), 0) && q.phaseAt(500) == AltarProgress.Phase.ACTIVE, "already up");

        // Keyframes: last keyframe is exactly the target, first is above the start, all within 0..1.
        AltarProgress k = new AltarProgress();
        k.retarget(1.0, 0, 70);
        int n = 6;
        double prev = 0;
        for (int i = 1; i <= n; i++) {
            double v = k.keyframe(i / (double) n);
            check(v > prev && v <= 1.0, "keyframe " + i + " increases");
            prev = v;
        }
        check(near(prev, 1.0), "last keyframe is target");
        k.retarget(0.0, 1000, 60);
        check(near(k.keyframe(1.0), 0.0), "last lowering keyframe is 0");

        // reset
        k.reset();
        check(near(k.valueAt(2000), 0) && !k.targetsUp(), "reset");

        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }
}
