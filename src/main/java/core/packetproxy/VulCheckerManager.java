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
package packetproxy;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import javax.tools.*;
import packetproxy.vulchecker.VulChecker;

public class VulCheckerManager {
	private static VulCheckerManager instance;

	public static VulCheckerManager getInstance() throws Exception {
		if (instance == null) {
			instance = new VulCheckerManager();
		}
		return instance;
	}

	private HashMap<String, Class<VulChecker>> vulCheckerMap = new HashMap<>();
	private static final String vulCheckerPackage = "packetproxy.vulchecker";

	private VulCheckerManager() {
		try {
			loadVulCheckers();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void loadVulCheckers() throws Exception {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);

		Set<JavaFileObject.Kind> kind = Sets.newHashSet(JavaFileObject.Kind.CLASS);
		for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, vulCheckerPackage, kind, false)) {
			try {
				Path vulCheckerFilePath = Paths.get(f.getName());
				Path vulCheckerFileName = vulCheckerFilePath.getFileName();
				String vulCheckerClassPath = vulCheckerPackage + "."
						+ vulCheckerFileName.toString().replaceAll("\\.class.*$", "");
				Class klass = Class.forName(vulCheckerClassPath);
				if (VulChecker.class.isAssignableFrom(klass) && !Modifier.isAbstract(klass.getModifiers())) {
					@SuppressWarnings("unchecked")
					VulChecker vulChecker = createInstance((Class<VulChecker>) klass);
					vulCheckerMap.put(vulChecker.getName(), klass);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public String[] getVulCheckerNameList() {
		String[] names = new String[vulCheckerMap.size()];
		int i = 0;
		for (String name : vulCheckerMap.keySet()) {
			names[i++] = name;
		}
		Arrays.sort(names);
		return names;
	}

	public ImmutableMap<String, Class<VulChecker>> getAllVulCheckers() {
		return ImmutableMap.copyOf(vulCheckerMap);
	}

	private VulChecker createInstance(Class<VulChecker> klass) throws Exception {
		return klass.getConstructor().newInstance();
	}

	public VulChecker createInstance(String vulCheckerName) throws Exception {
		Class<VulChecker> klass = vulCheckerMap.get(vulCheckerName);
		return klass != null ? createInstance(klass) : null;
	}
}
