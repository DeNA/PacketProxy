package packetproxy.quic.service.transportparameter;

import com.google.common.collect.Sets;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.tools.*;
import packetproxy.quic.value.VariableLengthInteger;
import packetproxy.quic.value.transportparameter.TransportParameter;
import packetproxy.quic.value.transportparameter.UnknownParameter;

public class TransportParameterParser {

	private static final String transportParameterPackage = "packetproxy.quic.value.transportparameter";
	private static final Class<TransportParameter> transportParameterClass = TransportParameter.class;
	private static Map<Long, Class<TransportParameter>> transportParameterMap;

	public static TransportParameter parse(ByteBuffer buffer) throws Exception {
		int saved = buffer.position();
		long type = VariableLengthInteger.parse(buffer).getValue();
		buffer.position(saved);

		if (transportParameterMap == null) {
			createTransportParameterMap();
		}

		Class<TransportParameter> klass = transportParameterMap.get(type);
		if (klass == null) {
			klass = transportParameterMap.get(UnknownParameter.ID);
		}
		return createInstance(klass, buffer);
	}

	private static TransportParameter createInstance(Class<TransportParameter> klass, ByteBuffer buffer)
			throws Exception {
		return klass.getConstructor(ByteBuffer.class).newInstance(buffer);
	}

	private static TransportParameter createInstance(Class<TransportParameter> klass) throws Exception {
		return klass.getConstructor().newInstance();
	}

	private static void createTransportParameterMap() throws Exception {
		transportParameterMap = new HashMap<>();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		JavaFileManager fm = compiler.getStandardFileManager(new DiagnosticCollector<JavaFileObject>(), null, null);

		Set<JavaFileObject.Kind> kind = Sets.newHashSet(JavaFileObject.Kind.CLASS);
		for (JavaFileObject f : fm.list(StandardLocation.CLASS_PATH, transportParameterPackage, kind, true)) {
			Path encode_file_path = Paths.get(f.getName());
			String encode_class_path = encode_file_path.toString().replaceAll("/", ".")
					.replaceFirst("^.*" + transportParameterPackage, transportParameterPackage)
					.replaceAll("\\.class.*$", "");
			Class<?> klass = Class.forName(encode_class_path);
			if (transportParameterClass.isAssignableFrom(klass) && !Modifier.isAbstract(klass.getModifiers())) {
				long id = klass.getField("ID").getLong(null);
				transportParameterMap.put(id, (Class<TransportParameter>) klass);
			}
		}
	}

}
