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
package packetproxy.model;

import static java.util.Comparator.comparing;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import packetproxy.model.CAs.CA;

public class CAFactory
{
	//public static void main(String[] args) {
	//	CAFactory.queryAll().stream().forEach(ca -> System.out.println(ca));
	//	System.out.println("----");
	//	CAFactory.find("PacketProxy CA").ifPresent(ca -> System.out.println(ca));
	//	CAFactory.find("PacketProxy CA2").ifPresent(ca -> System.out.println(ca));
	//	System.out.println("----");
	//	CA a = CAFactory.find("PacketProxy CA").get();
	//	CA b = CAFactory.find("PacketProxy CA").get();
	//	System.out.println(a == b);
	//	String c = CAFactory.find("PacketProxy CA2").map(s -> { System.out.println(s); return "ok";}).orElse("Error");
	//	System.out.println(c);
	//}

	private static final Class<CA> ca_class = packetproxy.model.CAs.CA.class;
	private static final String ca_package = "packetproxy.model.CAs";
	private static final List<CA> ca_list = new ArrayList<>();
	
	static {
		try {
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);
			Set<JavaFileObject.Kind> kind = new HashSet<JavaFileObject.Kind>(){
				private static final long serialVersionUID = 1L;
				{
					add(JavaFileObject.Kind.CLASS);
				}
			};
			for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, ca_package, kind, false)) {
				Path file_path = Paths.get(f.getName());
				Path file_name = file_path.getFileName();
				String ca_class_path = ca_package + "." + file_name.toString().replaceAll("\\.class.*$", "");
				Class<CA> klass = (Class<CA>)Class.forName(ca_class_path);
				if(ca_class.isAssignableFrom(klass) && !ca_class.equals(klass)){
					CA ca = klass.newInstance();
					ca_list.add(ca);
				}
				ca_list.stream().sorted(comparing(CA::getName));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static Optional<CA> findByUTF8Name(String name) {
		return ca_list.stream().filter(ca -> ca.getUTF8Name().equalsIgnoreCase(name)).findFirst();
	}
	public static Optional<CA> find(String name) {
		return ca_list.stream().filter(ca -> ca.getName().equalsIgnoreCase(name)).findFirst();
	}
	public static List<CA> queryExportable() {
		return ca_list.stream().filter(ca -> ca.getCACertificate() != null).collect(Collectors.toList());
	}
	public static List<CA> queryAll() {
		return ca_list;
	}
}
