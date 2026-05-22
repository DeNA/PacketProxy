package packetproxy.extensions.randomness.test;

// source code is from: NIST SP 800-22 rev1-a
// https://www.nist.gov/disclaimer

import org.apache.commons.math3.special.Erf;

public class FrequencyTest extends RandomnessTest {

	public double[] run(Integer[][] e) {
		int n = 0;
		for (int i = 0; i < e.length; i++) {

			n = e[i].length;
			for (int j = 0; j < n; j++) {

				e[i][j] = 2 * e[i][j] - 1;
			}
		}
		double[] p = new double[n];
		for (int i = 0; i < n; i++) {

			int s = 0;
			for (int j = 0; j < e.length; j++) {

				s += e[j][i];
			}
			double z = Math.abs(s) / Math.sqrt(e.length);
			p[i] = 1.0 - Erf.erf(z / Math.sqrt(2));
		}
		return p;
	}
}
