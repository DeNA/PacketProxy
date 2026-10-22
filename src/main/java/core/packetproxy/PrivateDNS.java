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
package packetproxy;

import static packetproxy.util.Logging.err;
import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import packetproxy.model.ConfigBoolean;
import packetproxy.model.ConfigInteger;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class PrivateDNS {

	static int BUFSIZE = 1024;
	static int DEFAULT_PORT = 53;
	private static PrivateDNS instance;
	private ConfigBoolean state;
	private PrivateDNSImp dns;
	private Servers servers;
	private Object lock;
	private SpoofAddrFactory spoofAddrFactry = new SpoofAddrFactory();

	class SpoofAddrFactory {

		private List<SubnetInfo> subnets = new ArrayList<SubnetInfo>();
		private Map<Integer, Inet6Address> ifscopes = new HashMap<>();
		private String defaultAddr = null;
		private Inet6Address defaultAddr6 = null;

		SpoofAddrFactory() throws Exception {
			Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
			for (NetworkInterface netint : Collections.list(nets)) {

				for (InterfaceAddress intAddress : netint.getInterfaceAddresses()) {

					InetAddress addr = intAddress.getAddress();
					if (addr instanceof Inet4Address) {

						short length = intAddress.getNetworkPrefixLength();
						if (length < 0)
							continue;
						String cidr = String.format("%s/%d", addr.getHostAddress(), length);
						SubnetUtils subnet = new SubnetUtils(cidr);
						subnets.add(subnet.getInfo());
						if (defaultAddr == null) {

							defaultAddr = addr.getHostAddress();
						} else if (defaultAddr.equals("127.0.0.1")) {

							defaultAddr = addr.getHostAddress();
						}
					} else {

						if (!addr.isMulticastAddress() && !addr.isLinkLocalAddress() && !addr.isSiteLocalAddress()) {

							ifscopes.put(((Inet6Address) addr).getScopeId(), (Inet6Address) addr);
							if (defaultAddr6 == null) {

								defaultAddr6 = (Inet6Address) addr;
							} else if (defaultAddr6.isLoopbackAddress()) {

								defaultAddr6 = (Inet6Address) addr;
							}
						}
					}
				}
			}
		}

		Map<Integer, String> getSpoofAddr(InetAddress addr) {
			Map<Integer, String> spoofAddrs = new HashMap<>();
			if (addr instanceof Inet4Address) {

				for (SubnetInfo subnet : subnets) {

					if (subnet.isInRange(addr.getHostAddress())) {

						spoofAddrs.put(4, subnet.getAddress());
					}
				}
				if (spoofAddrs.get(4) == null) {

					spoofAddrs.put(4, defaultAddr);
				}
				spoofAddrs.put(6, hostAddress(defaultAddr6));
			} else {

				if (ifscopes.containsKey(((Inet6Address) addr).getScopeId())) {

					spoofAddrs.put(6, ifscopes.get(((Inet6Address) addr).getScopeId()).getHostAddress());
				} else {

					spoofAddrs.put(6, hostAddress(defaultAddr6));
				}
				spoofAddrs.put(4, defaultAddr);
			}
			return spoofAddrs;
		}

		/*
		 * 起動時のフィルタリングで IPv6 アドレスが 1 つも残らなかった場合、
		 * defaultAddr6 は null のままになる。ループバックの ::1 は通常は残るため
		 * 既定の設定では起きないが、-Djava.net.preferIPv4Stack=true を付けた場合や
		 * OS 側で IPv6 を無効化した場合には起きる。
		 *
		 * ここで null を参照すると、NullPointerException が run() の汎用 catch まで
		 * 伝播して finish() が呼ばれ、最初の 1 問い合わせで DNS サーバーが停止する。
		 * 戻り値の null は書き換え先が無いことを表し、
		 * PrivateDnsResponseBuilder が回答レコードを足さずに応答を組み立てる。
		 */
		private String hostAddress(InetAddress addr) {
			return addr == null ? null : addr.getHostAddress();
		}
	}

	public static PrivateDNS getInstance() throws Exception {
		if (instance == null) {

			instance = new PrivateDNS();
		}
		return instance;
	}

	private PrivateDNS() throws Exception {
		lock = new Object();
		state = new ConfigBoolean("PrivateDNS");
		servers = Servers.getInstance();
		dns = null;
	}

	public boolean isRunning() throws Exception {
		return state.getState();
	}

	public boolean start(DNSSpoofingIPGetter dnsSpoofingIPGetter) {
		synchronized (lock) {
			if (dns == null) {

				try {

					dns = new PrivateDNSImp(dnsSpoofingIPGetter);
					if (dns.isRunning()) {

						dns.start();
						state.setState(true);
					} else {

						dns = null;
						state.setState(false);
						return false;
					}
				} catch (Exception e) {

					errWithStackTrace(e);
					dns = null;
					try {
						state.setState(false);
					} catch (Exception ignored) {
					}
					return false;
				}
			}
		}
		return true;
	}

	public boolean restart(DNSSpoofingIPGetter dnsSpoofingIPGetter) {
		synchronized (lock) {
			if (dns != null) {

				dns.finish();
				dns = null;
			}

			try {

				dns = new PrivateDNSImp(dnsSpoofingIPGetter);
				if (dns.isRunning()) {

					dns.start();
					state.setState(true);
				} else {

					dns = null;
					state.setState(false);
					return false;
				}
			} catch (Exception e) {

				errWithStackTrace(e);
				dns = null;
				try {
					state.setState(false);
				} catch (Exception ignored) {
				}
				return false;
			}
		}
		return true;
	}

	public int getConfiguredPort() {
		return getListenPort();
	}

	public boolean isPortChangeNeeded(int port) {
		if (!isValidPort(port)) {
			return false;
		}
		return port != getListenPort();
	}

	public boolean isPortInRange(int port) {
		return isValidPort(port);
	}

	public void setPort(int port, DNSSpoofingIPGetter dnsSpoofingIPGetter) {
		if (!isValidPort(port)) {
			return;
		}
		synchronized (lock) {
			try {
				ConfigInteger portConfig = new ConfigInteger("PrivateDNSPort");
				int currentPort = portConfig.getInteger();
				if (currentPort != port) {
					log("Private DNS port changed: %d -> %d", currentPort, port);
					portConfig.setInteger(port);
				}
			} catch (Exception e) {
				errWithStackTrace(e);
				return;
			}
		}

		if (dnsSpoofingIPGetter == null) {
			return;
		}

		try {
			if (isRunning() && !restart(dnsSpoofingIPGetter)) {
				state.setState(false);
			}
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	public void stop() {
		synchronized (lock) {
			if (dns != null) {

				dns.finish();
				dns = null;
				try {

					state.setState(false);
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		}
	}

	private class PrivateDNSImp extends Thread {

		private DNSSpoofingIPGetter spoofingIp;
		private final int listenPort;

		private List<DatagramSocket> sockets = List.of();
		private final AtomicBoolean closed = new AtomicBoolean();

		public PrivateDNSImp(DNSSpoofingIPGetter dnsSpoofingIPGetter) throws Exception {
			this.spoofingIp = dnsSpoofingIPGetter;
			listenPort = getListenPort();

			InetAddress bindAddr = InetAddress.getByName(spoofingIp.getInt());
			try {

				if (bindAddr.isAnyLocalAddress()) {

					sockets = listenOnWildcard();
				} else if (bindAddr instanceof Inet4Address) {

					sockets = List.of(open(StandardProtocolFamily.INET, new InetSocketAddress(bindAddr, listenPort)));
				} else {

					sockets = List.of(open(StandardProtocolFamily.INET6, new InetSocketAddress(bindAddr, listenPort)));
				}
			} catch (Exception e) {

				err("cannot boot private DNS server (permission issue or already listened): addr=%s port=%d",
						spoofingIp.getInt(), listenPort);
			}
		}

		/*
		 * ワイルドカードで待ち受ける場合は、IPv4 と IPv6 でソケットを分けたい。
		 * デュアルスタックの 1 本で待ち受けると、IPv4 クライアントへ送った応答が
		 * OS に落とされる環境があるため (macOS で確認。送信はエラーにならない)。
		 *
		 * ただし DatagramChannel.open(INET6) は IPV6_V6ONLY を立てないため、
		 * 同一ポートを IPv4 と共有できるかはプラットフォーム依存になる。
		 * 分けられない環境では従来どおり 1 本のデュアルスタックソケットに戻す。
		 */
		private List<DatagramSocket> listenOnWildcard() throws Exception {
			DatagramSocket v4 = open(StandardProtocolFamily.INET, new InetSocketAddress("0.0.0.0", listenPort));
			try {

				DatagramSocket v6 = open(StandardProtocolFamily.INET6, new InetSocketAddress("::", listenPort));
				return List.of(v4, v6);
			} catch (UnsupportedOperationException e) {

				/* IPv6 が無効な JVM や OS では IPv4 だけで待ち受ける */
				log("private DNS server listens on IPv4 only (%s)", e.getMessage());
				return List.of(v4);
			} catch (Exception e) {

				/* ファミリ間でポートを共有できないので、1 本のデュアルスタックに戻す */
				log("private DNS server listens on a single dual-stack socket (%s)", e.getMessage());
				v4.close();
			}
			return List.of(open(StandardProtocolFamily.INET6, new InetSocketAddress("::", listenPort)));
		}

		/* bind に失敗した channel は呼び出し元に渡らないので、ここで閉じる */
		private DatagramSocket open(ProtocolFamily family, InetSocketAddress addr) throws Exception {
			DatagramChannel channel = DatagramChannel.open(family);
			try {

				channel.bind(addr);
			} catch (Exception e) {

				channel.close();
				throw e;
			}
			return channel.socket();
		}

		public boolean isRunning() {
			return !sockets.isEmpty() && !closed.get();
		}

		/*
		 * 停止側とワーカーの双方から呼ばれるので、一覧は変更せず一度だけ閉じる。
		 * 後続の呼び出しが close の完了を待たずに戻ると、restart() が旧ソケットと
		 * ポートを争うため synchronized にしている。
		 */
		public synchronized void finish() {
			if (closed.compareAndSet(false, true)) {

				for (DatagramSocket soc : sockets) {

					soc.close();
				}
			}
		}

		public void run() {
			log("Private DNS Server started. (addr=%s port=%d)", spoofingIp.getInt(), listenPort);
			List<Thread> workers = new ArrayList<>();
			for (DatagramSocket soc : sockets) {

				Thread worker = new Thread(() -> serve(soc));
				worker.setDaemon(true);
				worker.start();
				workers.add(worker);
			}
			for (Thread worker : workers) {

				try {

					worker.join();
				} catch (InterruptedException e) {

					return;
				}
			}
		}

		private void serve(DatagramSocket soc) {
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket recvPacket = new DatagramPacket(buf, BUFSIZE);
			while (true) {

				try {

					/* getLength() は直前の受信サイズに縮むので、受信上限を明示しておく */
					recvPacket.setLength(BUFSIZE);
					soc.receive(recvPacket);
					InetAddress cAddr = recvPacket.getAddress();
					int cPort = recvPacket.getPort();

					Map<Integer, String> spoofingIpStrs = new HashMap<>();
					String spoofingIpStr = "";
					String spoofingIp6Str = "";

					// if (cAddr instanceof Inet6Address) {
					// util.packetProxyLog(String.format("[ScopeID] %s",
					// ((Inet6Address)cAddr).getScopeId()));
					// }
					if (spoofingIp.isAuto()) {

						spoofingIpStrs = spoofAddrFactry.getSpoofAddr(cAddr);
						spoofingIpStr = spoofingIpStrs.get(4);
						spoofingIp6Str = spoofingIpStrs.get(6);
					} else {

						spoofingIpStr = spoofingIp.get();
						spoofingIp6Str = spoofingIp.get6();
					}

					// util.packetProxyLog(String.format("[hostAddrStr] %s",
					// cAddr.getHostAddress()));
					// util.packetProxyLog(String.format("[SpoofingIP] %s : %s", spoofingIpStr,
					// spoofingIp6Str));

					byte[] requestData = recvPacket.getData();

					Message smsg = new Message(requestData);
					byte[] smsgBA = smsg.toWire();
					int queryRecType = smsg.getQuestion().getType();
					String queryHostName = smsg.getQuestion().getName().toString(true);
					String queryRecTypeName = Type.string(queryRecType);
					InetAddress addr;
					byte[] res = null;

					try {

						if (queryRecType == Type.A) {

							addr = PrivateDNSClient.getByName(queryHostName);
							if (addr instanceof Inet6Address) {

								throw new UnknownHostException();
							}
						} else if (queryRecType == Type.AAAA) {

							addr = PrivateDNSClient.getByName6(queryHostName);
							if (addr == null) {

								throw new UnknownHostException();
							}
						} else if (queryRecType == Type.HTTPS) {

							log("[DNS Query] '%s' [HTTPS]", queryHostName);
							PrivateDnsResponseBuilder jn;
							if (isTargetHost(queryHostName)) {

								Name label = Name.fromString(queryHostName + ".");
								Name svcDomain = Name.fromString(".");
								HTTPSRecord.ParameterAlpn alpn = new HTTPSRecord.ParameterAlpn();
								alpn.fromString("h1,h2,h3");
								List<HTTPSRecord.ParameterBase> params = List.of(alpn);
								HTTPSRecord record = new HTTPSRecord(label, DClass.IN, 300, 1, svcDomain, params);
								jn = new PrivateDnsResponseBuilder(record);
								log("Force to access '%s' with HTTP3", queryHostName);
							} else {

								Record[] records = PrivateDNSClient.getHTTPSRecord(queryHostName);
								jn = new PrivateDnsResponseBuilder(records);
							}
							res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);
							soc.send(new DatagramPacket(res, res.length, cAddr, cPort));
							continue;
						} else {

							log("[DNS Query] Unsupported Query Type: '%s' [%s]", queryHostName, queryRecTypeName);
							throw new UnsupportedOperationException();
						}

						String ip = addr.getHostAddress();

						log("[DNS Query] '%s' [%s]", queryHostName, queryRecTypeName);
						// log(String.format("[DNS Response Address] '%s'", ip));

						if (isTargetHost(queryHostName)) {

							if (queryRecType == Type.A) {

								// ToDo GUIにIPv4有効チェックを追加し、無効のときはスキップするようにする。
								ip = spoofingIpStr;
								log("Replaced to %s", ip);
							}
						}
						if (isTargetHost6(queryHostName)) {

							if (queryRecType == Type.AAAA) {

								/*
								 * IPv6 の書き換え先が無い場合は null になる。
								 * PrivateDnsResponseBuilder は回答レコードを足さず、
								 * NOERROR で回答なしの応答を返す。
								 */
								// ToDo GUIにIPv6有効チェックを追加し、無効のときはスキップするようにする。
								ip = spoofingIp6Str;
								log("Replaced to %s", ip);
							}
						}
						PrivateDnsResponseBuilder jn = new PrivateDnsResponseBuilder(ip);
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);

					} catch (UnknownHostException e) {

						err("[DNS Query] Unknown Host: '%s' [%s]", queryHostName, queryRecTypeName);
						PrivateDnsResponseBuilder jn = new PrivateDnsResponseBuilder();
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);

					} catch (UnsupportedOperationException e) {

						// Not implemented yet
						PrivateDnsResponseBuilder jn = new PrivateDnsResponseBuilder();
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);

					} catch (Exception e) {

						err("[DNS Query] Unknown Error: '%s' [%s]", queryHostName, queryRecTypeName);
						PrivateDnsResponseBuilder jn = new PrivateDnsResponseBuilder();
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);
					}
					soc.send(new DatagramPacket(res, res.length, cAddr, cPort));
				} catch (SocketException e) {
					if (closed.get() || soc.isClosed()) {

						return;
					}
					errWithStackTrace(e);
					finish();
					return;
				} catch (IOException e) {

					errWithStackTrace(e);
				} catch (Exception e) {

					errWithStackTrace(e);
					finish();
					return;
				}
			}
		}

		private boolean isTargetHost(String hostName) throws Exception {
			List<Server> server_list = servers.queryResolvedByDNS();
			for (Server server : server_list) {

				if (hostName.equals(server.getIp())) {

					return true;
				}
			}
			return false;
		}

		private boolean isTargetHost6(String hostName) throws Exception {
			List<Server> server_list = servers.queryResolvedByDNS6();
			for (Server server : server_list) {

				if (hostName.equals(server.getIp())) {

					return true;
				}
			}
			return false;
		}
	}

	private int getListenPort() {
		try {
			ConfigInteger portConfig = new ConfigInteger("PrivateDNSPort");
			int port = portConfig.getInteger();
			if (isValidPort(port)) {
				return port;
			}
			portConfig.setInteger(DEFAULT_PORT);
			return DEFAULT_PORT;
		} catch (Exception e) {
			errWithStackTrace(e);
			return DEFAULT_PORT;
		}
	}

	private boolean isValidPort(int port) {
		return 1 <= port && port <= 65535;
	}
}
