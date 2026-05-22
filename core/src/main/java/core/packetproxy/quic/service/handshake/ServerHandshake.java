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

import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceHandshake;
import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceInitial;
import static packetproxy.quic.utils.Constants.TOKEN_SIZE;
import static packetproxy.util.Logging.err;
import static packetproxy.util.Throwing.rethrow;

import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import net.luminis.tls.*;
import net.luminis.tls.extension.ApplicationLayerProtocolNegotiationExtension;
import net.luminis.tls.extension.Extension;
import net.luminis.tls.extension.ServerNameExtension;
import net.luminis.tls.handshake.*;
import packetproxy.CertCacheManager;
import packetproxy.model.CAs.CA;
import packetproxy.quic.service.connection.Connection;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.service.framegenerator.MessagesToCryptoFrames;
import packetproxy.quic.service.transportparameter.TransportParameters;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.Token;
import packetproxy.quic.value.frame.NewConnectionIdFrame;

public class ServerHandshake implements Handshake {

	private static final TlsSessionRegistry tlsSessionRegistry = new TlsSessionRegistryImpl();

	final Connection conn;
	final BlockingQueue<String> sniQueue = new LinkedBlockingQueue<>();
	final CA ca;
	final TransportParameters clientTransportParams = new TransportParameters(Constants.Role.CLIENT);
	Optional<String> sniName = Optional.empty();
	TlsServerEngine engine;

	public ServerHandshake(Connection conn, CA ca) {
		this.conn = conn;
		this.ca = ca;
	}

	public void startHandshake(String sniName) throws Exception {
		KeyStore ks = CertCacheManager.getInstance().getKeyStore(sniName, new String[]{sniName}, this.ca);
		RSAPrivateKey key = (RSAPrivateKey) ks.getKey("newalias", "testtest".toCharArray());
		List<X509Certificate> certs = Arrays.stream(ks.getCertificateChain("newalias"))
				.map(cert -> (X509Certificate) cert).collect(Collectors.toList());
		this.engine = new TlsServerEngine(certs, key, new MyServerMessageSender(), new MyTlsStatusEventHandler(),
				tlsSessionRegistry);
		this.engine.addSupportedCiphers(List.of(TlsConstants.CipherSuite.TLS_AES_128_GCM_SHA256));
	}

	@Override
	public void received(Message message) throws Exception {
		if (message instanceof ClientHello) {

			/* SNIを取得 */
			ClientHello ch = (ClientHello) message;
			ch.getExtensions().stream().filter(ext -> ext instanceof ServerNameExtension).findFirst()
					.ifPresent(rethrow(ext -> {
						ServerNameExtension serverNameExtension = (ServerNameExtension) ext;
						sniName = Optional.of(serverNameExtension.getHostName());
					}));
			if (sniName.isEmpty()) {

				throw new Exception("Error: SNI name was not found in TLS ClientHello HandShake message");
			}
			sniName.ifPresent(rethrow(sni -> {
				this.startHandshake(sni);
				this.sniQueue.put(sni);
				this.engine.received((ClientHello) message, ProtectionKeysType.None);
			}));

		} else if (message instanceof EncryptedExtensions) {

			/* do nothing */
		} else if (message instanceof FinishedMessage) {

			this.engine.received((FinishedMessage) message, ProtectionKeysType.Handshake);
		} else {

			err("Error: cannot process message %s", message);
		}
	}

	public String getSNI() throws Exception {
		return this.sniQueue.take();
	}

	class MyServerMessageSender implements ServerMessageSender {

		@Override
		public void send(ServerHello serverHello) throws IOException {
			MessagesToCryptoFrames stream = conn.getPnSpace(Constants.PnSpaceType.PnSpaceInitial)
					.getMsgToFrameCryptoStream();
			stream.write(serverHello);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(Constants.PnSpaceType.PnSpaceInitial).addSendFrames(frames);
			conn.getKeys().setClientRandom(serverHello.getRandom());
		}

