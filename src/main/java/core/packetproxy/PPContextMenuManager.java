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
package packetproxy;

import com.google.common.collect.Sets;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import packetproxy.ppcontextmenu.PPContextMenu;

public class PPContextMenuManager {
	private static PPContextMenuManager instance;
	private List<PPContextMenu> module_list;
	private static final String item_package = "packetproxy.ppcontextmenu";
	private static final Class<PPContextMenu> item_class = packetproxy.ppcontextmenu.PPContextMenu.class;
	
	public static PPContextMenuManager getInstance() throws Exception {
		if (instance == null) {
			instance = new PPContextMenuManager();
		}
		return instance;
	}
	
	public List<PPContextMenu> getMenuItemList(){
		return module_list;
	}

	private PPContextMenuManager() {
		try {
			loadItems();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void loadItems() throws Exception {
		module_list = new ArrayList<PPContextMenu>();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);

		Set<JavaFileObject.Kind> kind = Sets.newHashSet(JavaFileObject.Kind.CLASS);
		for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, item_package, kind, false)) {
			Path item_file_path = Paths.get(f.getName());
			Path item_file_name = item_file_path.getFileName();
			String item_class_path = item_package + "." + item_file_name.toString().replaceAll("\\.class.*$", "");

            @SuppressWarnings("rawtypes")
			Class klass = Class.forName(item_class_path);
			if(item_class.isAssignableFrom(klass) && !Modifier.isAbstract(klass.getModifiers())){
                @SuppressWarnings("unchecked")
				PPContextMenu ppcm = createInstance(klass);	
				module_list.add(ppcm);
			}
		}
	}

	private PPContextMenu createInstance(Class<PPContextMenu> klass) throws Exception
	{
		return klass.newInstance();
	}
}
