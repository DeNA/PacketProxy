package packetproxy.extensions.randomness.test;
// source code is from: NIST SP 800-22 rev1-a

// https://www.nist.gov/disclaimer

import org.apache.commons.math3.distribution.GammaDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;
import packetproxy.util.PacketProxyUtility;

public class RankTest extends RandomnessTest {
	public double[] run(Integer[][] e) {
		int N = e.length / (32 * 32);
		if (N == 0) {
			PacketProxyUtility.getInstance()
					.packetProxyLog("[Warn] bit length is not suitable for Rank test. Please collect more tokens.");
			return new double[e.length > 0 ? e[0].length : 0];
		}

		int n = 0;
		for (int i = 0; i < e.length; i++) {
			n = e[i].length;
		}

		double[] p = new double[n];
		for (int i = 0; i < n; i++) {
			SimpleMatrix mat = new SimpleMatrix(32, 32);

			int r = 32;
			double product = 1;
			for (int j = 0; j < r; j++) {
				product *= (1.0 - Math.pow(2, j - 32)) * (1 - Math.pow(2, j - 32)) / (1 - Math.pow(2, j - r));
			}
			double p32 = Math.pow(2, r * (32 + 32 - r) - 32 * 32) * product;

			r = 31;
			product = 1;
			for (int j = 0; j < r; j++) {
				product *= (1.0 - Math.pow(2, j - 32)) * (1 - Math.pow(2, j - 32)) / (1 - Math.pow(2, j - r));
			}
			double p31 = Math.pow(2, r * (32 + 32 - r) - 32 * 32) * product;

			double p30 = 1 - (p32 + p31);

			double F32 = 0, F31 = 0;
			for (int j = 0; j < N; j++) {
				for (int k = 0; k < 32; k++) {
					for (int l = 0; l < 32; l++) {
						mat.set(k, l, e[j * (32 * 32) + k * 32 + l][i]);
					}
				}
				SimpleSVD<SimpleMatrix> svd = mat.svd();
				switch (svd.rank()) {
					case 32 :
						F32++;
						break;
					case 31 :
						F31++;
				}
			}
			double F30 = N - (F32 + F31);

			double chiSquared = Math.pow(F32 - N * p32, 2) / (N * p32) + Math.pow(F31 - N * p31, 2) / (N * p31)
					+ Math.pow(F30 - N * p30, 2) / (N * p30);

			GammaDistribution dist = new GammaDistribution(N / 2.0, 1);
			p[i] = 1 - dist.cumulativeProbability(chiSquared / 2.0);
		}
		return p;
	}
}
