/*
 * Copyright 2019 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.extensions.randomness.test;
import static packetproxy.util.Logging.errWithStackTrace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.DoubleStream;
import javax.swing.JComboBox;

public class RandomnessTestManager {

	private static RandomnessTestManager instance;
	private final Map<String, RandomnessTest> testMap = new HashMap<>();
	private final double[] x;

	public static RandomnessTestManager getInstance() {
		if (instance == null) {

			instance = new RandomnessTestManager();
		}
		return instance;
	}

	RandomnessTestManager() {
		x = DoubleStream.iterate(-3.0, v -> (v <= 0.0), v -> (v + 0.1)).toArray();
		for (int i = 0; i < x.length; i++) {

			x[i] = Math.pow(10.0, x[i]);
		}
	}

	public JComboBox<String> createTestList() {
		JComboBox<String> testList = new JComboBox<>();

		testList.addItem("Frequency");
		testMap.put("Frequency", new FrequencyTest());

		testList.addItem("Runs");
		testMap.put("Runs", new RunsTest());

		testList.addItem("LongestRunOfOne");
		testMap.put("LongestRunOfOne", new LongestRunOfOneTest());

		testList.addItem("MatrixRank");
		testMap.put("MatrixRank", new RankTest());

		testList.addItem("LinearComplexity with 8bit");
		testMap.put("LinearComplexity with 8bit", new LinearComplexityTest(8));

		testList.addItem("LinearComplexity with 32bit");
		testMap.put("LinearComplexity with 32bit", new LinearComplexityTest(32));

		testList.addItem("Serial with 8bit");
		testMap.put("Serial with 8bit", new SerialTest(8));

		testList.addItem("ApproximateEntropy with 8bit");
		testMap.put("ApproximateEntropy with 8bit", new ApproximateEntropyTest(8));

		testList.addItem("CUsUM with forward");
		testMap.put("CUsUM with forward", new CUsUMTest(0));

		testList.addItem("CUsUM with backward");
		testMap.put("CUsUM with backward", new CUsUMTest(1));

		return testList;
	}

	// return list of (x, y)
	public double[][] analyze(String key, ArrayList<Integer[]> preprocessed) {
		double[][] res = new double[x.length][2];
		try {

			RandomnessTest test = testMap.get(key);

			double[] pValues = test.run(preprocessed.toArray(new Integer[0][0]));

			for (int i = 0; i < res.length; i++) {

				int cnt = 0;
				for (double pValue : pValues) {

					if (x[i] > pValue)
						cnt++;
				}
				res[i][0] = x[i];
				res[i][1] = cnt;
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		return res;
	}
}
