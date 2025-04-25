/*
 * Copyright 2021 DeNA Co., Ltd.
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
package packetproxy.vulchecker;

import com.google.common.collect.ImmutableList;
import packetproxy.vulchecker.generator.*;

public class NumberVulChecker extends VulChecker {
    @Override
    public String getName() {
        return "Number";
    }

    @Override
    public ImmutableList<Generator> getGenerators() {
        return ImmutableList.<Generator>builder()
                .add(new NegativeNumberGenerator())
                .add(new ZeroGenerator())
                .add(new DecimalsGenerator())
                .add(new IntegerOverflowMinusOneGenerator())
                .add(new IntegerOverflowGenerator())
                .add(new IntegerOverflowPlusOneGenerator())
                .add(new LongOverflowMinusOneGenerator())
                .add(new LongOverflowGenerator())
                .add(new LongOverflowPlusOneGenerator())
                .build();
    }
}