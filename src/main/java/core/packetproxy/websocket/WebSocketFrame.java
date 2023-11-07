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

import lombok.Value;
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.value.SimpleBytes;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

@Value
public class WebSocketFrame
{
    static private boolean empty(byte[] data, int index) {
        return index >= data.length;
    }

    static public int checkDelimiter(byte[] data) {
        boolean finFlg = false;
        int length = 0;
        int i = 0;
        while (!finFlg) {
            if (empty(data, i)) return -1;
            finFlg = (data[i] & 0x80) != 0x0;
            length = 1;
            if (empty(data, i+1)) return -1;
            int maskAndLength = (data[i+1] & 0xff);
            length += 1;
            length += ((maskAndLength & 0x80) != 0x0) ? 4 : 0;
            int lengthType = maskAndLength & 0x7f;
            if (lengthType < 126) {
                length += lengthType;
            } else if (lengthType == 126) {
                if (empty(data, i+2)) return -1;
                if (empty(data, i+3)) return -1;
                length += 2;
                length += ((data[i+2] & 0xff) << 8) + (data[i+3] & 0xff);
            } else { /* lengthType == 127 */
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

    static public WebSocketFrame parse(byte[] bytes) throws Exception {
        return parse(ByteBuffer.wrap(bytes));
    }

    static public WebSocketFrame parse(ByteBuffer buffer) throws Exception {
        ByteArrayOutputStream payloads = new ByteArrayOutputStream();
        OpCode opCode = null;
        boolean finFlg;
        boolean maskFlg;

        do {
            byte finOp = buffer.get();
            finFlg = (finOp & 0x80) != 0x0;
            if (opCode == null) {
                opCode = OpCode.fromInt(finOp & 0x0f);
            }
            byte maskAndLength = buffer.get();
            maskFlg = (maskAndLength & 0x80) != 0x0;
            int lengthType = maskAndLength & 0x7f;
            int length;
            if (lengthType < 126) {
                length = lengthType;
            } else if (lengthType == 126) {
                length = buffer.getShort();
            } else { /* lengthType == 127 */
                length = buffer.getInt();
            }
            byte[] mask = null;
            if (maskFlg) {
                mask = SimpleBytes.parse(buffer, 4).getBytes();
            }
            byte[] payload = null;
            if (length > 0) {
                payload = SimpleBytes.parse(buffer, length).getBytes();
                payload = decodeMask(payload, mask);
                payloads.write(payload);
            }
        } while (!finFlg);

        return of(opCode, payloads.toByteArray(), maskFlg);
    }

    static public WebSocketFrame of(byte[] payload, boolean maskEnabled) {
        return of(OpCode.Binary, payload, maskEnabled);
    }

    static public WebSocketFrame of(OpCode opcode, byte[] payload, boolean maskEnabled) {
        return new WebSocketFrame(opcode, payload, maskEnabled);
    }

    static private byte[] encodeMask(byte[] data, byte[] key) {
        assert (data != null);
        assert (key == null || key.length == 4);
        byte[] ret = data.clone();
        if (key != null) {
            for (int i = 0; i < data.length; i++) {
                ret[i] ^= key[i % 4];
            }
        }
        return ret;
    }

    static private byte[] decodeMask(byte[] data, byte[] key) {
        return encodeMask(data, key);
    }

    OpCode opcode;
    byte[] payload;
    boolean maskEnabled;

    private WebSocketFrame(OpCode opcode, byte[] payload, boolean maskEnabled) {
        this.opcode = opcode;
        this.payload = payload;
        this.maskEnabled = maskEnabled;
    }

    public byte[] getBytes() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + 14);
        buffer.put((byte)(0x80|opcode.code)); /* finFlg */
        byte maskFlg = this.maskEnabled ? (byte)0x80 : (byte)0x00;
        if (payload.length < 126) {
            buffer.put((byte)(payload.length|maskFlg));
        } else if (payload.length < 32768) {
            buffer.put((byte)(126|maskFlg));
            buffer.putShort((short)payload.length);
        } else {
            buffer.put((byte)(127|maskFlg));
            buffer.putInt(payload.length);
        }
        if (this.maskEnabled) {
            byte[] mask = Hex.decodeHex("0A0A0A0A");
            buffer.put(mask);
            buffer.put(encodeMask(payload, mask));
        } else {
            buffer.put(payload);
        }
        buffer.flip();
        return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
    }

}
