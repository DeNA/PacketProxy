/*
 * Copyright 2026 DeNA Co., Ltd.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ProjectDisplayNameTest {

	@Test
	public void fromFileName_defaultDatabase() {
		assertEquals("Default", ProjectDisplayName.fromFileName("resources.sqlite3"));
	}

	@Test
	public void fromFileName_namedProject() {
		assertEquals("myproject", ProjectDisplayName.fromFileName("myproject.sqlite3"));
	}

	@Test
	public void fromFileName_temporaryTimestampedProject() {
		assertEquals("Temporary", ProjectDisplayName.fromFileName("packetproxy-20260409-193537.sqlite3"));
	}

	@Test
	public void fromFileName_resourcesTemp() {
		assertEquals("Temporary", ProjectDisplayName.fromFileName("resources_temp.sqlite3"));
	}
}
