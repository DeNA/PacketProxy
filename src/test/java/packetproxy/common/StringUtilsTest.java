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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilsTest {
    @Test
    public void testCountChar() {
        int cnt = StringUtils.countChar("Hello", 'l', 0, "Hello".length());
        assertEquals(2, cnt);
        cnt = StringUtils.countChar("Hello", 'l', 3, "Hello".length());
        assertEquals(1, cnt);
        cnt = StringUtils.countChar("Hello", 'l', 0, 3);
        assertEquals(1, cnt);
        cnt = StringUtils.countChar("Hello", 'l', 3, 3);
        assertEquals(0, cnt);
        cnt = StringUtils.countChar("Hello", 'l', 4, 3);
        assertEquals(0, cnt);
    }
}
