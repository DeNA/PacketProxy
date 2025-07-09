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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import net.luminis.tls.ProtectionKeysType;
import net.luminis.tls.TlsProtocolException;
import net.luminis.tls.handshake.*;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.quic.value.frame.CryptoFrame;

public class CryptoFramesToMessages {

	public static HandshakeMessage convertToHandshakeMessage(byte[] handshakeMsgBytes) throws Exception {
		TlsMessageParser parser = new TlsMessageParser();
		DebugMessageProcessor processor = new DebugMessageProcessor();
		return parser.parseAndProcessHandshakeMessage(ByteBuffer.wrap(handshakeMsgBytes), processor,
				ProtectionKeysType.None);
	}

	private final List<CryptoFrame> cryptoFrames = new ArrayList<>();
	private final ByteArrayOutputStream messages = new ByteArrayOutputStream();
	private int nextMessageLength;
	private int alreadyParsedBytes;

	public CryptoFramesToMessages() {
		this.nextMessageLength = 0;
		this.alreadyParsedBytes = 0;
	}

	public void write(CryptoFrame cryptoFrame) {
		cryptoFrames.add(cryptoFrame);
	}

	public List<HandshakeMessage> getHandshakeMessages() {
		List<HandshakeMessage> messages = new ArrayList<>();
		for (var msgOpt = getHandshakeMessage(); msgOpt.isPresent(); msgOpt = getHandshakeMessage()) {
			messages.add(msgOpt.get());
		}
		return messages;
	}

	@SneakyThrows
	public Optional<HandshakeMessage> getHandshakeMessage() {

		if (this.nextMessageLength == 0) {
			if (this.messages.size() < 4) { /* header(4B) = messageType(1B) + messageLength(3B) */
				if (!refillOneCryptoFrameToNextMessageBuffer(this.alreadyParsedBytes + this.messages.size())) {
					return Optional.empty();
				}
			}
			byte[] data = this.messages.toByteArray();
			if (data.length < 4) {
				return Optional.empty();
			}
			this.nextMessageLength = 4; /* header(4B) */
			this.nextMessageLength += ((data[1] & 0xff) << 16) | ((data[2] & 0xff) << 8) | (data[3] & 0xff);
		}

		while (this.messages.size() < this.nextMessageLength) {
			if (!refillOneCryptoFrameToNextMessageBuffer(this.alreadyParsedBytes + this.messages.size())) {
				return Optional.empty();
			}
		}

		byte[] messagesBytes = this.messages.toByteArray();
		byte[] message = ArrayUtils.subarray(messagesBytes, 0, this.nextMessageLength);
		byte[] remaining = ArrayUtils.subarray(messagesBytes, this.nextMessageLength, messagesBytes.length);
		this.messages.reset();
		this.messages.write(remaining); /* 読み過ぎた分は書き戻す */
		this.nextMessageLength = 0;
		this.alreadyParsedBytes += message.length;

		TlsMessageParser parser = new TlsMessageParser();
		DebugMessageProcessor processor = new DebugMessageProcessor();
		HandshakeMessage handshakeMessage = parser.parseAndProcessHandshakeMessage(ByteBuffer.wrap(message), processor,
				ProtectionKeysType.None);
		return Optional.of(handshakeMessage);
	}

	private Optional<CryptoFrame> getCryptoFrameByOffset(long requiredOffset) {
		return cryptoFrames.stream().filter(frame -> frame.getOffset() == requiredOffset).findFirst();
	}

	@SneakyThrows
	private boolean refillOneCryptoFrameToNextMessageBuffer(long offset) {
		Optional<CryptoFrame> cryptoFrameOptional = getCryptoFrameByOffset(offset);
		if (cryptoFrameOptional.isPresent()) {
			this.messages.write(cryptoFrameOptional.get().getData());
			return true;
		}
		return false;
	}

	private static class DebugMessageProcessor implements MessageProcessor {
		@Override
		public void received(ClientHello ch, ProtectionKeysType protectedBy) throws TlsProtocolException, IOException {
		}
		@Override
		public void received(ServerHello sh, ProtectionKeysType protectedBy) throws TlsProtocolException, IOException {
		}
		@Override
		public void received(EncryptedExtensions ee, ProtectionKeysType protectedBy)
				throws TlsProtocolException, IOException {
		}
		@Override
		public void received(CertificateMessage cm, ProtectionKeysType protectedBy)
				throws TlsProtocolException, IOException {
		}
		@Override
		public void received(CertificateVerifyMessage cv, ProtectionKeysType protectedBy)
				throws TlsProtocolException, IOException {
		}
		@Override
		public void received(FinishedMessage fm, ProtectionKeysType protectedBy)
				throws TlsProtocolException, IOException {
		}
		@Override
		public void received(NewSessionTicketMessage nst, ProtectionKeysType protectedBy)
				throws TlsProtocolException, IOException {
		}
		@Override
		public void received(CertificateRequestMessage cr, ProtectionKeysType protectedBy)
				throws TlsProtocolException, IOException {
		}
	}

}
