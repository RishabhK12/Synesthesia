import com.hackgt.behindalert.CompassModel;

public class CompassTest {
    static void check(boolean b, String what) { if (!b) throw new AssertionError(what); }
    static double lag(double bearing) { return 20 * Math.sin(Math.toRadians(bearing)); }
    public static void main(String[] args) {
        CompassModel m = new CompassModel();
        for (int sector = 0; sector < 8; sector++) {
            for (int clap = 0; clap < 4; clap++) m.add(sector, lag(sector * 45) + (clap - 1.5) * 0.1);
        }
        check(m.complete() && m.usable(), "Eight-sector calibration");
        check(Math.abs(m.predictedLag(45) - m.predictedLag(135)) < .1, "Static front/back ambiguity");
        CompassModel.Estimate first = m.observe(0, 0, lag(45), .5);
        check(first != null && !first.resolved && first.alternative >= 0, "Single sound keeps two possibilities");
        m.observe(200, 0, lag(45), .5);
        check(!m.estimate(0).resolved, "Repeated windows without rotation stay ambiguous");
        m.observe(400, 15, lag(30), .5);
        CompassModel.Estimate rotated = m.observe(600, 35, lag(10), .5);
        check(rotated != null && rotated.resolved, "Motion resolves a stationary source");
        check(Math.abs(CompassModel.wrap180(rotated.primary - 10)) <= 10, "Current phone-relative bearing");
        check(rotated.turnDegrees >= 30, "Recorded turn span");
        String saved = m.serialize(); CompassModel restored = new CompassModel(); restored.deserialize(saved);
        check(restored.usable() && Math.abs(restored.center(2) - m.center(2)) < .001, "Calibration persistence");
        CompassModel quiet = new CompassModel(); quiet.deserialize(saved);
        check(quiet.observe(0, 0, lag(45), .14) == null, "One weak speech window stays hidden");
        check(quiet.observe(40, 0, lag(45), .14) == null && quiet.estimate(0) == null,
                "Overlapping weak windows cannot bypass confirmation");
        check(quiet.observe(200, 0, lag(45), .14) != null, "Consistent soft speech is shown");
        restored.clearSector(2);
        check(!restored.complete() && restored.estimate(0) == null, "Incomplete calibration cannot produce a bearing");
        m.expire(2500);
        check(m.estimate(35) == null, "Stale sound expires");
        check(m.observe(3000, 0, 42, .5) == null, "Reject lag inconsistent with calibration");
        CompassModel bad = new CompassModel();
        for (int i = 0; i < 8; i++) for (int k = 0; k < 4; k++) bad.add(i, 0);
        check(bad.complete() && !bad.usable() && bad.observe(0, 0, 0, .5) == null, "Near-mono calibration rejected");
        check(m.observe(3000, 0, Double.NaN, .9) == null, "Invalid lag rejected");
        System.out.println("CompassTest: calibration, front/back ambiguity, motion, stale tracks and persistence passed.");
    }
}
