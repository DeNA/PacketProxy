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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import packetproxy.common.Binary.HexString;
import packetproxy.util.PacketProxyUtility;
import org.apache.commons.lang3.ArrayUtils;

public class WebSocket {
	public enum OpCode {
		Cont    (0x00),
		Text    (0x01),
		Binary  (0x02),
		Close   (0x08),
		Ping    (0x09),
		Pong    (0x0A),

		DataRsv1(0x03),
		DataRsv2(0x04),
		DataRsv3(0x05),
		DataRsv4(0x06),
		DataRsv5(0x07),
		CtrlRsv1(0x0B),
		CtrlRsv2(0x0C),
		CtrlRsv3(0x0D),
		CtrlRsv4(0x0E),
		CtrlRsv5(0x0F);

		public final int code;
		private OpCode(final int code) {
			this.code = code;
		}

		public static OpCode fromInt(final int code) {
			if ((code & 0xF0) != 0) {
				throw new IllegalArgumentException("No such opcode");
			}
			for (OpCode opcode : values()) {
				if (opcode.code == code) {
					return opcode;
				}
			}
			return null;
		}
	}
	/*
	public static void main(String[] args) {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		try {
			//byte[] req = new Binary(new HexString("819CFADA7B10A8B5187BDAB30F308DB30F78DA922F5DB6EF5B479FB8287F99B11E64")).toByteArray();
			byte[] req = new Binary(new HexString("019CFADA7B10A8B5187BDAB30F308DB30F78DA922F5DB6EF5B479FB8287F99B11E64819CFADA7B10A8B5187BDAB30F308DB30F78DA922F5DB6EF5B479FB8287F99B11E64")).toByteArray();
			byte[] res = new Binary(new HexString("811C526F636B20697420776974682048544D4C3520576562536F636B6574")).toByteArray();
			WebSocket ws = new WebSocket(req);
			util.packetProxyLog(ws.toString());
			util.packetProxyLog(Integer.toString(WebSocket.checkDelimiter(req)));
			WebSocket ws_res = new WebSocket(res);
			util.packetProxyLog(ws_res.toString());
			util.packetProxyLog(Integer.toString(WebSocket.checkDelimiter(res)));
			byte[] req2 = new Binary(new HexString("811C526F636B20697420776974682048544D4C3520576562536F636B6574")).toByteArray();
			byte[] req3 = new WebSocket(req2).toByteArray();
			util.packetProxyLog(new Binary(req2).toHexString(16).toString());
			util.packetProxyLog(new Binary(req3).toHexString(16).toString());
			byte[] result = WebSocket.generateFromPayload("Test message".getBytes()).toByteArray();
			util.packetProxyLog(new Binary(result).toHexString(16).toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	*/

	private static boolean empty(byte[] data, int index) {
		return index < data.length ?  false : true;
	}

	public static int checkDelimiter(byte[] data) {
		boolean fin_flag = false;
		int length = 0;
		int i = 0;
		while (fin_flag != true) {
			if (empty(data, i)) return -1;
			fin_flag = ((data[i] & 0x80) != 0x0) ? true : false;
			length = 1;
			if (empty(data, i+1)) return -1;
			int mask_length_cand = (data[i+1] & 0xff);
			length += 1;
			length += ((mask_length_cand & 0x80) != 0x0) ? 4 : 0;
			int length_cand = mask_length_cand & 0x7f;
			if (length_cand < 126) {
				length += length_cand;
			} else if (length_cand == 126) {
				if (empty(data, i+2)) return -1;
				if (empty(data, i+3)) return -1;
				length += 2;
				length += ((data[i+2] & 0xff) << 8) + (data[i+3] & 0xff);
			} else if (length_cand == 127) {
				if (empty(data, i+2)) return -1;
				if (empty(data, i+3)) return -1;
				if (empty(data, i+4)) return -1;
				if (empty(data, i+5)) return -1;
				length += 4;
				length += ((data[i+2] & 0xff) << 24) + ((data[i+3] & 0xff) << 16) + ((data[i+4] & 0xff) << 8) + (data[i+5] & 0xff);
			}
			if (empty(data, i+length-1)) return -1;
			i += length;
		}
		return i;
	}

	public static class WebSocketBlock {
		private boolean fin_flag;
		private int length;
		private OpCode opcode;
		private byte[] payload;
		private boolean mask_flag = false;
		private byte[] mask_key = null;

		public WebSocketBlock(byte[] data, OpCode opcode, byte[] mask_key) {
			Init(data, opcode, mask_key);
		}
		public WebSocketBlock() {
			Init(new byte[]{}, OpCode.Close, null);
		}
		private void Init(byte[] data, OpCode optype, byte[] mask_key) {
			this.fin_flag = true;
			this.opcode = optype;
			if (this.opcode == OpCode.Close) {
				this.payload = new byte[]{};
			} else {
				this.payload = data.clone();
			}
			this.length = payload.length;
			if (mask_key != null) {
				this.mask_flag = true;
				this.mask_key = mask_key.clone();
			}
		}

