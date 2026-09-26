import com.hackgt.behindalert.Dsp;
import java.util.Random;
public class DspTest {
  static Random rnd = new Random(1);
  // clap: decaying noise burst starting at `start`; R delayed by d samples (d>0 => L first), R gain g, plus background noise + one wall echo
  static float[][] clap(int n, int start, int d, double g, double noise, boolean echo) {
    double[] src = new double[n + 200];
    for (int i = 0; i < src.length; i++) { int t = i - start; src[i] = t < 0 ? 0 : rnd.nextGaussian() * Math.exp(-t / 300.0); }
    float[] l = new float[n], r = new float[n];
    for (int i = 0; i < n; i++) {
      double a = src[i], b = i - d >= 0 && i - d < src.length ? src[i - d] : 0;
      if (echo) { if (i - 400 >= 0) a += 0.5 * src[i - 400]; if (i - 400 - d + 7 >= 0) b += 0.5 * src[i - 400 - d + 7]; }
      l[i] = (float) (0.3 * a + noise * rnd.nextGaussian());
      r[i] = (float) (0.3 * g * b + noise * rnd.nextGaussian());
    }
    return new float[][]{l, r};
  }
  public static void main(String[] x) {
    int fs = 48000, maxLag = (int) Math.ceil(0.30 / 343 * fs);
    Dsp.GccPhat g = new Dsp.GccPhat(4096);
    int[] delays = {22, -22, 15, -15, 0};
    for (boolean echo : new boolean[]{false, true})
      for (double noise : new double[]{0.001, 0.01})
        for (int d : delays) {
          float[][] c = clap(4096, 2500, d, 0.6, noise, echo);
          Dsp.GccResult gr = g.run(c[0], c[1], maxLag, fs, 150, 12000);
          Dsp.OnsetResult o = Dsp.onset(c[0], c[1], 0.3, maxLag);
          System.out.printf("echo=%-5s noise=%.3f  true lag(R-first +)=%+3d | GCC %+6.2f conf %.2f | onset %+4.0f valid=%s%n",
            echo, noise, -d, gr.lag, gr.peak, o.lag, o.valid);
        }
    System.out.println("score behind: " + Dsp.score(-450, 1, 466));
  }
}
