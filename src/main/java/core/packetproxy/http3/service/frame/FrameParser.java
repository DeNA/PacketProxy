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

package packetproxy.http3.service.frame;

import com.google.common.collect.Sets;
import packetproxy.http3.value.frame.Frame;
import packetproxy.http3.value.frame.Frames;
import packetproxy.http3.value.frame.GreaseFrame;
import packetproxy.quic.value.VariableLengthInteger;

import javax.tools.*;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FrameParser {

    private static final String framePackage = "packetproxy.http3.value.frame";
    private static final Class<Frame> frameClass = Frame.class;
    private static final Map<Long,Class<Frame>> frameMap = new HashMap<>();

    static {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);

            Set<JavaFileObject.Kind> kind = Sets.newHashSet(JavaFileObject.Kind.CLASS);
            for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, framePackage, kind, true)) {
                Path encode_file_path = Paths.get(f.getName());
                if (encode_file_path.toString().contains("test")) {
                    continue;
                }
                String encode_class_path = encode_file_path.toString()
                        .replaceAll("/", ".")
                        .replaceFirst("^.*" + framePackage, framePackage)
                        .replaceAll("\\.class.*$", "");
                Class<?> klass = Class.forName(encode_class_path);
                if (frameClass.isAssignableFrom(klass) && !Modifier.isAbstract(klass.getModifiers()) && !frameClass.isNestmateOf(klass)) {
                    List<Long> types = (List<Long>) klass.getMethod("supportedTypes").invoke(null);
                    types.forEach(type -> frameMap.put(type, (Class<Frame>) klass));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static public Frames parse(byte[] bytes) throws Exception {
        return parse(ByteBuffer.wrap(bytes));
    }

    static public Frames parse(ByteBuffer buffer) throws Exception {
        Frames frames = Frames.emptyList();
        while (buffer.hasRemaining()) {
            long type = getTypeWithoutIncrement(buffer);
            Class<Frame> klass = frameMap.get(type);
            if (klass == null) {
                frames.add(GreaseFrame.parse(buffer));
            } else {
                frames.add(createInstance(klass, buffer));
            }
        }
        return frames;
    }

    static private Frame createInstance(Class<Frame> klass, ByteBuffer buffer) throws Exception {
        return (Frame) klass.getMethod("parse", ByteBuffer.class).invoke(null, buffer);
    }

    static private long getTypeWithoutIncrement(ByteBuffer buffer) {
        int saved = buffer.position();
        long type = VariableLengthInteger.parse(buffer).getValue();
        buffer.position(saved);
        return type;
    }
}
