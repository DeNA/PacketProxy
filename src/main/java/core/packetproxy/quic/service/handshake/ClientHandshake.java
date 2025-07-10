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

package packetproxy.quic.service.handshake;

import static packetproxy.quic.service.handshake.HandshakeState.State.*;
import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceHandshake;
import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceInitial;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.X509TrustManager;
import net.luminis.tls.*;
import net.luminis.tls.extension.ApplicationLayerProtocolNegotiationExtension;
import net.luminis.tls.extension.Extension;
import net.luminis.tls.handshake.*;
import packetproxy.quic.service.connection.Connection;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.service.framegenerator.MessagesToCryptoFrames;
import packetproxy.quic.service.transportparameter.TransportParameters;
import packetproxy.quic.utils.Constants;

public class ClientHandshake implements Handshake {

	private final Connection conn;
	private final TlsClientEngine engine;

	public ClientHandshake(Connection conn) {
		this.conn = conn;
		this.engine = new TlsClientEngine(new MyClientMessageSender(), new MyClientTlsStatusEventHandler());
		this.engine.addSupportedCiphers(List.of(TlsConstants.CipherSuite.TLS_AES_128_GCM_SHA256));
		this.engine.add(new ApplicationLayerProtocolNegotiationExtension("h3"));
		this.engine.setTrustManager(new X509TrustManager() {

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		});
		this.engine.setHostnameVerifier(new HostnameVerifier() {

			@Override
			public boolean verify(String s, X509Certificate x509Certificate) {
				return true;
			}
		});
		TransportParameters tp = new TransportParameters(Constants.Role.CLIENT);
		tp.setInitSrcConnId(this.conn.getConnIdPair().getSrcConnId().getBytes());
		tp.setMaxUdpPayloadSize(1472);
		tp.setAckDelayExponent(10);
		tp.setMaxIdleTimeout(30_000);
		tp.setOldMinAckDelay(25_000);
		tp.setInitMaxStreamUni(10 * 1024);
		tp.setInitMaxStreamBidi(10 * 1024);
		tp.setInitMaxData(10L * 1024 * 1024 * 1024);
		tp.setInitMaxStreamDataBidiLocal(10L * 1024 * 1024 * 1024);
		tp.setInitMaxStreamDataBidiRemote(10L * 1024 * 1024 * 1024);
		tp.setInitMaxStreamDataUni(10L * 1024 * 1024 * 1024);
		tp.setActiveConnIdLimit(4);
		this.engine.add(tp);
	}

	@Override
	public void received(Message message) throws Exception {
		if (message instanceof ServerHello) {

			this.engine.received((ServerHello) message, ProtectionKeysType.None);
		} else if (message instanceof EncryptedExtensions) {

			this.engine.received((EncryptedExtensions) message, ProtectionKeysType.Handshake);
		} else if (message instanceof CertificateMessage) {

			this.engine.received((CertificateMessage) message, ProtectionKeysType.Handshake);
		} else if (message instanceof CertificateVerifyMessage) {

			this.engine.received((CertificateVerifyMessage) message, ProtectionKeysType.Handshake);
		} else if (message instanceof FinishedMessage) {

			this.engine.received((FinishedMessage) message, ProtectionKeysType.Handshake);
		} else if (message instanceof NewSessionTicketMessage) {

			this.engine.received((NewSessionTicketMessage) message, ProtectionKeysType.Application);
		} else {

			System.err.println("Error: couldn't process message " + message);
		}
	}

	public void start(String serverName) throws Exception {
		this.engine.setServerName(serverName);
		this.engine.startHandshake();
	}

	class MyClientMessageSender implements ClientMessageSender {

		@Override
		public void send(ClientHello clientHello) throws IOException {
			MessagesToCryptoFrames stream = conn.getPnSpace(PnSpaceInitial).getMsgToFrameCryptoStream();
			stream.write(clientHello);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(PnSpaceInitial).addSendFrames(frames);
			conn.getKeys().setClientRandom(clientHello.getClientRandom());
		}

		@Override
		public void send(FinishedMessage finishedMessage) throws IOException {
			MessagesToCryptoFrames stream = conn.getPnSpace(PnSpaceHandshake).getMsgToFrameCryptoStream();
			stream.write(finishedMessage);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(PnSpaceHandshake).addSendFrames(frames);
			conn.getHandshakeState().transit(Confirmed);
		}

		@Override
		public void send(CertificateMessage certificateMessage) throws IOException {
			/* do nothing */
		}

		@Override
		public void send(CertificateVerifyMessage certificateVerifyMessage) {
			/* do nothing */
		}
	}

	class MyClientTlsStatusEventHandler implements TlsStatusEventHandler {

		@Override
		public void earlySecretsKnown() {
			conn.getKeys().computeZeroRttKey(engine.getClientEarlyTrafficSecret());
		}

		@Override
		public void handshakeSecretsKnown() {
			conn.getKeys().computeHandshakeKey(engine.getClientHandshakeTrafficSecret(),
					engine.getServerHandshakeTrafficSecret());
			conn.getHandshakeState().transit(HasHandshakeKeys);
		}

		@Override
		public void handshakeFinished() {
			conn.getKeys().computeApplicationKey(engine.getClientApplicationTrafficSecret(),
					engine.getServerApplicationTrafficSecret());
			conn.getHandshakeState().transit(HasAppKeys);
		}

		@Override
		public void newSessionTicketReceived(NewSessionTicket ticket) {
		}

		@Override
		public void extensionsReceived(List<Extension> extensions) throws TlsProtocolException {
		}

		@Override
		public boolean isEarlyDataAccepted() {
			return false;
		}
	}

}
