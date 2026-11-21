package com.veyrion.character.awakening;

/** Pure checks of the altar maths: the floating-plate bug, easing and looping. No Bukkit needed. */
public final class PillarMathCheck {

    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) {
        double segH = 1.3;
        double stump = 1.1;
        double tall = 5.2;
        int n = PillarMath.segmentCount(tall, segH);
        check(n == 4, "5.2 / 1.3 needs 4 segments, got " + n);
        check(PillarMath.segmentCount(0.1, segH) == 1, "always at least one segment");

        // The old bug: at the stump height only segment 0 has anything to show. Every other segment must be
        // hidden (zero scale), never a flat plate.
        int hiddenAtRest = 0;
        for (int k = 0; k < n; k++) {
            double v = PillarMath.visible(stump, k, segH);
            if (PillarMath.hidden(v)) {
                hiddenAtRest++;
            }
            check(PillarMath.hidden(v) == (v <= PillarMath.HIDE_EPS), "hidden() agrees with visible, k=" + k);
        }
        check(hiddenAtRest == n - 1, "at rest exactly the upper segments are hidden: " + hiddenAtRest);
        check(!PillarMath.hidden(PillarMath.visible(stump, 0, segH)), "segment 0 is visible at rest");

        // Over the whole rise: a segment is either hidden or has a positive height; never in between, and
        // the visible heights always add up to the shaft height.
        for (int step = 0; step <= 1000; step++) {
            double p = step / 1000.0;
            double h = stump + (tall - stump) * p;
            double sum = 0;
            for (int k = 0; k < n; k++) {
                double v = PillarMath.visible(h, k, segH);
                check(v >= 0 && v <= segH + 1e-9, "visible within 0..segH");
                if (!PillarMath.hidden(v)) {
                    check(v > PillarMath.HIDE_EPS, "a drawn segment has real height");
                }
                sum += v;
            }
            check(Math.abs(sum - Math.min(h, n * segH)) < 1e-6, "segments add up to the shaft at p=" + p);
        }
        // Full height: nothing hidden except possibly an empty last slot.
        check(!PillarMath.hidden(PillarMath.visible(tall, n - 1, segH)), "top segment shows at full height");

        // Easing and looping.
        check(PillarMath.ease(0) == 0 && PillarMath.ease(1) == 1 && PillarMath.ease(0.5) == 0.5, "smoothstep");
        check(PillarMath.ease(-3) == 0 && PillarMath.ease(9) == 1, "ease clamps");
        check(PillarMath.easeOut(0) == 0 && PillarMath.easeOut(1) == 1, "easeOut ends");
        check(Math.abs(PillarMath.wrap(2.25) - 0.25) < 1e-12 && PillarMath.wrap(3.0) == 0.0, "wrap");
        check(PillarMath.wrapped(0.9, 0.05) && !PillarMath.wrapped(0.1, 0.4), "wrap detection");
        // A looping display must jump exactly once per lap.
        double prev = 0;
        int jumps = 0;
        for (int tick = 1; tick <= 400; tick++) {
            double f = PillarMath.wrap(tick / 26.0);
            if (PillarMath.wrapped(prev, f)) {
                jumps++;
            }
            prev = f;
        }
        check(jumps == 15, "400 ticks at 26 ticks per lap make 15 laps, got " + jumps);

        // The eight shockwave plates must overlap (no gaps) at every radius.
        for (double r = 0.6; r <= 8.2; r += 0.2) {
            double chord = 2.0 * r * Math.sin(Math.PI / 8);
            check(PillarMath.plateWidth(r, 8) >= chord, "plates cover the ring at r=" + r);
        }

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
