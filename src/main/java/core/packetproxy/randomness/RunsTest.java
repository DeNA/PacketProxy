package packetproxy.randomness;

import org.apache.commons.math3.special.Erf;

public class RunsTest extends RandomnessTest {
    public double[] run(Integer[][] e) {
        int n = 0;
        for (int i = 0; i < e.length; i++) {
            n = e[i].length;
        }
        double[] p = new double[n];
        for (int i = 0; i < n; i++) {
            double pi = 0;
            for (int j = 0; j < e.length; j++) {
                pi += e[j][i];
            }
            pi /= e.length;
            int v = 1;
            for (int j = 1; j < e.length; j++) {
                if (e[j][i] != e[j-1][i]) {
                    v++;
                }
            }
            double erfcArgs = Math.abs(v - 2.0 * e.length * pi * (1.0 - pi)) / (2.0 * pi * (1.0 - pi) * Math.sqrt(2.0 * e.length));
            p[i] = Erf.erfc(erfcArgs);
        }
        return p;
    } 
}
