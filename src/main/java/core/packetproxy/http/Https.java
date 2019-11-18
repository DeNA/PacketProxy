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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ServerSocketFactory;
import javax.net.ssl.HttpsURLConnection;
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

import org.msgpack.jackson.dataformat.Tuple;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

import packetproxy.CertCacheManager;
import packetproxy.common.ClientKeyManager;
import packetproxy.model.Server;
import packetproxy.model.Servers;
import packetproxy.model.CAs.CA;

public class Https {
	private static final char[] KS_PASS = "testtest".toCharArray();

	private static SSLContext createSSLContext(String commonName, CA ca) throws Exception {
		SSLContext sslContext = SSLContext.getInstance("TLS");
		String[] domainNames = Servers.getInstance().queryResolvedByDNS().stream().map(a -> a.getIp()).sorted(String::compareTo).toArray(String[]::new);
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

	public static ServerSocket createServerSSLSocket(int listen_port, String commonName, CA ca, String ApplicationProtocol) throws Exception {
		SSLServerSocket serverSocket = (SSLServerSocket)createServerSSLSocket(listen_port, commonName, ca);
		SSLParameters sslp = serverSocket.getSSLParameters();
		String[] serverAPs ={ ApplicationProtocol };
		sslp.setApplicationProtocols(serverAPs);
		serverSocket.setSSLParameters(sslp);
		return serverSocket;
	}
	
	/*
	 * SSLSocket client は Socketから生成
	 * SSLSocket server は InetSocketAddressから生成
	 */
	public static Tuple<SSLSocket, SSLSocket> createClientAndServerSSLSocket(Socket clientSocket, InetSocketAddress serverAddr, String serverName, CA ca) throws Exception {
		SSLContext sslContext = createSSLContext(serverName, ca);
		SSLSocketFactory ssf = sslContext.getSocketFactory();
		SSLSocket clientSSLSocket  = (SSLSocket)ssf.createSocket(clientSocket, null, true);
		clientSSLSocket.setUseClientMode(false);

		SSLSocket[] serverSSLSocket = new SSLSocket[1];

		clientSSLSocket.setHandshakeApplicationProtocolSelector((serverSocket, clientProtocols) -> {
			try {
				serverSSLSocket[0] = (SSLSocket) SSLSocketFactory.getDefault().createSocket(serverAddr.getAddress(), serverAddr.getPort());
				SSLParameters sp = serverSSLSocket[0].getSSLParameters();
				sp.setApplicationProtocols(clientProtocols.toArray(new String[clientProtocols.size()]));
				serverSSLSocket[0].setSSLParameters(sp);
				serverSSLSocket[0].startHandshake();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return serverSSLSocket[0].getApplicationProtocol();
		});

		return new Tuple<SSLSocket, SSLSocket>(clientSSLSocket, serverSSLSocket[0]);
	}

	public static SSLSocket convertToServerSSLSocket(Socket socket, String commonName, CA ca) throws Exception {
		SSLContext sslContext = createSSLContext(commonName, ca);
		SSLSocketFactory ssf = sslContext.getSocketFactory();
		SSLSocket ssl_socket  = (SSLSocket)ssf.createSocket(socket, null, true);
		ssl_socket.setUseClientMode(false);

		SSLParameters sslp = ssl_socket.getSSLParameters();
		String[] serverAPs ={ "h2", "http/1.1", "http/1.0" };
		sslp.setApplicationProtocols(serverAPs);
		ssl_socket.setSSLParameters(sslp);
		

		ssl_socket.setHandshakeApplicationProtocolSelector((serverSocket, clientProtocols) -> {
			SSLSession session = serverSocket.getHandshakeSession();
			for (String a: clientProtocols) {
				System.out.println(String.format("hoge: %s", a));
			}
			System.out.println(String.format("hoge1: %s", session.getProtocol()));
			System.out.println(String.format("hoge1: %s", session.getCipherSuite()));
			
			// 接続先サーバにアクセスすることで、実際のALPNを決定する
			String ret = "";
			try {
				SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket(commonName, 443);
				SSLParameters sp = s.getSSLParameters();
				sp.setApplicationProtocols(clientProtocols.toArray(new String[clientProtocols.size()]));
				s.setSSLParameters(sp);
				s.startHandshake();
				ret = s.getApplicationProtocol();
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(String.format("hoge2: %s", ret));
			
			return ret;
		});

		ssl_socket.startHandshake();

		System.out.println(ssl_socket.getApplicationProtocol());
		return ssl_socket;
	}

	public static SSLSocket convertToServerSSLSocket(Socket socket, String commonName, CA ca, InputStream is) throws Exception {
		SSLContext sslContext = createSSLContext(commonName, ca);
		SSLSocketFactory ssf = sslContext.getSocketFactory();
		SSLSocket ssl_socket  = (SSLSocket)ssf.createSocket(socket, is, true);
		ssl_socket.setUseClientMode(false);

		SSLParameters sslp = ssl_socket.getSSLParameters();
		String[] serverAPs ={ "h2", "http/1.1", "http/1.0" };
		sslp.setApplicationProtocols(serverAPs);
		ssl_socket.setSSLParameters(sslp);

		ssl_socket.startHandshake();
		System.out.println(ssl_socket.getApplicationProtocol());
		return ssl_socket;
	}

	private static SSLSocketFactory createSSLSocketFactory() throws Exception {
		SSLContext sslContext = SSLContext.getInstance("TLS");
		X509TrustManager[] trustManagers = { new X509TrustManager() {
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

	private static KeyManager[] clientKeyManagers = { new X509KeyManager() {
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

	public static SSLSocket convertToClientSSLSocket(Socket socket) throws Exception {
		SSLSocketFactory ssf = createSSLSocketFactory();
		SSLSocket sock = (SSLSocket) ssf.createSocket(socket, null, socket.getPort(), false);
		SSLParameters sslp = sock.getSSLParameters();
		String[] clientAPs ={ "h2", "http/1.1", "http/1.0" };
		sslp.setApplicationProtocols(clientAPs);
		sock.setSSLParameters(sslp);
		sock.startHandshake();
		return sock;
	}

	public static SSLSocket createClientSSLSocket(InetSocketAddress addr) throws Exception {
		SSLSocketFactory ssf = createSSLSocketFactory();
		SSLSocket sock = (SSLSocket) ssf.createSocket(addr.getAddress(), addr.getPort());
		SSLParameters sslp = sock.getSSLParameters();
		String[] clientAPs ={ "h2", "http/1.1", "http/1.0" };
		sslp.setApplicationProtocols(clientAPs);
		sock.setSSLParameters(sslp);
		sock.startHandshake();
		return sock;
	}

	public static SSLSocket createClientSSLSocket(InetSocketAddress addr, String SNIServerName) throws Exception {
		/* SNI */
		SNIHostName serverName = new SNIHostName(SNIServerName);
		/* Fetch Client Certificate from ClientKeyManager */
		Server server = Servers.getInstance().queryByAddress(addr);
		clientKeyManagers = ClientKeyManager.getKeyManagers(server);

		SSLSocketFactory ssf = createSSLSocketFactory();
		SSLSocket sock = (SSLSocket) ssf.createSocket(addr.getAddress(), addr.getPort());
		SSLParameters sslp = sock.getSSLParameters();
		String[] clientAPs ={ "h2", "http/1.1", "http/1.0" };
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
		SSLSocketFactory ssf = HttpsURLConnection.getDefaultSSLSocketFactory();
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
