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
package packetproxy;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class CoreModuleArchitectureTest {

	private final JavaClasses classes = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.withImportOption(location -> location.contains("/core/build/classes/")).importPackages("packetproxy");

	@Test
	void coreMustNotDependOnUiOrGulp() {
		noClasses().that().resideInAPackage("packetproxy..").should().dependOnClassesThat()
				.resideInAnyPackage("packetproxy.gui..", "packetproxy.gulp..", "packetproxy.cli..").check(classes);
	}

	@Test
	void coreMustNotDependOnSwingOrAwt() {
		noClasses().that().resideInAPackage("packetproxy..").should().dependOnClassesThat()
				.resideInAnyPackage("javax.swing..", "java.awt..").check(classes);
	}
}
