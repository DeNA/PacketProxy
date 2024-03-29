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
package packetproxy.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Protobuf3
{ 
	public static class Key {
		public static enum Type {
			Variant,
			Bit64,
			LengthDelimited,
			StartGroup,
			EndGroup,
			Bit32,
			None,
			Reserved
		}

		long fieldNumber = 0;
		Type wireType = Type.None;

		public Key(long fieldNumber, Type wireType) {
			this.fieldNumber = fieldNumber;
			this.wireType = wireType;
		}
		public Key(long keyData) {
			init(keyData);
		}
		public Key(ByteArrayInputStream data) {
			init(decodeVar(data));
		}
		private void init(long keyData) {
			this.fieldNumber = keyData >> 3;
			this.wireType    = Type.values()[(int)(keyData & 0x07)];
		}
		public Type getWireType() { return this.wireType; }
		public long getFieldNumber() { return this.fieldNumber; }
		@Override
		public String toString() {
			return String.format("Key[FieldNum:%d, Type:%s]", fieldNumber, wireType);
		}
		public void writeTo(ByteArrayOutputStream output) {
			writeVar((fieldNumber << 3)|(wireType.ordinal() & 7), output);
		}
	}
	
	public static boolean validateVar(ByteArrayInputStream input) {
		byte[] raw = new byte[input.available()];
		input.mark(input.available());
		input.read(raw, 0, input.available());
		boolean ret = validateVar(raw);
		input.reset();
		return ret;
	}
	public static boolean validateVar(byte[] input) {
		return validateVar(input, null);
	}
	public static boolean validateVar(byte[] input, int[] outLength) {
		long var = 0;
		int i = 0;
		while (0 < input.length) {
			long nextB = input[i] & 0xff;
			var = var | ((nextB & 0x7f) << (7*i));
			i++;
			if (i >= 2 && ((nextB & 0xff) == 0)) // 0xf4 00 のように 00で終わるケース。これは、0x74になるべき
				return false;
			if ((nextB & 0x80) == 0)
				break;
			if (i > 9) // max 64bit (long size)
				return false;
			if (i == input.length)
				return false;
		}
		if (outLength != null) 
			outLength[0] = i;
		return true;
	}
	public static long decodeVar(ByteArrayInputStream input) {
		long var = 0;
		for (long i = 0; input.available() > 0; i++) {
			long nextB = (byte)(input.read() & 0xff) ;
			var = var | ((nextB & 0x7f) << (7*i));
			if ((nextB & 0x80) == 0)
				break;
		}
		return var;
	}
	public static void writeVar(long var, ByteArrayOutputStream output) {
		for (int i = 1; i <= 10; ++i) {
			byte b = (byte)(var & 0x7f);
			if (i == 10) {
				var = 0;
			} else {
				var = (var >>> 7);
			}
			if (var == 0) {
				output.write(b);
				break;
			} else {
				output.write((byte)(b|0x80));
			}
		}
	}
	
	public static boolean validateBit64(ByteArrayInputStream input) {
		return input.available() < 8 ? false : true;
	}
	public static boolean validateBit64(byte[] input) {
		return input.length < 8 ? false : true;
	}
	public static long decodeBit64(ByteArrayInputStream input) throws Exception {
		long bit64 = 0;
		for (int idx = 0; idx < 8; idx++) {
			long nextB = input.read();
			bit64 = bit64 | (nextB << (8*idx));
		}
		return bit64;
	}

	public static boolean validateBit32(ByteArrayInputStream input) {
		return input.available() < 4 ? false : true;
	}
	public static boolean validateBit32(byte[] input) {
		return input.length < 4 ? false : true;
	}
	public static int decodeBit32(ByteArrayInputStream input) throws Exception {
		int bit32 = 0;
		for (int idx = 0; idx < 4; idx++) {
			int nextB = input.read();
			bit32 = bit32 | (nextB << (8*idx));
		}
		return bit32;
	}

	/* 注意：repeatedデータが、inputバッファとぴったり合わないとfalse */
	public static boolean validateRepeatedStrictly(byte[] input) {
		int i = 0;
		int entries = 0;
		while (i < input.length) {
			byte[] subInput = ArrayUtils.subarray(input, i, input.length);
			int[] varLen = new int[1];
			if (validateVar(subInput, varLen) == false) {
				return false;
			}
			i = i + varLen[0];
			entries++;
		}
		if (entries > 64) { /* 64エントリを超える場合はrepeatedとみなさずbytesとみなす */
			return false;
		}
		return i == input.length;
	}
	public static List<Object> decodeRepeated(ByteArrayInputStream input) {
		List<Object> list = new LinkedList<>();
		while (input.available() > 0) {
			long var = decodeVar(input);
			list.add(var);
		}
		return list;
	}

	public static String decodeBytes(byte[] rawSubData) {
		return IntStream.range(0, rawSubData.length).mapToObj(i->String.format("%02x", rawSubData[i])).collect(Collectors.joining(":"));
	}
	public static byte[] encodeBytes(String bytes) throws Exception {
		String hexStr = bytes.replace(":", "");
		return new Binary(new Binary.HexString(hexStr)).toByteArray();
	}
	
	public static String decode(byte[] input) throws Exception {
		ByteArrayInputStream data = new ByteArrayInputStream(input);
		Map<String,Object> messages = new TreeMap<>();
		decodeData(data, messages);
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);
	}
	
	public static byte[] encode(String input) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		HashMap<String,Object> messages = mapper.readValue(input, new TypeReference<HashMap<String,Object>>(){});
		return encodeData(messages);
	}
	
	public static boolean decodeData(ByteArrayInputStream data, Map<String,Object> messages) throws Exception {
		int ordinary = 0;
		while (data.available() > 0) {
			Key key = new Key(data);
			
			switch (key.getWireType()) {
			case Variant: {
				if (validateVar(data) == false) {
					return false;
				}
				long variant = decodeVar(data);
	            messages.put(String.format("%04x:%04x:Varint", key.getFieldNumber(), ordinary), variant);
				break;
			}
			case Bit32: {
				if (validateBit32(data) == false) {
					return false;
				}
				int bit32 = decodeBit32(data);
	            messages.put(String.format("%04x:%04x:32-bit", key.getFieldNumber(), ordinary), bit32);
				break;
			}
			case Bit64: {
				if (validateBit64(data) == false) {
					return false;
				}
				long bit64 = decodeBit64(data);
	            messages.put(String.format("%04x:%04x:64-bit", key.getFieldNumber(), ordinary), bit64);
				break;
			}
			case LengthDelimited: {
				if (validateVar(data) == false) {
					return false;
				}
				long length = decodeVar(data);
				if (length > data.available()) {
					return false;
				}

				byte[] rawSubData = new byte[(int)length];
				data.read(rawSubData, 0, (int)length);

				/* String */
				if (StringUtils.validatePrintableUTF8(rawSubData)) {
					messages.put(String.format("%04x:%04x:String", key.getFieldNumber(), ordinary), new String(rawSubData, "UTF-8"));
					break;
				}
				
				/* Data */
				Map<String,Object> subMsg = new TreeMap<>();
				if (decodeData(new ByteArrayInputStream(rawSubData), subMsg) == true) {
					messages.put(String.format("%04x:%04x:embedded message", key.getFieldNumber(), ordinary), subMsg); 
					break;
				}
				
				/* Repeated */
				if (validateRepeatedStrictly(rawSubData) == true) {
					List<Object> list = decodeRepeated(new ByteArrayInputStream(rawSubData));
					messages.put(String.format("%04x:%04x:repeated", key.getFieldNumber(), ordinary), list);
					break;
				}

				/* Bytes */
				String result = decodeBytes(rawSubData);
				messages.put(String.format("%04x:%04x:bytes", key.getFieldNumber(), ordinary), result);
				break;
			}
			default:
				return false;
			}
			ordinary++;
		}
		return true;
	}

	public static byte[] encodeData(Map<String,Object> messages) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		String[] orderedKeys = new String[messages.keySet().size()];
		messages.keySet().stream().forEach(key -> {
			String[] keyval = key.split(":");
			int ordinary    = Integer.parseInt(keyval[1], 16);
			orderedKeys[ordinary] = key;
		});
		for (String key : orderedKeys) {
			String[] keyval = key.split(":");
			long fieldNumber = Long.parseLong(keyval[0], 16);
			String type      = keyval[2];

			switch (type) {
			case "Varint": {
				new Key(fieldNumber, Key.Type.Variant).writeTo(output);
				Object d = messages.get(key);
				long var = 0;
				if (d instanceof Integer) {
					var = ((Integer) d).longValue();
				} else if (d instanceof Long){
					var = ((Long) d).longValue();
				}
				writeVar(var, output);
				break;
			}
			case "String": {
				new Key(fieldNumber, Key.Type.LengthDelimited).writeTo(output);
				String str = messages.get(key).toString();
				writeVar(str.getBytes().length, output);
				output.write(str.getBytes());
				break;
			}
			case "32-bit": {
				new Key(fieldNumber, Key.Type.Bit32).writeTo(output);
				int bit32 = (int)messages.get(key); 
				output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bit32).array());
				break;
			}
			case "64-bit": {
				new Key(fieldNumber, Key.Type.Bit64).writeTo(output);
				Object d = messages.get(key); 
				long bit64 = 0;
				if (d instanceof Integer) {
					bit64 = ((Integer) d).longValue();
				} else if (d instanceof Long){
					bit64 = ((Long) d).longValue();
				}
				output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(bit64).array());
				break;
			}
			case "repeated": {
				new Key(fieldNumber, Key.Type.LengthDelimited).writeTo(output);
				List<Object> list = (List<Object>)messages.get(key); 
				ByteArrayOutputStream tmp = new ByteArrayOutputStream();
				list.stream().forEach(o -> {
					long var = 0;
					if (o instanceof Integer) {
						var = ((Integer) o).longValue();
					} else if (o instanceof Long){
						var = ((Long) o).longValue();
					} else {
						System.err.println("Unknown object type");
					}
					writeVar(var, tmp);
				});
				writeVar(tmp.toByteArray().length, output);
				output.write(tmp.toByteArray());
				break;
			}
			case "embedded message": {
				new Key(fieldNumber, Key.Type.LengthDelimited).writeTo(output);
				byte[] tmp = encodeData((Map<String,Object>)messages.get(key));
				writeVar(tmp.length, output);
				output.write(tmp);
				break;
			}
			case "bytes": {
				new Key(fieldNumber, Key.Type.LengthDelimited).writeTo(output);
				byte[] bytes = encodeBytes((String)messages.get(key));
				writeVar(bytes.length, output);
				output.write(bytes);
				break;
			}
			default:
				System.err.println(String.format("Unknown type: %s", type));
			}
		}
		return output.toByteArray();
	}
	
}