		@Override
		public void send(EncryptedExtensions encryptedExtensions) throws IOException {
			MessagesToCryptoFrames stream = conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake)
					.getMsgToFrameCryptoStream();
			stream.write(encryptedExtensions);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake).addSendFrames(frames);
		}

		@Override
		public void send(CertificateMessage certificateMessage) throws IOException {
			MessagesToCryptoFrames stream = conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake)
					.getMsgToFrameCryptoStream();
			stream.write(certificateMessage);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake).addSendFrames(frames);
		}

		@Override
		public void send(CertificateVerifyMessage certificateVerifyMessage) throws IOException {
			MessagesToCryptoFrames stream = conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake)
					.getMsgToFrameCryptoStream();
			stream.write(certificateVerifyMessage);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake).addSendFrames(frames);
		}

		@Override
		public void send(FinishedMessage finishedMessage) throws IOException {
			MessagesToCryptoFrames stream = conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake)
					.getMsgToFrameCryptoStream();
			stream.write(finishedMessage);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(Constants.PnSpaceType.PnSpaceHandshake).addSendFrames(frames);
		}

		@Override
		public void send(NewSessionTicketMessage ticket) throws IOException {
			/* send CryptFrame(NewSessionTicketMassage) */
			MessagesToCryptoFrames stream = conn.getPnSpace(Constants.PnSpaceType.PnSpaceApplicationData)
					.getMsgToFrameCryptoStream();
			stream.write(ticket);
			Frames frames = stream.toCryptoFrames();
			conn.getPnSpace(Constants.PnSpaceType.PnSpaceApplicationData).addSendFrames(frames);

			/* send NewConnectionIdFrame */
			/* 現在のところ、ActiveConnIdLimit=2(default)を想定して、新しいコネクションIDを1つだけ返す。 (Todo: ClientのActiveConnIdLimit値に応じて返す) */
			NewConnectionIdFrame frame1 = new NewConnectionIdFrame(1, 0, ConnectionId.generateRandom(),
					Token.generateRandom(TOKEN_SIZE));
			conn.getPnSpace(Constants.PnSpaceType.PnSpaceApplicationData).addSendFrames(Frames.of(frame1));
		}
	}

	class MyTlsStatusEventHandler implements TlsStatusEventHandler {

		@Override
		public void earlySecretsKnown() {
			// Logging.log("earlySecretsKnown");
			conn.getKeys().computeZeroRttKey(engine.getClientEarlyTrafficSecret());
		}

		@Override
		public void handshakeSecretsKnown() {
			// Logging.log("handshakeSecretsKnown");
			conn.getKeys().computeHandshakeKey(engine.getClientHandshakeTrafficSecret(),
					engine.getServerHandshakeTrafficSecret());
		}

		@Override
		public void handshakeFinished() {
			// Logging.log("handshakeFinished");
			conn.getKeys().computeApplicationKey(engine.getClientApplicationTrafficSecret(),
					engine.getServerApplicationTrafficSecret());
			conn.getKeys().discardInitialKey();
			conn.getPnSpace(PnSpaceInitial).close();
			conn.getKeys().discardHandshakeKey();
			conn.getPnSpace(PnSpaceHandshake).close();
		}

		@Override
		public void newSessionTicketReceived(NewSessionTicket ticket) {
			// Logging.log("newSessionTicketReceived");
		}

		@Override
		public void extensionsReceived(List<Extension> extensions) throws TlsProtocolException {
			/* use always h3 mode */
			engine.addServerExtensions(new ApplicationLayerProtocolNegotiationExtension("h3"));

			/* quic transport parameter */
			TransportParameters tp = new TransportParameters(Constants.Role.SERVER);
			tp.setMaxUdpPayloadSize(1472);
			tp.setAckDelayExponent(10);
			tp.setMaxIdleTimeout(30_000);
			tp.setOldMinAckDelay(25000);
			tp.setOrigDestConnId(conn.getInitialSecret().getBytes());
			tp.setInitSrcConnId(conn.getConnIdPair().getSrcConnId().getBytes());
			tp.setInitMaxStreamUni(10 * 1024);
			tp.setInitMaxStreamBidi(10 * 1024);
			tp.setInitMaxData(10L * 1024 * 1024 * 1024);
			tp.setInitMaxStreamDataBidiLocal(10L * 1024 * 1024 * 1024);
			tp.setInitMaxStreamDataBidiRemote(10L * 1024 * 1024 * 1024);
			tp.setInitMaxStreamDataUni(10L * 1024 * 1024 * 1024);
			tp.setActiveConnIdLimit(2);
			tp.setDisableActiveMigration(true);
			// if (retryRequired) {
			// tp.setRetrySrcConnId();
			// }
			engine.addServerExtensions(tp);
		}

		@Override
		public boolean isEarlyDataAccepted() {
			// Logging.log("isEarlyDataAccepted");
			return false;
		}
	}
}
