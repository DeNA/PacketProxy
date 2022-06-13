package packetproxy.randomness;

abstract public class RandomnessTest {
    // e[i][j] should be in {0, 1}
    // return: p-value of each bit
    abstract public double[] run(Integer[][] e);
}
