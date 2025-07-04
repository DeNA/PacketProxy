package packetproxy.extensions.randomness.test;
// source code is from: NIST SP 800-22 rev1-a

// https://www.nist.gov/disclaimer

import org.apache.commons.math3.distribution.GammaDistribution;

import packetproxy.util.PacketProxyUtility;

public class LongestRunOfOneTest extends RandomnessTest {
    public double[] run(Integer[][] e) {
        int n = e.length;
        if (n < 128) {
            PacketProxyUtility.getInstance().packetProxyLog(
                    "[Warn] bit length is not suitable for LongestRunOfOne test. Please collect more tokens.");
            return new double[e.length > 0 ? e[0].length : 0];
        }
        int K = 0, M = 0;
        int[] V;
        double[] pi;
        if (n < 6272) {
            K = 3;
            M = 8;
            V = new int[] { 1, 2, 3, 4 };
            pi = new double[] { 0.21484375, 0.3671875, 0.23046875, 0.1875 };
        } else if (n < 750000) {
            K = 5;
            M = 128;
            V = new int[] { 4, 5, 6, 7, 8, 9 };
            pi = new double[] { 0.1174035788, 0.242955959, 0.249363483, 0.17517706, 0.102701071, 0.112398847 };
        } else {
            K = 6;
            M = 10000;
            V = new int[] { 10, 11, 12, 13, 14, 15, 16 };
            pi = new double[] { 0.0882, 0.2092, 0.2483, 0.1933, 0.1208, 0.0675, 0.0727 };
        }
        GammaDistribution dist = new GammaDistribution(K / 2.0, 1);
        int[] nu = new int[K + 1];

        int N = e.length / M;
        for (int i = 0; i < e.length; i++) {
            n = e[i].length;
        }
        double[] p = new double[n];

        for (int k = 0; k < n; k++) {
            for (int i = 0; i < N; i++) {
                int Vb = 0;
                int r = 0;
                for (int j = 0; j < M; j++) {
                    int idx = i * M + j;
                    if (e[idx][k] == 1) {
                        Vb = Math.max(Vb, ++r);
                    } else {
                        r = 0;
                    }
                }

                if (Vb < V[0]) {
                    nu[0]++;
                }
                for (int j = 0; j <= K; j++) {
                    if (Vb == V[j]) {
                        nu[j]++;
                    }
                }
                if (Vb > V[K]) {
                    nu[K]++;
                }
            }

            double chi2 = 0;
            for (int i = 0; i <= K; i++) {
                chi2 += ((nu[i] - N * pi[i]) * (nu[i] - N * pi[i])) / (N * pi[i]);
            }
            p[k] = 1 - dist.cumulativeProbability(chi2 / 2.0);
        }
        return p;
    }
}
