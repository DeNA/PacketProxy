/*
 * Copyright 2019,2023 DeNA Co., Ltd.
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

import static packetproxy.util.Logging.err;
import static packetproxy.util.Logging.errWithStackTrace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MessagePack {

	// https://github.com/msgpack/msgpack/blob/master/spec.md
	// 元々のMessagePackの型情報を保持する
	public static class Key {

		public static enum Type {
			Integer, UnsignedInteger, Float, Boolean, RawString, RawBinary, Map, Array, Extension, Nil, None;

			public static Type fromString(String str) {
				for (Type t : Type.values()) {

					if (t.toString().equalsIgnoreCase(str)) {

						return t;
					}
				}
				return null;
			}
		}

		Type type = Type.None;
		int size = -1;
		boolean fix;
		int value = 0; // firstByteに値も入っている場合に使う:

		public Key(byte firstByte) {
			init(firstByte);
		}

		public Key(Type type, int size, boolean fix) {
			this.type = type;
			this.size = size;
			this.fix = fix;
		}

		public void init(byte firstByte) {
			if ((byte) 0x00 <= firstByte && firstByte <= (byte) 0x7f) {

				this.type = Type.Integer;
				this.size = 1;
				this.fix = true;
				this.value = (byte) firstByte - (byte) 0x00;
			} else if ((byte) 0x80 <= firstByte && firstByte <= (byte) 0x8f) {

				this.type = Type.Map;
				this.size = (byte) firstByte - (byte) 0x80;
				this.fix = true;
			} else if ((byte) 0x90 <= firstByte && firstByte <= (byte) 0x9f) {

				this.type = Type.Array;
				this.size = (byte) firstByte - (byte) 0x90;
				this.fix = true;
			} else if ((byte) 0xa0 <= firstByte && firstByte <= (byte) 0xbf) {

				this.type = Type.RawString;
				this.size = (byte) firstByte - (byte) 0xa0;
				this.fix = true;
			} else if (firstByte == (byte) 0xc0) {

				this.type = Type.Nil;
				this.size = 1;
				this.fix = true;
			} else if (firstByte == (byte) 0xc1) {

				// never used
				this.type = Type.None;
				this.size = 0;
				this.fix = true;
			} else if (firstByte == (byte) 0xc2) {

				this.type = Type.Boolean;
				this.size = 1;
				this.fix = true;
				this.value = 0; // false
			} else if (firstByte == (byte) 0xc3) {

				this.type = Type.Boolean;
				this.size = 1;
				this.fix = true;
				this.value = 1; // true
			} else if ((byte) 0xc4 <= firstByte && firstByte <= (byte) 0xc6) {

				this.type = Type.RawBinary;
				this.size = 1 << (firstByte - 0xc4);
				this.fix = false;
			} else if ((byte) 0xc7 <= firstByte && firstByte <= (byte) 0xc9) {

				this.type = Type.Extension;
				this.size = 1 << (firstByte - 0xc7);
				this.fix = false;
			} else if ((byte) 0xca <= firstByte && firstByte <= (byte) 0xcb) {

				this.type = Type.Float;
				this.size = 4 << (firstByte - 0xca);
				this.fix = false;
			} else if ((byte) 0xcc <= firstByte && firstByte <= (byte) 0xcf) {

				this.type = Type.UnsignedInteger;
				this.size = 1 << (firstByte - 0xcc);
				this.fix = false;
			} else if ((byte) 0xd0 <= firstByte && firstByte <= (byte) 0xd3) {

				this.type = Type.Integer;
				this.size = 1 << (firstByte - 0xd0);
				this.fix = false;
			} else if ((byte) 0xd4 <= firstByte && firstByte <= (byte) 0xd8) {

				this.type = Type.Extension;
				this.size = 1 << (firstByte - 0xd4);
				this.fix = true;
			} else if ((byte) 0xd9 <= firstByte && firstByte <= (byte) 0xdb) {

				this.type = Type.RawString;
				this.size = 1 << (firstByte - 0xd9);
				this.fix = false;
			} else if ((byte) 0xdc <= firstByte && firstByte <= (byte) 0xdd) {

				this.type = Type.Array;
				this.size = 2 << (firstByte - 0xdc);
				this.fix = false;
			} else if ((byte) 0xde <= firstByte && firstByte <= (byte) 0xdf) {

				this.type = Type.Map;
				this.size = 2 << (firstByte - 0xde);
				this.fix = false;
			} else if ((byte) 0xe0 <= firstByte && firstByte <= (byte) 0xff) {

				this.type = Type.Integer;
				this.size = 1;
				this.fix = true;
				this.value = -1 * ((~firstByte) + 1);
			}

			assert (firstByte != 0xc1 && this.type != Type.None);
			assert (this.size != -1);
		}

		public Key(int index, Type type, int size, boolean fix) {
			this.type = type;
			this.size = size;
			this.fix = fix;
		}

		public Type getType() {
			return this.type;
		}

		// fixや小さい型で入りきらない場合に大きい型に変える
		// nは値または長さ
		public void fitType(long n) {
			switch (this.type) {
				case Integer : {
					if (this.fix && (n < -32 || 127 < n)) {

						this.fix = false;
						this.size = 0;
					}
					if (this.fix) {

						this.value = (int) n;
					} else {

						if (this.size < getIntegerSize(n)) {

							this.size = getIntegerSize(n);
						}
					}
					break;
				}
				case UnsignedInteger : {
					assert (!this.fix);
					if (this.size < getUnsignedIntegerSize(n)) {

						this.size = getUnsignedIntegerSize(n);
					}
					break;
				}
				case Float : {

					// unimplmented
					assert (false);
					assert (!this.fix);
					break;
				}
				case Boolean : {
					this.value = (n == 0 ? 0 : 1);
					break;
				}
				case RawString : {
					if (this.fix && n >= 32) {

						this.fix = false;
						this.size = 0;
					}
					if (this.fix) {

						this.size = (int) n;
					} else {

						if (this.size < getUnsignedIntegerSize(n)) {

							this.size = getUnsignedIntegerSize(n);
						}
					}
					break;
				}
				case RawBinary : {
					assert (!this.fix);
					if (this.size < getUnsignedIntegerSize(n)) {

						this.size = getUnsignedIntegerSize(n);
					}
					break;
				}
				case Array :
				case Map : {
					if (this.fix && n >= 16) {

						this.fix = false;
						this.size = 0;
					}
					if (this.fix) {

						this.size = (int) n;
					} else {

						int size = getUnsignedIntegerSize(n);
						if (size == 1) {

							size = 2;
						}
						if (this.size < size) {

							this.size = size;
						}
					}
					break;
				}
				case Extension : {
					if (this.fix && n != 1 && n != 2 && n != 4 && n != 8 && n != 16) {

						this.fix = false;
						this.size = 0;
					}
					if (this.fix) {

						this.size = (int) n;
					} else {

						if (this.size < getUnsignedIntegerSize(n)) {

							this.size = getUnsignedIntegerSize(n);
						}
					}
					break;
				}
				case Nil : {

					// nop
					break;
				}
				case None : {

					// nop
					break;
				}
			}
		}

		public byte toFirstByte() {
			switch (this.type) {
				case Integer : {
					if (this.fix) {

						return (byte) this.value;
					} else {

						return (byte) (0xd0 + rightmostBitPostion(this.size));
					}
				}
				case UnsignedInteger : {
					assert (!this.fix);
					return (byte) (0xcc + rightmostBitPostion(this.size));
				}
				case Float : {
					assert (!this.fix);
					return (byte) (0xca + rightmostBitPostion(this.size) - 2);
				}
				case Boolean : {
					assert (this.fix);
					return (byte) (0xc2 + this.value);
				}
				case RawString : {
					if (this.fix) {

						return (byte) (0xa0 + this.size);
					} else {

						return (byte) (0xd9 + rightmostBitPostion(this.size));
					}
				}
				case RawBinary : {
					assert (!this.fix);
					return (byte) (0xc4 + rightmostBitPostion(this.size));
				}
				case Map : {
					if (this.fix) {

						return (byte) (0x80 + this.size);
					} else {

						return (byte) (0xde + rightmostBitPostion(this.size) - 1);
					}
				}
				case Array : {
					if (this.fix) {

						return (byte) (0x90 + this.size);
					} else {

						return (byte) (0xdc + rightmostBitPostion(this.size) - 1);
					}
				}
				case Extension : {
					if (this.fix) {

						return (byte) (0xd4 + rightmostBitPostion(this.size));
					} else {

						return (byte) (0xc7 + rightmostBitPostion(this.size));
					}
				}
				case Nil : {
					return (byte) 0xc0;
				}
				case None : {
					return (byte) 0xc1;
				}
			}
			assert (false);
			return (byte) 0xc1;
		}

		@Override
		public String toString() {
			return String.format("Key[Type:%s, Size: %d, Fix: %d]", type, size, fix ? 1 : 0);
		}

		// 右端のビットの位置を返す、0-origin
		private int rightmostBitPostion(int n) {
			for (int i = 0; i < 64; i++) {

				if (((n >> i) & 1) == 1) {

					return i;
				}
			}
			return 0;
		}

		private int getIntegerSize(long n) {
			if (-128 <= n && n <= 127) {

				return 1;
			}
			if (-32768 <= n && n <= 32767) {

				return 2;
			}
			if (-2147483648L <= n && n <= 2147483647L) {

				return 4;
			}
			return 8;
		}

		private int getUnsignedIntegerSize(long n) {
			if (n <= 255) {

				return 1;
			}
			if (n <= 65535) {

				return 2;
			}
			if (n <= 4294967295L) {

				return 4;
			}
			return 8;
		}
	}

	public static String decode(byte[] input_data) throws Exception {
		ByteArrayInputStream input = new ByteArrayInputStream(input_data);
		Map<String, Object> messages = new TreeMap<>();
		// Logging.log("Decode");
		// Logging.log(StringUtils.byteToHex(input_data));
		decodeData(0, input, messages);
		// int useLength = input_data.length - input.available();
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);
	}

	public static boolean decodeData(int ordinary, ByteArrayInputStream input, Map<String, Object> messages)
			throws Exception {
		try {

			if (input.available() == 0) {

				err("MessagePack Parse failed: out of range");
				return false;
			}
			byte firstByte = (byte) (input.read() & 0xff);
			Key key = new Key(firstByte);
			Object value = null;
			// Logging.log(key + " rest: " + input.available());
			switch (key.type) {
				case Integer : {
					if (key.fix) {

						value = key.value;
					} else {

						value = decodeInteger(key.size, true, input);
					}
					break;
				}
				case UnsignedInteger : {
					assert (!key.fix);
					value = decodeInteger(key.size, false, input);
					break;
				}
				case Float : {
					assert (!key.fix);
					if (key.size == 4) {

						value = decodeFloat(input);
					} else {

						value = decodeDouble(input);
					}
					break;
				}
				case Boolean : {
					value = key.value;
					break;
				}
				case RawString : {
					int length;
					if (key.fix) {

						length = key.size;
					} else {

						length = (int) decodeInteger(key.size, false, input);
					}
					value = decodeString(length, input);
					break;
				}
				case RawBinary : {
					assert (!key.fix);
					int length = (int) decodeInteger(key.size, false, input);
					value = decodeBinary(length, input);
					break;
				}
				case Map : {
					int length;
					if (key.fix) {

						length = key.size;
					} else {

						length = (int) decodeInteger(key.size, false, input);
					}
					List<Object> list = new ArrayList<>();
					for (int i = 0; i < 2 * length; i++) {

						Map<String, Object> child = new TreeMap<>();
						if (!decodeData(i, input, child)) {

							return false;
						}
						list.add(child);
					}
					value = list;
					break;
				}
				case Array : {
					int length;
					if (key.fix) {

						length = key.size;
					} else {

						length = (int) decodeInteger(key.size, false, input);
					}
					List<Object> list = new ArrayList<>();
					for (int i = 0; i < length; i++) {

						Map<String, Object> child = new TreeMap<>();
						if (!decodeData(i, input, child)) {

							return false;
						}
						list.add(child);
					}
					value = list;
					break;
				}
				case Extension : {
					int length;
					if (key.fix) {

						length = key.size;
					} else {

						length = (int) decodeInteger(key.size, false, input);
					}
					int type = (int) decodeInteger(1, false, input);
					String data = decodeBinary(length, input);
					value = String.format("%d:%s", type, data);
					break;
				}
				case Nil : {
					value = null;
					break;
				}
				case None : {
					value = null;
					break;
				}
			}
			messages.put(keyString(key, ordinary), value);
		} catch (Exception e) {

			errWithStackTrace(e);
			return false;
		}
		return true;
	}

	public static String keyString(Key key, int ordinary) {
		return String.format("%02d:%s:%01d:%01d", ordinary, key.type, key.size, key.fix ? 1 : 0);
	}

	public static byte[] encode(String input) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		HashMap<String, Object> messages = mapper.readValue(input, new TypeReference<HashMap<String, Object>>() {
		});
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		encodeData(messages, output);
		// Logging.log("Encode");
		// Logging.log(StringUtils.byteToHex(output.toByteArray()));
		return output.toByteArray();
	}

	public static void encodeData(Map<String, Object> messages, ByteArrayOutputStream output) throws Exception {
		ArrayList<String> orderedKeys = new ArrayList<String>();
		messages.keySet().stream().forEach(key -> {

			// String[] keyval = key.split(":");
			// int ordinary = Integer.parseInt(keyval[3]);
			orderedKeys.add(key);;
		});
		Collections.sort(orderedKeys);
		for (String keyStr : orderedKeys) {

			Key key;
			{
				String[] keyval = keyStr.split(":");
				Key.Type type = Key.Type.fromString(keyval[1]);
				int size = Integer.parseInt(keyval[2]);
				boolean fix = Integer.parseInt(keyval[3]) == 1;
				key = new Key(type, size, fix);
			}
			// Logging.log(key);

			switch (key.type) {
				case Integer : {
					long value = ((Number) messages.get(keyStr)).longValue();
					key.fitType((value));
					output.write(key.toFirstByte());
					if (!key.fix) {

						byte[] valueBytes = encodeInteger(key.size, false, value);
						output.write(valueBytes);
					}
					break;
				}
				case UnsignedInteger : {
					assert (!key.fix);
					long value = ((Number) messages.get(keyStr)).longValue();
					key.fitType((value));
					output.write(key.toFirstByte());
					byte[] valueBytes = encodeInteger(key.size, false, value);
					output.write(valueBytes);
					break;
				}
				case Float : {
					assert (!key.fix);
					double value = (Double) messages.get(keyStr);
					output.write(key.toFirstByte());
					byte[] valueBytes;
					if (key.size == 4) {

						valueBytes = encodeFloat((float) value);
					} else {

						valueBytes = encodeDouble(value);
					}
					output.write(valueBytes);
					break;
				}
				case Boolean : {
					long value = ((Number) messages.get(keyStr)).longValue();
					key.fitType(value);
					output.write(key.toFirstByte());
					break;
				}
				case RawString : {
					String value = (String) messages.get(keyStr);
					key.fitType((long) value.length());
					output.write(key.toFirstByte());
					byte[] valueBytes = encodeString(value);
					if (!key.fix) {

						output.write(encodeInteger(key.size, false, valueBytes.length));
					}
					output.write(valueBytes);
					break;
				}
				case RawBinary : {
					String value = (String) messages.get(keyStr);
					key.fitType((long) value.length() / 2);
					output.write(key.toFirstByte());
					byte[] valueBytes = encodeBinary(value);
					if (!key.fix) {

						output.write(encodeInteger(key.size, false, valueBytes.length));
					}
					output.write(valueBytes);
					break;
				}
				case Map : {
					List<Object> list = (List<Object>) messages.get(keyStr);
					key.fitType((long) list.size() / 2);
					output.write(key.toFirstByte());
					if (!key.fix) {

						byte[] valueBytes = encodeInteger(key.size, false, (long) list.size() / 2);
						output.write(valueBytes);
					}
					for (int i = 0; i < list.size(); i++) {

						Map<String, Object> child = (Map<String, Object>) list.get(i);
						encodeData(child, output);
					}
					break;
				}
				case Array : {
					List<Object> list = (List<Object>) messages.get(keyStr);
					key.fitType((long) list.size());
					output.write(key.toFirstByte());
					if (!key.fix) {

						byte[] valueBytes = encodeInteger(key.size, false, (long) list.size());
						output.write(valueBytes);
					}
					for (int i = 0; i < list.size(); i++) {

						Map<String, Object> child = (Map<String, Object>) list.get(i);
						encodeData(child, output);
					}
					break;
				}
				case Extension : {
					String value = (String) messages.get(keyStr);
					String[] typevalue = value.split(":");
					byte type = (byte) Integer.parseInt(typevalue[0]);
					byte[] valueBytes = encodeBinary(typevalue[1]);

					key.fitType((long) valueBytes.length);
					output.write(key.toFirstByte());
					if (!key.fix) {

						output.write(encodeInteger(key.size, false, valueBytes.length));
					}
					output.write(type);
					output.write(valueBytes);
					break;
				}
				case Nil : {
					output.write(key.toFirstByte());
					break;
				}
				case None : {
					output.write(key.toFirstByte());
					break;
				}
			}
		}
	}

	public static long decodeInteger(int size, boolean signed, ByteArrayInputStream input) throws Exception {
		if (input.available() < size) {

			throw new Exception("MessagePack Parse failed: out of range");
		}
		long firstbit = 0;
		long bit64 = 0;
		for (int idx = 0; idx < size; idx++) {

			long nextB = input.read();
			bit64 = (bit64 << 8) | nextB;
			if (idx == 0) {

				firstbit = (nextB >> 7) & 1;
			}
		}
		if (signed && firstbit == 1) {

			// signedで負数は符号拡張する
			for (int idx = size; idx < 8; idx++) {

				bit64 = bit64 | (0xff << (8 * idx));
			}
		}
		return bit64;
	}

	public static byte[] encodeInteger(int size, boolean signed, long v) throws Exception {
		byte[] ret = new byte[size];
		for (int idx = 0; idx < size; idx++) {

			ret[size - idx - 1] = (byte) ((v >> (8 * idx)) & 0xff);
		}
		return ret;
	}

	public static float decodeFloat(ByteArrayInputStream input) throws Exception {
		if (input.available() < 4) {

			throw new Exception("MessagePack Parse failed: out of range");
		}
		DataInputStream din = new DataInputStream(input);
		return din.readFloat();
	}

	public static byte[] encodeFloat(float v) throws Exception {
		ByteArrayOutputStream bas = new ByteArrayOutputStream();
		DataOutputStream ds = new DataOutputStream(bas);
		ds.writeFloat(v);
		return bas.toByteArray();
	}

	public static double decodeDouble(ByteArrayInputStream input) throws Exception {
		if (input.available() < 8) {

			throw new Exception("MessagePack Parse failed: out of range");
		}
		DataInputStream din = new DataInputStream(input);
		return din.readDouble();
	}

	public static byte[] encodeDouble(double v) throws Exception {
		ByteArrayOutputStream bas = new ByteArrayOutputStream();
		DataOutputStream ds = new DataOutputStream(bas);
		ds.writeDouble(v);
		return bas.toByteArray();
	}

	public static String decodeString(int length, ByteArrayInputStream input) throws Exception {
		if (input.available() < length) {

			throw new Exception("MessagePack Parse failed: out of range");
		}
		byte[] data = input.readNBytes(length);
		return new String(data);
	}

	public static byte[] encodeString(String v) throws Exception {
		return v.getBytes();
	}

	public static String decodeBinary(int length, ByteArrayInputStream input) throws Exception {
		if (input.available() < length) {

			throw new Exception("MessagePack Parse failed: out of range");
		}
		byte[] data = input.readNBytes(length);
		return new String(StringUtils.byteToHex(data));
	}

	public static byte[] encodeBinary(String v) throws Exception {
		return StringUtils.hexToByte(v.getBytes());
	}
}
