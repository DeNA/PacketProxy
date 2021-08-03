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
package packetproxy.vulchecker.generator;

import packetproxy.common.I18nString;

public class NegativeNumberGenerator extends Generator {
    @Override
    public String getName() {
        return I18nString.get("Negative Number: -1");
    }

    @Override
    public boolean generateOnStart() {
        return true;
    }

    @Override
    public String generate(String inputData) {
        return "-1";
    }
}