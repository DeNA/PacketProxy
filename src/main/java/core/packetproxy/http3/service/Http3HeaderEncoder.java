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

package packetproxy.http3.service;

import static packetproxy.util.Throwing.rethrow;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http3.qpack.QpackEncoder;
import org.eclipse.jetty.http3.qpack.QpackException;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import packetproxy.quic.value.SimpleBytes;

public class Http3HeaderEncoder {

	final ByteBufferPool bufferPool = new MappedByteBufferPool();
	final ByteBufferPool.Lease lease = new ByteBufferPool.Lease(bufferPool);
	final QpackEncoder encoder;

	public Http3HeaderEncoder(long capacity) {
		this.encoder = new QpackEncoder(instructions -> instructions.forEach(i -> i.encode(this.lease)), 1024 * 1024);
		this.encoder.setCapacity((int) capacity);
	}

	/**
	 * エンコーダに命令を入力する Note: エンコーダの内部状態が変化します
	 */
	public void putInstructions(byte[] instructions) throws QpackException {
		this.encoder.parseInstructions(ByteBuffer.wrap(instructions));
	}

	/**
	 * 現在のデコーダの内部状態を命令化する
	 */
	public byte[] getInstructions() {
		ByteArrayOutputStream encoderInsts = new ByteArrayOutputStream();
		this.lease.getByteBuffers()
				.forEach(rethrow(inst -> encoderInsts.write(SimpleBytes.parse(inst, inst.remaining()).getBytes())));
		return encoderInsts.toByteArray();
	}

	/**
	 * ヘッダをエンコードする Note: エンコーダの内部状態が変化します
	 */
	public byte[] encode(long streamId, MetaData metaData) throws QpackException {
		ByteBuffer buffer = ByteBuffer.allocate(4096);
		this.encoder.encode(buffer, streamId, metaData);
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

}
