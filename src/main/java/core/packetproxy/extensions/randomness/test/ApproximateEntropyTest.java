package packetproxy.extensions.randomness.test;

// source code is from: NIST SP 800-22 rev1-a
// https://www.nist.gov/disclaimer

import org.apache.commons.math3.distribution.GammaDistribution;

public class ApproximateEntropyTest extends RandomnessTest {
    private int M;

    public ApproximateEntropyTest(int m) {
        this.M = m;
    }

    public double[] run(Integer[][] e) {
        GammaDistribution dist = new GammaDistribution(Math.pow(2, M - 1), 1);

        int n = 0;
        for (int i = 0; i < e.length; i++) {
            n = e[i].length;
        }

        double[] p = new double[n];

        int seqLength = e.length;
        double[] ApEn = new double[2];
        int r = 0;
        for (int i = 0; i < n; i++) {
            for (int blockSize = M; blockSize <= M + 1; blockSize++) {
                if (blockSize == 0) {
                    ApEn[0] = 0.00;
                    r++;
                } else {
                    double numOfBlocks = seqLength;
                    int powLen = (int)Math.pow(2, blockSize + 1) - 1;
                    int[] P = new int[powLen];

                    for (int j = 0; j < numOfBlocks; j++) {
                        int k = 1;
                        for (int l = 0; l < blockSize; l++) {
                            k <<= 1;
                            if (e[(j + l) % seqLength][i] == 1) {
                                k++;
                            }
                        }
                        P[k-1]++;
                    }

                    double sum = 0.0;
                    int idx = (int)Math.pow(2, blockSize) - 1;
                    for (int j = 0; j < Math.pow(2, blockSize); j++,idx++) {
                        if (P[idx] > 0) {
                            sum += P[idx] * Math.log(P[idx] / numOfBlocks);
                        }
                    }
                    sum /= numOfBlocks;
                    ApEn[r++] = sum;
                }
            }
            double apen = ApEn[0] - ApEn[1];
            double chi2 = 2.0 * seqLength * (Math.log(2) - apen);
            p[i] = 1 - dist.cumulativeProbability(chi2 / 2.0);
        }

        return p;
    }
}