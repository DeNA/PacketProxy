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
package packetproxy.common;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StringSearchBenchmark {
	private byte[] text;
	private byte[] pattern;

	@Setup(Level.Iteration)
	public void beforeIteration() {
		Random rng = new Random();

		text = new byte[4 * 1024];

		rng.nextBytes(text);
		int x = rng.nextInt(4 * 1024 - 13);
		pattern = Arrays.copyOfRange(text, x, x + 12);
	}

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	public void boyerMoore() {
		final BoyerMoore alg = new BoyerMoore(pattern);
		alg.searchIn(text);
	}

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	public void bruteforceSearch() {
		Utils.indexOf(text, 0, text.length, pattern);
	}
}
