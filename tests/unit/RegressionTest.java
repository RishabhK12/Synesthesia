import org.junit.Test;

public class RegressionTest {
    @Test public void existingDsp() { DspTest.main(new String[0]); }
    @Test public void existingDirections() { DirTest.main(new String[0]); }
    @Test public void existingDiagnostics() throws Exception { DiagnosticTest.main(new String[0]); }
    @Test public void existingCompass() { CompassTest.main(new String[0]); }
}
