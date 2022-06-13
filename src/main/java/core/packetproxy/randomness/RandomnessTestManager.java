package packetproxy.randomness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.DoubleStream;

import javax.swing.JComboBox;

public class RandomnessTestManager {
    private static RandomnessTestManager instance;
    private Map<String, RandomnessTest> testMap = new HashMap<String, RandomnessTest>();
    private double[] x;

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
                for (int j = 0; j < pValues.length; j++) {
                    if (x[i] > pValues[j]) cnt++;
                }
                res[i][0] = x[i];
                res[i][1] = cnt;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }
}
