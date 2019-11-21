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
package packetproxy.http2.frames;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.eclipse.jetty.http2.hpack.HpackDecoder;

import com.google.common.collect.Sets;

import packetproxy.http2.frames.Frame.Type;

public class FrameFactory {
	
	static private Map<Type, Class<?>> frameList;
	
	static {
		try {
			frameList = new HashMap<Type,Class<?>>();

			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);

			Set<JavaFileObject.Kind> kind = Sets.newHashSet(JavaFileObject.Kind.CLASS);
			for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, "packetproxy.http2.frames", kind, false)) {
				Path frameFilePath = Paths.get(f.getName());
				Path frameFileName = frameFilePath.getFileName();
				String frameClassPath = "packetproxy.http2.frames" + "." + frameFileName.toString().replaceAll("\\.class.*$", "");
				Class<?> klass = Class.forName(frameClassPath);
				if (packetproxy.http2.frames.Frame.class.isAssignableFrom(klass)) {
					@SuppressWarnings("unchecked")
					Class<Frame> frameKlass = (Class<Frame>)klass;
					if (frameKlass.getName().equals("packetproxy.http2.frames.Frame")) {
						continue;
					}
					if (frameList.containsValue(frameKlass) == false) {
						Frame.Type type = (Type)frameKlass.getDeclaredField("TYPE").get(Frame.Type.Unassigned);
						frameList.put(type, frameKlass);
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static public Frame create(byte[] data, HpackDecoder decoder) throws Exception {
		Frame f = new Frame(data);
		Class<?> frameClass = frameList.get(f.getType());
		if (frameClass != null) {
			if (frameClass == HeadersFrame.class) {
				return (Frame) frameClass.getConstructor(Frame.class, HpackDecoder.class).newInstance(f, decoder);
			} else {
				return (Frame) frameClass.getConstructor(Frame.class).newInstance(f);
			}
		}
		return f;
	}
	
	static public void debug() {
		System.out.println(frameList.toString());
	}

	private FrameFactory() {
	}

}
