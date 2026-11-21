package com.veyrion.character.awakening;

/** Pure checks for CoreMath (Ancient Core geometry). No Bukkit needed. See README.txt. */
public final class CoreMathCheck {
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
        // Runes: constant radius, even spacing, tilt keeps the distance from the centre.
        for (int count = 1; count <= 8; count++) {
            for (double angle = 0; angle < 7; angle += 0.7) {
                double[][] p = new double[count][];
                for (int i = 0; i < count; i++) {
                    p[i] = CoreMath.orbit(i, count, angle, 1.3, 0.35);
                    double d = Math.sqrt(p[i][0] * p[i][0] + p[i][1] * p[i][1] + p[i][2] * p[i][2]);
                    check(near(d, 1.3), "orbit radius count=" + count);
                }
                if (count >= 2) {
                    double first = dist(p[0], p[1]);
                    for (int i = 0; i < count; i++) {
                        check(near(dist(p[i], p[(i + 1) % count]), first) || count == 2, "even spacing count=" + count);
                    }
                }
            }
        }
        check(CoreMath.orbit(0, 0, 1.0, 1.0, 0.0).length == 3, "count 0 does not divide by zero");

        // Flow: starts exactly at the source, ends exactly at the crystal, never leaves a sane box.
        double[] start = CoreMath.flow(5.0, 4.8, -5.0, 0.0, 0.9, 0.45);
        check(near(start[0], 5.0) && near(start[1], 4.8) && near(start[2], -5.0), "flow starts at the source");
        double[] end = CoreMath.flow(5.0, 4.8, -5.0, 1.0, 0.9, 0.45);
        check(near(end[0], 0) && near(end[1], 0) && near(end[2], 0), "flow ends at the crystal");
        double previous = Double.MAX_VALUE;
        for (double f = 0; f <= 1.0001; f += 0.02) {
            double[] q = CoreMath.flow(5.0, 4.8, -5.0, f, 0.9, 0.45);
            double horizontal = Math.hypot(q[0], q[2]);
            check(horizontal <= previous + 1e-9, "horizontal distance never grows at f=" + f);
            previous = horizontal;
            check(q[1] <= 4.8 + 0.9 + 1e-9, "never above source + arc at f=" + f);
        }
        double[] clamped = CoreMath.flow(1, 1, 1, 7.0, 0.9, 0.45);
        check(near(clamped[0], 0) && near(clamped[2], 0), "f above 1 is clamped");

        // Mote size: tiny at the ends, never negative, fullest in the middle.
        check(CoreMath.flowSize(0.5) > CoreMath.flowSize(0.0) && CoreMath.flowSize(0.5) > CoreMath.flowSize(1.0),
                "mote biggest in the middle");
        for (double f = -1; f <= 2; f += 0.05) {
            check(CoreMath.flowSize(f) >= 0.0 && CoreMath.flowSize(f) <= 1.0, "flowSize range at " + f);
        }

        // Bob: bounded by the amplitude, one full cycle per period, zero amplitude means no movement.
        for (double t = 0; t < 400; t += 3.7) {
            check(Math.abs(CoreMath.bob(t, 90.0, 0.07)) <= 0.07 + 1e-12, "bob bounded");
            check(CoreMath.bob(t, 90.0, 0.0) == 0.0, "bob amplitude 0");
        }
        check(near(CoreMath.bob(10.0, 90.0, 0.07), CoreMath.bob(100.0, 90.0, 0.07)), "bob periodic");
        check(Double.isFinite(CoreMath.bob(5.0, 0.0, 1.0)), "bob period 0 is safe");

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }
}
