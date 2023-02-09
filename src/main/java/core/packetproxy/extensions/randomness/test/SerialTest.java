package packetproxy.extensions.randomness.test;
// source code is from: NIST SP 800-22 rev1-a
// https://www.nist.gov/disclaimer

import org.apache.commons.math3.distribution.GammaDistribution;

public class SerialTest extends RandomnessTest {
    private int M;

    public SerialTest(int m) {
        this.M = m;
    }

    public double[] run(Integer[][] e) {
        int n = 0;
        for (int i = 0; i < e.length; i++) {
            n = e[i].length;
        }

        double[] p = new double[n];
        for (int i = 0; i < n; i++) {
            double pSim0 = psi2(M, e.length, e, i);
            double pSim1 = psi2(M - 1, e.length, e, i);
            double pSim2 = psi2(M - 2, e.length, e, i);

            double del1 = pSim0 - pSim1;
            double del2 = pSim0 - 2.0 * pSim1 + pSim2;

            GammaDistribution dist = new GammaDistribution(Math.abs(del1 / 2.0), 1);
            double p1 = 1 - dist.cumulativeProbability(Math.pow(2, M - 1));
            dist = new GammaDistribution(Math.abs(del2 / 2.0), 1);
            double p2 = 1 - dist.cumulativeProbability(Math.pow(2, M - 2));

            p[i] = Math.min(p1, p2);
        }
        return p;
    }

    private double psi2(int m, int n, Integer[][] e, int idx) {
        if (m == 0 || m == -1) {
            return 0.0;
        }

        int numOfBlocks = n;
        int powLen = (int)Math.pow(2, m + 1) - 1;
        int[] P = new int[powLen];
        for (int i = 0; i < numOfBlocks; i++) {
            int k = 1;
            for (int j = 0; j < m; j++) {
                if (e[(i + j) % n][idx] == 0) {
                    k *= 2;
                } else {
                    k = 2 * k + 1;
                }
            }
            P[k-1]++;
        }
        double sum = 0.0;
        for (int i = (int)Math.pow(2, m) - 1; i < powLen; i++) {
            sum += Math.pow(P[i], 2);
        }
        sum = (sum * Math.pow(2, m) / (double)n) - n;
        return sum;
    }
}

