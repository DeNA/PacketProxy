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

package packetproxy.quic.service.framegenerator;

import java.util.ArrayList;
import java.util.List;
import net.luminis.tls.handshake.HandshakeMessage;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.service.frame.FramesBuilder;
import packetproxy.quic.value.frame.CryptoFrame;

public class MessagesToCryptoFrames {

	private final List<HandshakeMessage> handshakeMessages = new ArrayList<>();
	private long offset = 0;

	public synchronized void write(HandshakeMessage handshakeMessage) {
		this.handshakeMessages.add(handshakeMessage);
	}

	public synchronized Frames toCryptoFrames() {
		FramesBuilder framesBuilder = new FramesBuilder();

		for (HandshakeMessage msg : handshakeMessages) {

			byte[] msgBytes = msg.getBytes();
			int msgLen = msgBytes.length;
			int msgOff = 0;
			while (msgLen > 0) {

				int subMsgLen = Math.min(msgLen, 1200);
				byte[] subMsg = ArrayUtils.subarray(msgBytes, msgOff, msgOff + subMsgLen);
				CryptoFrame cryptoFrame = new CryptoFrame(this.offset, subMsg);
				framesBuilder.add(cryptoFrame);
				this.offset += subMsgLen;
				msgOff += subMsgLen;
				msgLen -= subMsgLen;
			}
		}
		this.handshakeMessages.clear(); /* remove all messages */
		return framesBuilder.build();
	}
}
