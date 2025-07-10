package packetproxy.extensions.randomness.test;

// source code is from: NIST SP 800-22 rev1-a
// https://www.nist.gov/disclaimer

import org.apache.commons.math3.distribution.NormalDistribution;

public class CUsUMTest extends RandomnessTest {

	private int mode;
	private NormalDistribution dist = new NormalDistribution();

	public CUsUMTest(int mode) {
		this.mode = mode;
	}

	public double[] run(Integer[][] e) {
		int n = 0;
		for (int i = 0; i < e.length; i++) {

			n = e[i].length;
		}
		double[] p = new double[n];

		for (int j = 0; j < n; j++) {

			int s = 0;
			int sup = 0;
			int inf = 0;
			int z = 0, zInv = 0;
			for (int i = 0; i < e.length; i++) {

				if (e[i][j] == 1) {

					s++;
				} else {

					s--;
				}
				sup = Math.max(sup, s);
				inf = Math.min(inf, s);
				z = Math.max(sup, -inf);
				zInv = Math.max(sup - s, s - inf);
			}

			// foreward
			double sum1 = 0, sum2 = 0;
			if (mode == 0) {

				for (int k = (-e.length / z + 1) / 4; k <= (n / z - 1) / 4; k++) {

					sum1 += dist.cumulativeProbability(((4 * k + 1) * z) / Math.sqrt(n));
					sum1 -= dist.cumulativeProbability(((4 * k - 1) * z) / Math.sqrt(n));
				}
				for (int k = (-e.length / z - 3) / 4; k <= (n / z - 1) / 4; k++) {

					sum2 += dist.cumulativeProbability(((4 * k + 3) * z) / Math.sqrt(n));
					sum2 -= dist.cumulativeProbability(((4 * k + 1) * z) / Math.sqrt(n));
				}
			}
			// backward
			else {

				for (int k = (-e.length / zInv + 1) / 4; k <= (n / zInv - 1) / 4; k++) {

					sum1 += dist.cumulativeProbability(((4 * k + 1) * zInv) / Math.sqrt(n));
					sum1 -= dist.cumulativeProbability(((4 * k - 1) * zInv) / Math.sqrt(n));
				}
				for (int k = (-e.length / zInv - 3) / 4; k <= (n / zInv - 1) / 4; k++) {

					sum2 += dist.cumulativeProbability(((4 * k + 3) * zInv) / Math.sqrt(n));
					sum2 -= dist.cumulativeProbability(((4 * k + 1) * zInv) / Math.sqrt(n));
				}
			}

			p[j] = 1.0 - sum1 + sum2;
		}
		return p;
	}
}