		public WebSocketBlock(InputStream bin) throws Exception {
			int fin_opcode = bin.read();
			fin_flag = ((fin_opcode & 0x80) != 0x0) ? true : false;
			opcode = OpCode.fromInt(fin_opcode & 0x0f);
			int mask_length_cand = bin.read();
			mask_flag = ((mask_length_cand & 0x80) != 0x0) ? true : false;
			int length_cand = mask_length_cand & 0x7f;
			if (length_cand < 126) {
				length = length_cand;
			} else if (length_cand == 126) {
				length = (bin.read() << 8) + bin.read();
			} else if (length_cand == 127) {
				length = (bin.read() << 24) + (bin.read() << 16) + (bin.read() << 8) + bin.read();
			}
			if (mask_flag == true) {
				mask_key = new byte[4];
				bin.read(mask_key, 0, 4);
			}
			payload = new byte[length];
			bin.read(payload, 0, length);
			payload = decode(payload, mask_key);
		}

		static private byte[] encode(byte[] data, byte[] key) {
			assert(data != null);
			assert(key == null || key.length == 4);
			byte[] ret = data.clone();
			if (key != null) {
				for (int i = 0; i < data.length; i++) {
					ret[i] ^= key[i%4];
				}
			}
			return ret;
		}
		static private byte[] decode(byte[] data, byte[] key) {
			return encode(data, key);
		}

		public byte[] toByteArray() throws Exception {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			bout.write((fin_flag == true ? 0x80 : 0x00) | opcode.code);
			int mask_key_bit = mask_flag ? 1 << 7 : 0;
			if (length < 126) {
				bout.write(length | mask_key_bit);
			} else if (126 <= length && length < 32768) {
				bout.write(126 | mask_key_bit);
				bout.write((length & 0xff00) >> 8);
				bout.write(length & 0xff);
			} else if (32768 <= length) {
				bout.write(127 | mask_key_bit);
				bout.write((length & 0xff000000) >> 24);
				bout.write((length & 0xff0000) >> 16);
				bout.write((length & 0xff00) >> 8);
				bout.write(length & 0xff);
			}
			if (mask_flag) {
				bout.write(mask_key);
				byte[] data = encode(payload, mask_key);
				bout.write(data);
			} else {
				bout.write(payload);
			}
			return bout.toByteArray();
		}

		@Override
		public String toString() {
			return "WebSocketBlock [fin_flag=" + fin_flag + ", opcode="
				+ opcode + ", length=" + length + ", payload="
				+ new String(payload) + ", mask_flag=" + mask_flag
				+ ", mask_key=" + Arrays.toString(mask_key) + "]";
		}

		public int getLength() {
			return length;
		}

		public byte[] getPayload() {
			return payload;
		}

		public boolean isLast() {
			return fin_flag;
		}

		public OpCode getOpcode() {
			return opcode;
		}
		public boolean isMask() {
			return mask_flag;
		}
		public byte[] getMaskKey() {
			return mask_key;
		}
	}

	private int length;
	private byte[] payload;
	private List<WebSocketBlock> blocks = new ArrayList<WebSocketBlock>();;
	public enum Direction { CLIENT, SERVER };

	// デコード
	public WebSocket(byte[] data) throws Exception {
		ByteArrayInputStream bin = new ByteArrayInputStream(data);
		while (bin.available() > 0) {
			WebSocketBlock block = new WebSocketBlock(bin);
			blocks.add(block);
			length = length + block.getLength();
			payload = ArrayUtils.addAll(payload, block.getPayload());
		}
	}

	// エンコード
	public static WebSocket generateFromPayload(byte[] payload) {
		return new WebSocket(payload, OpCode.Text, null);
	}
	public static WebSocket generateFromPayload(byte[] payload, OpCode opcode, byte[] mask_key) {
		return new WebSocket(payload, opcode, mask_key);
	}
	// encodeモジュールでオリジナルのデータからopcode, mask_keyを引っ張ってきて同じ設定にする用
	public static WebSocket generateFromPayload(byte[] payload, WebSocket original_block_config) {
		assert(!original_block_config.blocks.isEmpty());
		if (original_block_config.blocks.size() == 0) { // Resendの時はマスクを決め打ちにする (暫定対応)。データ構造上、オリジナルのマスク値をとれない。
			return new WebSocket(payload, OpCode.Text, new byte[]{ (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA} );
		} else { // 通常の通信のときは、オリジナルのマスク値を利用
			OpCode opcode = original_block_config.blocks.get(0).getOpcode();
			byte[] mask_key = original_block_config.blocks.get(0).getMaskKey();
			return new WebSocket(payload, opcode, mask_key);
		}
	}
	private WebSocket(byte[] data, OpCode opcode, byte[] mask_key) {
		String str = new String(data);
		WebSocketBlock block;
		if (str.matches("WebSocket Finished")) {
			// TODO payloadのどこかに上の文字列が入っているとバグる
			block = new WebSocketBlock();
		} else {
			block = new WebSocketBlock(data, opcode, mask_key);
		}
		blocks.add(block);
	}

	public byte[] getPayload() {
		if (payload == null || payload.length == 0) {
			return "WebSocket Finished".getBytes();
		}
		return payload;
	}

	public List<WebSocketBlock> getFrames() {
		return blocks;
	}

	public byte[] toByteArray() throws Exception {
		byte[] result = new byte[]{};
		for (WebSocketBlock block : blocks) {
			result = ArrayUtils.addAll(result, block.toByteArray());
		}
		return result;
	}

	@Override
	public String toString() {
		return "WebSocket [length=" + length + ", payload="
			+ new String(payload) + ", blocks=" + blocks + "]";
	}

}
