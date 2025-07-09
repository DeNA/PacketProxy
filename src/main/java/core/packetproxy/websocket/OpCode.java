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
package packetproxy.websocket;

public enum OpCode {
	Cont(0x00), Text(0x01), Binary(0x02), Close(0x08), Ping(0x09), Pong(0x0A),

	DataRsv1(0x03), DataRsv2(0x04), DataRsv3(0x05), DataRsv4(0x06), DataRsv5(0x07), CtrlRsv1(0x0B), CtrlRsv2(
			0x0C), CtrlRsv3(0x0D), CtrlRsv4(0x0E), CtrlRsv5(0x0F);

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
