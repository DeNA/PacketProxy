/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy.quic.service.frame;

import com.google.common.collect.Sets;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.*;
import lombok.SneakyThrows;
import packetproxy.quic.value.frame.Frame;

public class FrameParser {

	private static final String framePackage = "packetproxy.quic.value.frame";
	private static final Class<Frame> frameClass = Frame.class;
	private static Map<Byte, Class<Frame>> frameMap;

	@SneakyThrows
	private static void createFrameMap() {
		frameMap = new HashMap<>();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);

		Set<JavaFileObject.Kind> kind = Sets.newHashSet(JavaFileObject.Kind.CLASS);
		for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, framePackage, kind, true)) {

			Path encode_file_path = Paths.get(f.getName());
			String encode_class_path = encode_file_path.toString().replaceAll("/", ".")
					.replaceFirst("^.*" + framePackage, framePackage).replaceAll("\\.class.*$", "");
			Class<?> klass = Class.forName(encode_class_path);
			if (frameClass.isAssignableFrom(klass) && !Modifier.isAbstract(klass.getModifiers())) {

				List<Byte> types = (List<Byte>) klass.getMethod("supportedTypes").invoke(null);
				types.forEach(type -> frameMap.put(type, (Class<Frame>) klass));
			}
		}
	}

	private static Frame createInstance(Class<Frame> klass, ByteBuffer buffer) throws Exception {
		return (Frame) klass.getMethod("parse", ByteBuffer.class).invoke(null, buffer);
	}

	public static Frame create(ByteBuffer buffer) throws Exception {
		int saved = buffer.position();
		byte type = buffer.get();
		buffer.position(saved);

		if (frameMap == null) {

			createFrameMap();
		}

		Class<Frame> klass = frameMap.get(type);
		if (klass == null) {

			throw new Exception(String.format("Error: unknown frame type: %x", type));
		} else {

			return createInstance(klass, buffer);
		}
	}
}
