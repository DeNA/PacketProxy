package packetproxy.extensions.randomness.test;

// source code is from: NIST SP 800-22 rev1-a

// https://www.nist.gov/disclaimer

import static packetproxy.util.Logging.log;

import org.apache.commons.math3.distribution.GammaDistribution;

public class LinearComplexityTest extends RandomnessTest {

	private int M;
	private static double[] pi = {0.010417, 0.03125, 0.12500, 0.50000, 0.25000, 0.06250, 0.020833};
	private static int K = 6;

	public LinearComplexityTest(int m) {
		this.M = m;
	}

	public double[] run(Integer[][] e) {
		int N = e.length / M;
		if (N == 0) {

			log("[Warn] bit length is not suitable for Rank test. Please collect more tokens.");
			return new double[e.length > 0 ? e[0].length : 0];
		}

		int n = 0;
		for (int i = 0; i < e.length; i++) {

			n = e[i].length;
		}

		double[] p = new double[n];
		for (int i = 0; i < n; i++) {

			double[] nu = new double[K + 1];
			for (int j = 0; j < N; j++) {

				int[] B = new int[M], C = new int[M], P = new int[M], T = new int[M];
				int L = 0, m = -1, d = 0;
				C[0] = 1;
				B[0] = 1;
				int tmpN = 0;
				while (tmpN < M) {

					d = e[j * M + tmpN][i];
					for (int k = 0; k <= L; k++) {

						d += C[k] * e[j * M + tmpN - k][i];
					}

					d &= 1;
					if (d == 1) {

						for (int k = 0; k < M; k++) {

							T[k] = C[k];
							P[k] = 0;
						}
						for (int k = 0; k < M; k++) {

							if (B[k] == 1) {

								P[k + tmpN - m] = 1;
							}
						}
						for (int k = 0; k < M; k++) {

							C[k] = (C[k] + P[k]) & 1;
						}
						if (L <= tmpN / 2) {

							L = tmpN + 1 - L;
							m = tmpN;
							for (int k = 0; k < M; k++) {

								B[k] = T[k];
							}
						}
					}
					tmpN++;
				}

				int sign = (M % 2 == 1 ? -1 : 1);
				double mean = M / 2.0 + (9.0 + sign) / 36.0 - 1.0 / Math.pow(2, M) * (M / 3.0 + 2.0 / 9.0);

				sign = (M % 2 == 0 ? 1 : -1);
				double tmpT = sign * (L - mean) + 2.0 / 9.0;

				if (tmpT <= -2.5) {

					nu[0]++;
				} else if (tmpT <= -1.5) {

					nu[1]++;
				} else if (tmpT <= -0.5) {

					nu[2]++;
				} else if (tmpT <= 0.5) {

					nu[3]++;
				} else if (tmpT <= 1.5) {

					nu[4]++;
				} else if (tmpT <= 2.5) {

					nu[5]++;
				} else {

					nu[6]++;
				}
			}

			double chi2 = 0.00;
			for (int j = 0; j <= K; j++) {

				chi2 += Math.pow(nu[j] - N * pi[j], 2) / (N * pi[j]);
			}
			GammaDistribution dist = new GammaDistribution(K / 2.0, 1);
			p[i] = 1 - dist.cumulativeProbability(chi2 / 2.0);
		}

		return p;
	}
}
