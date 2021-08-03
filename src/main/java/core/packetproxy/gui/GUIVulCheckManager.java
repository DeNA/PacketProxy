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
package packetproxy.gui;

import com.google.common.collect.ImmutableList;
import packetproxy.common.I18nString;
import packetproxy.common.Range;
import packetproxy.model.OneShotPacket;
import packetproxy.vulchecker.VulCheckPattern;
import packetproxy.vulchecker.VulChecker;
import packetproxy.vulchecker.generator.Generator;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GUIVulCheckManager {

    private OneShotPacket origPacket;
    private Range origRange;

    private ImmutableList<Generator> generators;
    private Map<String, VulCheckPattern> patternMap = new HashMap<>();
    private Map<String, Boolean> enableMap = new HashMap<>();
    private Map<String, Boolean> generatedMap = new HashMap<>();
    private VulCheckPattern emptyPattern;

    public GUIVulCheckManager(VulChecker vulChecker, OneShotPacket packet, Range range) throws Exception {
        this.generators = vulChecker.getGenerators();
        this.origPacket = packet;
        this.origRange = range;
        this.emptyPattern = createEmptyPattern(packet);
        for (Generator g : this.generators) {
            generatedMap.put(g.getName(), false);
        }
        generateOnStart();
    }

    private VulCheckPattern createEmptyPattern(OneShotPacket packet) throws Exception{
        OneShotPacket emptyPacket = (OneShotPacket) packet.clone();
        emptyPacket.setData(I18nString.get("Activate the checkbox to generate a pattern").getBytes(StandardCharsets.UTF_8));
        emptyPacket.setEncoder("Sample");
        emptyPacket.setAlpn("");
        return new VulCheckPattern("empty", emptyPacket, null);
    }

    private void generateOnStart() throws Exception {
        for (Generator generator : generators) {
            if (generator.generateOnStart()) {
                generateFromOrig(generator);
            } else {
                noGenerate(generator);
            }
        }
    }

    public VulCheckPattern generate(String name) throws Exception {
        Generator generator = findGenerator(name);
        return generateFromOrig(generator);
    }

    public void saveVulCheckPattern(String name, VulCheckPattern pattern) {
        if (enableMap.get(name)) {
            patternMap.put(name, pattern);
        }
    }

    public ImmutableList<Generator> getGenerators() {
        return generators;
    }

    public ImmutableList<VulCheckPattern> getAllVulCheckPattern() {
        var builder = ImmutableList.<VulCheckPattern>builder();
        for (Generator generator : generators) {
            builder.add(findVulCheckPattern(generator.getName()));
        }
        return builder.build();
    }

    public ImmutableList<VulCheckPattern> getAllEnabledVulCheckPattern() {
        var builder = ImmutableList.<VulCheckPattern>builder();
        for (Generator generator : generators) {
            if (enableMap.get(generator.getName())) {
                builder.add(findVulCheckPattern(generator.getName()));
            }
        }
        return builder.build();
    }

    public VulCheckPattern findVulCheckPattern(String name) {
        return isEnabled(name) ? patternMap.get(name) : emptyPattern;
    }

    public boolean isEnabled(String name) {
        return enableMap.get(name);
    }

    public void setEnabled(String name, boolean enabled) throws Exception {
        if (enabled) {
            enableMap.put(name, true);
            if (!generatedMap.get(name)) { // まだ一度もgenerateされていないケース
                generate(name);
            }
        } else {
            enableMap.put(name, false);
        }
    }

    private Generator findGenerator(String name) {
        return generators.stream().filter(g -> g.getName().equals(name)).findFirst().orElseThrow();
    }

    private VulCheckPattern generateFromOrig(Generator generator) throws Exception {
        OneShotPacket clonedPacket = (OneShotPacket) origPacket.clone();
        String userSelectedStr = new String(clonedPacket.getData(origRange), StandardCharsets.UTF_8);
        String generatedStr = generator.generate(userSelectedStr);
        clonedPacket.replaceData(origRange, generatedStr.getBytes(StandardCharsets.UTF_8));
        Range newRange = Range.of(origRange.getPositionStart(), origRange.getPositionStart() + generatedStr.length());
        VulCheckPattern newPattern = new VulCheckPattern(generator.getName(), clonedPacket, newRange);
        patternMap.put(generator.getName(), newPattern);
        enableMap.put(generator.getName(), true);
        generatedMap.put(generator.getName(), true);
        return newPattern;
    }

    private VulCheckPattern noGenerate(Generator generator) throws Exception {
        OneShotPacket clonedPacket = (OneShotPacket) origPacket.clone();
        VulCheckPattern pattern = new VulCheckPattern(generator.getName(), clonedPacket, origRange);
        patternMap.put(generator.getName(), pattern);
        enableMap.put(generator.getName(), false);
        return pattern;
    }
}