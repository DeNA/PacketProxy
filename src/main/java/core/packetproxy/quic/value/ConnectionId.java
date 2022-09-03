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

package packetproxy.quic.value;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.utils.Constants;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

@Value(staticConstructor = "of")
public class ConnectionId {

    static public ConnectionId generateRandom() {
        byte[] connId = new byte[Constants.CONNECTION_ID_SIZE];
        new SecureRandom().nextBytes(connId);
        return new ConnectionId(connId);
    }

    static public ConnectionId parse(ByteBuffer buffer, long length) {
        byte[] connId = SimpleBytes.parse(buffer, length).getBytes();
        return new ConnectionId(connId);
    }

    @Getter(AccessLevel.NONE)
    byte[] connId;


    public byte[] getBytes() {
        return connId;
    }

    @Override
    public String toString() {
        return String.format("ConnectionId([%s])", Hex.encodeHexString(this.connId));
    }
}
