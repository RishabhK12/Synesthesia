import com.hackgt.behindalert.*;
import java.util.Random;
public class DirTest {
  static Random rnd = new Random(7);
  static final int FS=48000, W=4096, MAXLAG=(int)Math.ceil(0.30/343*FS);
  static Dsp.GccPhat g = new Dsp.GccPhat(W);
  // angle: -90 = hard left, 0 = front, +90 = hard right. L mic = left ear side.
  static double[] clap(double angleDeg, double gainR, double noise) {
    double s = Math.sin(Math.toRadians(angleDeg));
    int d = (int)Math.round(s * 22 + rnd.nextGaussian()*1.0);        // + => sound on right => R first => L lags
    double shadow = Math.pow(10, -3*s/20);                            // 3 dB head shadow
    double[] src = new double[W+200]; int start=2400;
    for (int i=0;i<src.length;i++){int t=i-start; src[i]= t<0?0:rnd.nextGaussian()*Math.exp(-t/250.0);}
    float[] l=new float[W], r=new float[W];
    for (int i=0;i<W;i++){
      double a = (d>0 ? (i-d>=0?src[i-d]:0) : src[i]);
      double b = (d<0 ? (i+d>=0?src[i+d]:0) : src[i]);
      if (i-500>=0){ a+=0.4*src[i-500]; b+=0.4*src[i-500+3]; }        // room echo
      l[i]=(float)(0.3*a*shadow + noise*rnd.nextGaussian());
      r[i]=(float)(0.3*b/shadow*gainR + noise*rnd.nextGaussian());
    }
    Dsp.GccResult gr=g.run(l,r,MAXLAG,FS,150,12000);
    Dsp.OnsetResult o=Dsp.onset(l,r,0.3,MAXLAG);
    double gainOffset = 20*Math.log10(1/gainR);   // what the quiet-room step would measure
    return new double[]{ gr.peak>=0.2? gr.lag/FS*1e6 : Double.NaN, o.valid? o.lag/FS*1e6 : Double.NaN, Dsp.levelDb(l,r)-gainOffset };
  }
  public static void main(String[] a){
    Direction dir=new Direction();
    double gainR=Math.pow(10,-4/20.0), noise=0.01;
    System.out.println("uncalibrated, hard right: combined="+dir.classify(clap(80,gainR,noise),0.4,-1).combined);
    for(int i=0;i<4;i++){ dir.add(Direction.LEFT, clap(-80+rnd.nextGaussian()*8,gainR,noise)); dir.add(Direction.RIGHT, clap(80+rnd.nextGaussian()*8,gainR,noise)); dir.add(Direction.MID, clap(rnd.nextGaussian()*8,gainR,noise)); }
    System.out.println(dir.report());
    int[] angles={-90,-60,-40,-20,0,20,40,60,90};
    int[][] correct=new int[4][1]; int tot=0;
    for(int ang:angles){
      StringBuilder sb=new StringBuilder(String.format("%+4d°: ",ang));
      for(int t=0;t<20;t++){
        Direction.Result r=dir.classify(clap(ang,gainR,noise),0.4,-1);
        int want = ang<=-40?0: ang>=40?2 : (Math.abs(ang)<=20?1:-9);
        if (t<6) sb.append(String.format("%+.2f ", r.combined));
        if (want>=0){ tot++; for(int k=0;k<3;k++) if(r.zone[k]==want) correct[k][0]++; if(r.combinedZone==want) correct[3][0]++; }
      }
      System.out.println(sb);
    }
    System.out.printf("accuracy over %d sounds: GCC %d, onset %d, level %d, combined %d%n",tot,correct[0][0],correct[1][0],correct[2][0],correct[3][0]);
    Direction d2=new Direction(); d2.deserialize(dir.serialize()); System.out.println("persist roundtrip ok: "+d2.report().equals(dir.report()));
  }
}
