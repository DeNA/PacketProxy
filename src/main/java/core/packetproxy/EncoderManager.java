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

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.apache.commons.io.FilenameUtils;
import packetproxy.encode.Encoder;

public class EncoderManager
{
	private static EncoderManager incetance;
	
	public static EncoderManager getInstance() throws Exception {
		if (incetance == null) {
			incetance = new EncoderManager();
		}
		return incetance;
	}

	final static private String DEFAULT_PLUGIN_DIR = System.getProperty("user.home")+"/.packetproxy/plugins";
	private HashMap<String,Class<Encoder>> module_list;
	private static final String encode_package = "packetproxy.encode";
	private static final Class<Encoder> encode_class = packetproxy.encode.Encoder.class;
	
	// リフレクションを利用して自動でモジュールをロードするようになったので、クラス名を追記する必要はありません。

	private EncoderManager() {
		try {
			loadModules();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void loadModules() throws Exception {
		module_list = new HashMap<String,Class<Encoder>>();
		loadModulesFromJar(module_list);
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);

		Set<JavaFileObject.Kind> kind = Sets.newHashSet(JavaFileObject.Kind.CLASS);
		for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, encode_package, kind, false)) {
			Path encode_file_path = Paths.get(f.getName());
			Path encode_file_name = encode_file_path.getFileName();
			String encode_class_path = encode_package + "." + encode_file_name.toString().replaceAll("\\.class.*$", "");
			Class klass = Class.forName(encode_class_path);
			if(encode_class.isAssignableFrom(klass) && !Modifier.isAbstract(klass.getModifiers())){
                @SuppressWarnings("unchecked")
				Encoder encoder = createInstance((Class<Encoder>) klass);
				module_list.put(encoder.getName(), klass);
			}
		}
	}

	private void loadModulesFromJar(HashMap<String,Class<Encoder>> module_list) throws Exception
	{
		File[] files = new File(DEFAULT_PLUGIN_DIR).listFiles();
		if(null==files){
			return;
		}
		for(File file:files){
			if(!file.isFile() || !"jar".equals(FilenameUtils.getExtension(file.getName()))){
				continue;
			}
			URLClassLoader urlClassLoader = URLClassLoader.newInstance(new URL[]{file.toURI().toURL()});
			JarFile jarFile = new JarFile(file.getPath());
			for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); )
			{
				//Jar filter
				JarEntry jarEntry = entries.nextElement();
				if (jarEntry.isDirectory()) continue;

				// class filter
				String fileName = jarEntry.getName();
				if (!fileName.endsWith(".class")) continue;

				Class<?> klass;
				try {
					//jar内のクラスファイルの階層がパッケージに準拠している必要がある
					//例: packetproxy.plugin.EncodeHTTPSample
					//-> packetproxy/plugin/EncodeHTTPSample.class
					String encode_class_path = fileName.replaceAll("\\.class.*$", "").replaceAll("/",".");
					klass = urlClassLoader.loadClass(encode_class_path);
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}

				// Encode Classを継承しているか
				if (!encode_class.isAssignableFrom(klass)) continue;
				if (Modifier.isAbstract(klass.getModifiers())) continue;

				@SuppressWarnings("unchecked")
				Encoder encoder = createInstance((Class<Encoder>) klass);
				module_list.put(encoder.getName(),(Class<Encoder>)klass);
			}
		}
	}

	public String[] getEncoderNameList() {
		String[] names = new String[module_list.size()];
		int i = 0;
		for (String name : module_list.keySet()) {
				names[i++] = name;
		}
		Arrays.sort(names);
		return names;
	}
	private Encoder createInstance(Class<Encoder> klass) throws Exception
	{
		return klass.newInstance();
	}
	public Encoder createInstance(String name) throws Exception
	{
		Class<Encoder> klass = module_list.get(name);
		if (klass == null) {
			return null;
		}
		return createInstance(klass);
	}
}
