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

import java.nio.ByteBuffer;
import lombok.Value;

@Value(staticConstructor = "of")
public class StreamId {

	public static StreamId parse(ByteBuffer buffer) {
		return StreamId.of(buffer.getLong());
	}

	long id;

	public boolean isClientInitiated() {
		return (this.id & 0x01) == 0;
	}

	public boolean isServerInitiated() {
		return (this.id & 0x01) > 0;
	}

	public boolean isUniDirectional() {
		return (this.id & 0x02) > 0;
	}

	public boolean isBidirectional() {
		return (this.id & 0x02) == 0;
	}

	public byte[] getBytes() {
		return ByteBuffer.allocate(8).putLong(this.id).array();
	}

	@Override
	public String toString() {
		return String.format("StreamId(%x)", id);
	}

}
