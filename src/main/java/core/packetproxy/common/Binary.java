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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Binary {

	private static String[] int_to_ascii_string = new String[256];
	private static String[] int_to_hex_string = new String[256];
	private byte[] hexarray;

	public static class HexString {

		private String str;

		public HexString(String str) {
			this.str = str;
		}

		@Override
		public String toString() {
			return str;
		}
	}

	public static class AsciiString {

		private String str;

		public AsciiString(String str) {
			this.str = str;
		}

		@Override
		public String toString() {
			return str;
		}
	}

	/*
	public static void main(String[] args) {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		try {
			util.packetProxyLog(new Binary(new HexString("616263")).toHexString(2).toString());
			util.packetProxyLog(new Binary(new HexString("61 62 63")).toHexString(2).toString());
			util.packetProxyLog(new Binary(new HexString("61  62    63")).toHexString(2).toString());
			util.packetProxyLog(new Binary(new byte[]{0x61,0x62,0x63}).toHexString(2).toString());
			util.packetProxyLog(new Binary(new HexString("616263a283")).toAsciiString(2).toString());
			util.packetProxyLog(new Binary(new HexString("61 62 63 a2 83")).toAsciiString(2).toString());
			util.packetProxyLog(new Binary(new HexString("61  62    63   a283")).toAsciiString(2).toString());
			util.packetProxyLog(new Binary(new byte[]{0x61,0x62,0x63,(byte)0xA2,(byte)0x83}).toAsciiString(2).toString());
			util.packetProxyLog(new Binary("hoge\nhogeあああ".getBytes()).toHexString().toString());
	
			String data = new String("\n");
			for (int i = 0; i < 20; i++) {
				data = data + data;
			}
			long start = System.currentTimeMillis();
			new Binary(data.getBytes()).toHexString();
			long end = System.currentTimeMillis();
			util.packetProxyLog("size: " + data.length() + ", time: " + ((end - start) / 1000.0));
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}
	*/

	public Binary(byte[] hexArray) throws Exception {
		this.hexarray = hexArray;
	}

	public Binary(HexString hexstr) throws Exception {
		this.hexarray = createHexarrayFromHexstr(hexstr);
	}

	public byte[] toByteArray() {
		return this.hexarray;
	}

	public int toInt(boolean littleEndiain) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		if (littleEndiain) {

			bb.order(ByteOrder.LITTLE_ENDIAN);
		}
		for (int i = 0; i < 4; i++) {

			if (i < hexarray.length)
				bb.put(hexarray[i]);
			else
				bb.put((byte) 0);
		}
		bb.flip();
		return bb.getInt();
	}

	public long toLong(boolean littleEndiain) {
		ByteBuffer bb = ByteBuffer.allocate(8);
		if (littleEndiain) {

			bb.order(ByteOrder.LITTLE_ENDIAN);
		}
		for (int i = 0; i < 8; i++) {

			if (i < hexarray.length)
				bb.put(hexarray[i]);
			else
				bb.put((byte) 0);
		}
		bb.flip();
		return bb.getLong();
	}

	public HexString toHexString() {
		return createHexstrFromHexarray(hexarray, 0);
	}

	public HexString toHexString(int count) {
		return createHexstrFromHexarray(hexarray, count);
	}

	public AsciiString toAsciiString() {
		return createAsciistrFromHexarray(hexarray, 0);
	}

	public AsciiString toAsciiString(int count) {
		return createAsciistrFromHexarray(hexarray, count);
	}

	private byte[] createHexarrayFromHexstr(HexString hstr) throws Exception {
		String hexstr = hstr.toString();
		hexstr = hexstr.replaceAll(" ", "");
		hexstr = hexstr.replaceAll("\r", "");
		hexstr = hexstr.replaceAll("\n", "");
		if (hexstr.isEmpty()) {

			return new byte[0];
		}
		if (hexstr.length() % 2 != 0) {

			throw new IllegalArgumentException("format error");
		}
		byte[] hexarray = new byte[hexstr.length() / 2];
		for (int i = 0; i < hexstr.length(); i += 2) {

			String oneByte = hexstr.substring(i, i + 2);
			hexarray[i / 2] = (byte) Integer.parseInt(oneByte, 16);
		}
		return hexarray;
	}

	private HexString createHexstrFromHexarray(byte[] hexarray, int count) {
		initIntToHexString();
		StringBuilder sb = new StringBuilder();;
		for (int i = 0; i < hexarray.length; i++) {

			sb.append(int_to_hex_string[hexarray[i] & 0xff]);
			if (count != 0 && ((i + 1) % count) == 0) {

				sb.append("\n");
			}
		}
		return new HexString(new String(sb));
	}

	private AsciiString createAsciistrFromHexarray(byte[] hexarray, int count) {
		initIntToAsciiString();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < hexarray.length; i++) {

			sb.append(int_to_ascii_string[hexarray[i] & 0xff]);
			if (count != 0 && ((i + 1) % count) == 0) {

				sb.append("\n");
			}
		}
		return new AsciiString(new String(sb));
	}

	// 高速化用の変換配列を初期化
	void initIntToHexString() {
		if (int_to_hex_string[255] != null) {

			return;
		}
		for (int i = 0; i < 256; i++) {

			int_to_hex_string[i] = String.format("%02X ", i);
		}
	}

	void initIntToAsciiString() {
		if (int_to_ascii_string[255] != null) {

			return;
		}
		for (int i = 0; i < 256; i++) {

			if (i < 20 || 0x7f < i) {

				int_to_ascii_string[i] = ".";
			} else {

				int_to_ascii_string[i] = String.valueOf((char) i);
			}
		}
	}
}
