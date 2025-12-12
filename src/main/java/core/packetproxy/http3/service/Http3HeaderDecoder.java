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
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http3.qpack.QpackDecoder;
import org.eclipse.jetty.http3.qpack.QpackException;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import packetproxy.quic.value.SimpleBytes;

public class Http3HeaderDecoder {

	final ByteBufferPool bufferPool = new MappedByteBufferPool();
	final ByteBufferPool.Lease lease = new ByteBufferPool.Lease(bufferPool);
	final QpackDecoder decoder;

	public Http3HeaderDecoder() {
		this.decoder = new QpackDecoder(instructions -> instructions.forEach(i -> i.encode(this.lease)));
		this.decoder.setMaxHeadersSize(1024 * 1024);
		this.decoder.setMaxTableCapacity(1024 * 1024);
		this.decoder.setMaxBlockedStreams(256);
		this.decoder.setBeginNanoTimeSupplier(System::nanoTime);
	}

	/** デコーダに命令を入力する Note: デコーダの内部状態が変化します */
	public void putInstructions(byte[] instructions) throws QpackException {
		this.decoder.parseInstructions(ByteBuffer.wrap(instructions));
	}

	/** 現在のデコーダの内部状態を命令化する */
	public byte[] getInstructions() {
		ByteArrayOutputStream decoderInsts = new ByteArrayOutputStream();
		this.lease.getByteBuffers()
				.forEach(rethrow(inst -> decoderInsts.write(SimpleBytes.parse(inst, inst.remaining()).getBytes())));
		return decoderInsts.toByteArray();
	}

	/** エンコードされたヘッダをデコードする Note: デコーダの内部状態が変化します */
	public List<MetaData> decode(long streamId, byte[] headerEncoded) throws QpackException {
		List<MetaData> metaDataList = new ArrayList<>();
		this.decoder.decode(streamId, ByteBuffer.wrap(headerEncoded), (sid, metadata, wasBlocked) -> {
			if (metadata != null) {
				metaDataList.add(metadata);
			}
		});
		return metaDataList;
	}
}
