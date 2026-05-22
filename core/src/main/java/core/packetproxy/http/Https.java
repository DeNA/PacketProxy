/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.http;

import static packetproxy.util.Logging.errWithStackTrace;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import packetproxy.CertCacheManager;
import packetproxy.common.ClientKeyManager;
import packetproxy.common.Utils;
import packetproxy.model.CAs.CA;
import packetproxy.model.ConfigString;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class Https {

	private static final char[] KS_PASS = "testtest".toCharArray();

	public static SSLContext createSSLContext(String commonName, CA ca) throws Exception {
		SSLContext sslContext = SSLContext.getInstance("TLS");
		String[] domainNames = Servers.getInstance().queryResolvedByDNS().stream().map(a -> a.getIp())
				.sorted(String::compareTo).toArray(String[]::new);
		KeyStore ks = CertCacheManager.getInstance().getKeyStore(commonName, domainNames, ca);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, KS_PASS);
		sslContext.init(kmf.getKeyManagers(), null, null);
		return sslContext;
	}

	public static ServerSocket createServerSSLSocket(int listen_port, String commonName, CA ca) throws Exception {
		SSLContext sslContext = createSSLContext(commonName, ca);
		ServerSocketFactory ssf = sslContext.getServerSocketFactory();
		return (SSLServerSocket) ssf.createServerSocket(listen_port);
	}

	public static ServerSocket createServerSSLSocket(int listen_port, String commonName, CA ca,
			String ApplicationProtocol) throws Exception {
		SSLServerSocket serverSocket = (SSLServerSocket) createServerSSLSocket(listen_port, commonName, ca);
		SSLParameters sslp = serverSocket.getSSLParameters();
		String[] serverAPs = {ApplicationProtocol};
		sslp.setApplicationProtocols(serverAPs);
		serverSocket.setSSLParameters(sslp);
		return serverSocket;
	}

	public static SSLSocket[] createBothSideSSLSockets(Socket clientSocket, InputStream lookahead,
			InetSocketAddress serverAddr, InetSocketAddress proxyAddr, String serverName, CA ca) throws Exception {
		SSLSocket clientSSLSocket = (SSLSocket) createSSLContext(serverName, ca).getSocketFactory()
				.createSocket(clientSocket, lookahead, true);
		clientSSLSocket.setUseClientMode(false);

		Server server = Servers.getInstance().queryByAddress(serverAddr);
		clientKeyManagers = ClientKeyManager.getKeyManagers(server);
		SSLSocket[] serverSSLSocket = new SSLSocket[1];
		clientSSLSocket.setHandshakeApplicationProtocolSelector((clientSocketParam, clientProtocols) -> {
			try {

				Socket serverSocket;
				if (proxyAddr != null) {

					serverSocket = new Socket(proxyAddr.getAddress(), proxyAddr.getPort());
					OutputStream proxyOut = serverSocket.getOutputStream();
					InputStream proxyIn = serverSocket.getInputStream();
					proxyOut.write(String.format("CONNECT %s:%d HTTP/1.1\r\nHost: %s\r\n\r\n",
							serverAddr.getHostString(), serverAddr.getPort(), serverAddr.getHostString()).getBytes());
					proxyOut.flush();
					int length = 0;
					byte[] input_data = new byte[1024];
					while ((length = proxyIn.read(input_data, 0, input_data.length)) != -1) {

						if ((Utils.indexOf(input_data, 0, length, "\r\n\r\n".getBytes())) >= 0) {

							break;
						}
					}
				} else {

					serverSocket = new Socket(serverAddr.getAddress(), serverAddr.getPort());
				}
				serverSSLSocket[0] = (SSLSocket) createSSLSocketFactory().createSocket(serverSocket, null, true);
				serverSSLSocket[0].setUseClientMode(true);
				SSLParameters sp = serverSSLSocket[0].getSSLParameters();

				List<String> alpns = new LinkedList<>(clientProtocols);
				if (new ConfigString("PriorityOrderOfHttpVersions").getString().equals("HTTP1")) {

					if (alpns.contains("http/1.1") || alpns.contains("http/1.0")) {

						alpns.remove("h2");
						alpns.remove("grpc");
						alpns.remove("grpc-exp");
					}
				}
				sp.setApplicationProtocols(alpns.toArray(new String[alpns.size()]));

				serverSSLSocket[0].setSSLParameters(sp);
				serverSSLSocket[0].startHandshake();
			} catch (Exception e) {

				errWithStackTrace(e);
			}
			return serverSSLSocket[0].getApplicationProtocol();
		});

		clientSSLSocket.startHandshake();

		/* case: ALPN is not supported */
		if (serverSSLSocket[0] == null) {

			// Logging.log("ALPN is not supported: " + serverName);
			Socket serverSocket;
			if (proxyAddr != null) {

				serverSocket = new Socket(proxyAddr.getAddress(), proxyAddr.getPort());
				OutputStream proxyOut = serverSocket.getOutputStream();
				InputStream proxyIn = serverSocket.getInputStream();
				proxyOut.write(String.format("CONNECT %s:%d HTTP/1.1\r\nHost: %s\r\n\r\n", serverAddr.getHostString(),
						serverAddr.getPort(), serverAddr.getHostString()).getBytes());
				proxyOut.flush();
				int length = 0;
				byte[] input_data = new byte[1024];
				while ((length = proxyIn.read(input_data, 0, input_data.length)) != -1) {

					if ((Utils.indexOf(input_data, 0, length, "\r\n\r\n".getBytes())) >= 0) {

						break;
					}
				}
			} else {

				serverSocket = new Socket(serverAddr.getAddress(), serverAddr.getPort());
			}
			serverSSLSocket[0] = (SSLSocket) createSSLSocketFactory().createSocket(serverSocket, null, true);
			serverSSLSocket[0].setUseClientMode(true);
			serverSSLSocket[0].startHandshake();
		}

		return new SSLSocket[]{clientSSLSocket, serverSSLSocket[0]};
	}

	public static SSLSocket convertToServerSSLSocket(Socket socket, String commonName, CA ca, InputStream is)
			throws Exception {
		SSLContext sslContext = createSSLContext(commonName, ca);
		SSLSocketFactory ssf = sslContext.getSocketFactory();
		SSLSocket ssl_socket = (SSLSocket) ssf.createSocket(socket, is, true);
		ssl_socket.setUseClientMode(false);

		SSLParameters sslp = ssl_socket.getSSLParameters();
		String[] serverAPs = {"http/1.1", "http/1.0"};
		sslp.setApplicationProtocols(serverAPs);
		ssl_socket.setSSLParameters(sslp);

		ssl_socket.startHandshake();
		return ssl_socket;
	}

	public static SSLSocketFactory createSSLSocketFactory() throws Exception {
		SSLContext sslContext = SSLContext.getInstance("TLS");
		X509TrustManager[] trustManagers = {new X509TrustManager() {

			public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
			}

			public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
			}

			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[]{};
			}
		}};
		sslContext.init(clientKeyManagers, trustManagers, new SecureRandom());
		return (SSLSocketFactory) sslContext.getSocketFactory();
	}

	private static KeyManager[] clientKeyManagers = {new X509KeyManager() {

		@Override
		public String[] getClientAliases(String s, Principal[] principals) {
			return new String[0];
		}

		@Override
		public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
			return null;
		}

		@Override
		public String[] getServerAliases(String s, Principal[] principals) {
			return new String[0];
		}

		@Override
		public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
			return null;
		}

		@Override
		public X509Certificate[] getCertificateChain(String s) {
			return new X509Certificate[0];
		}

		@Override
		public PrivateKey getPrivateKey(String s) {
			return null;
		}
	}};

	public static SSLSocket convertToClientSSLSocket(Socket socket, String alpn) throws Exception {
		SSLSocketFactory ssf = createSSLSocketFactory();
		SSLSocket sock = (SSLSocket) ssf.createSocket(socket, null, socket.getPort(), false);
		SSLParameters sslp = sock.getSSLParameters();
		String[] clientAPs;
		if (alpn != null && alpn.length() > 0) {

			clientAPs = new String[]{alpn};
		} else {

			clientAPs = new String[]{"h2", "http/1.1", "http/1.0"};
		}
		sslp.setApplicationProtocols(clientAPs);
		sock.setSSLParameters(sslp);
		sock.startHandshake();
		return sock;
	}

	public static SSLSocket createClientSSLSocket(InetSocketAddress addr, String alpn) throws Exception {
		SSLSocketFactory ssf = createSSLSocketFactory();
		SSLSocket sock = (SSLSocket) ssf.createSocket(addr.getAddress(), addr.getPort());
		SSLParameters sslp = sock.getSSLParameters();
		String[] clientAPs;
		if (alpn != null && alpn.length() > 0) {

			clientAPs = new String[]{alpn};
		} else {

			clientAPs = new String[]{"h2", "http/1.1", "http/1.0"};
		}
		sslp.setApplicationProtocols(clientAPs);
		sock.setSSLParameters(sslp);
		sock.startHandshake();
		return sock;
	}

	public static SSLSocket createClientSSLSocket(InetSocketAddress addr, String SNIServerName, String alpn)
			throws Exception {
		/* SNI */
		SNIHostName serverName = new SNIHostName(SNIServerName);
		/* Fetch Client Certificate from ClientKeyManager */
		Server server = Servers.getInstance().queryByAddress(addr);
		clientKeyManagers = ClientKeyManager.getKeyManagers(server);

		SSLSocketFactory ssf = createSSLSocketFactory();
		SSLSocket sock = (SSLSocket) ssf.createSocket(addr.getAddress(), addr.getPort());
		SSLParameters sslp = sock.getSSLParameters();
		String[] clientAPs;
		if (alpn != null && alpn.length() > 0) {

			clientAPs = new String[]{alpn};
		} else {

			clientAPs = new String[]{"h2", "http/1.1", "http/1.0"};
		}
		sslp.setApplicationProtocols(clientAPs);

		sock.setSSLParameters(sslp);
		List<SNIServerName> serverNames = new ArrayList<>();
		serverNames.add(serverName);
		SSLParameters params = sock.getSSLParameters();
		params.setServerNames(serverNames);
		sock.setSSLParameters(params);
		sock.startHandshake();
		return sock;
	}

	public static String getCommonName(InetSocketAddress addr) throws Exception {
		SSLSocketFactory ssf = createSSLSocketFactory();
		SSLSocket socket = (SSLSocket) ssf.createSocket(addr.getAddress(), addr.getPort());
		socket.startHandshake();
		SSLSession session = socket.getSession();
		X509Certificate[] servercerts = (X509Certificate[]) session.getPeerCertificates();

		Pattern pattern = Pattern.compile("CN=([^,]+)", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(servercerts[0].getSubjectDN().getName());
		if (matcher.find()) {

			return matcher.group(1);
		}
		return "";
	}
}
