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

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.xbill.DNS.DNSSpoofingIPGetter;
import org.xbill.DNS.Message;
import org.xbill.DNS.Type;
import org.xbill.DNS.jnamed;

import packetproxy.model.ConfigBoolean;
import packetproxy.model.Server;
import packetproxy.model.Servers;
import packetproxy.util.PacketProxyUtility;

public class PrivateDNS
{
	static int BUFSIZE = 1024;
	static int PORT = 53;
	static String dnsServer = "8.8.8.8";
	private static PrivateDNS instance;
	private ConfigBoolean state;
	private PrivateDNSImp dns;
	private Servers servers;
	private Object lock;
	private PacketProxyUtility util;
	private SpoofAddrFactory spoofAddrFactry = new SpoofAddrFactory();
	
	class SpoofAddrFactory {
		private List<SubnetInfo> subnets = new ArrayList<SubnetInfo>();
		private String defaultAddr = null;

		SpoofAddrFactory() throws Exception {
			Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
			for (NetworkInterface netint : Collections.list(nets)) {
				for (InterfaceAddress intAddress : netint.getInterfaceAddresses()) {
					InetAddress addr = intAddress.getAddress();
					if (addr instanceof Inet4Address) {
						short length = intAddress.getNetworkPrefixLength();
						if(length<0)continue;
						String cidr = String.format("%s/%d", addr.getHostAddress(),length);
						SubnetUtils subnet = new SubnetUtils(cidr);
						subnets.add(subnet.getInfo());
						if (defaultAddr == null) {
							defaultAddr = addr.getHostAddress();
						} else if (defaultAddr.equals("127.0.0.1")) {
							defaultAddr = addr.getHostAddress();
						}
					}
				}
			}
		}
		String getSpoofAddr(String addr) {
			for (SubnetInfo subnet : subnets) {
				if (subnet.isInRange(addr)) {
					return subnet.getAddress();
				}
			}
			return defaultAddr;
		}
	}

	public static PrivateDNS getInstance() throws Exception {
		if (instance == null) {
			instance = new PrivateDNS();
		}
		return instance;
	}

	private PrivateDNS() throws Exception{
		lock = new Object();
		state = new ConfigBoolean("PrivateDNS");
		util = PacketProxyUtility.getInstance();
		servers = Servers.getInstance();
		dns = null;
	}

	public boolean isRunning() throws Exception {
		return state.getState();
	}

	public void start(DNSSpoofingIPGetter dnsSpoofingIPGetter){
		synchronized (lock) {
			if (dns == null) {
				try {
					dns = new PrivateDNSImp(dnsSpoofingIPGetter);
					if (dns.isRunning()) {
						dns.start();
						state.setState(true);
					} else {
						
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void stop(){
		synchronized (lock) {
			if (dns != null) {
				dns.finish();
				dns = null;
				try {
					state.setState(false);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

    @SuppressWarnings("unused")
	private class PrivateDNSImp extends Thread {
		private DNSSpoofingIPGetter spoofingIp;

		private InetAddress cAddr;
		private int cPort;
		private byte[] buf  = new byte[BUFSIZE];
		DatagramSocket soc;
		DatagramPacket recvPacket;
		DatagramPacket sendPacket;
		DatagramSocket s_soc;
		DatagramPacket s_recvPacket;
		DatagramPacket s_sendPacket;
		InetAddress s_sAddr;

		public PrivateDNSImp(DNSSpoofingIPGetter dnsSpoofingIPGetter) throws Exception {
			try {
				this.spoofingIp = dnsSpoofingIPGetter;
				soc = new DatagramSocket(PORT);
				recvPacket = new DatagramPacket(buf, BUFSIZE);
				sendPacket = null;
			} catch (BindException e) {
				util.packetProxyLogErr("cannot boot private DNS server (permission issue or already listened)");
				return;
			}

			s_sAddr = InetAddress.getByName(dnsServer);
			s_soc = new DatagramSocket();
			s_recvPacket = new DatagramPacket(buf, BUFSIZE);
			s_sendPacket = null;
		}

		public boolean isRunning() {
			return soc != null;
		}

		public void finish() {
			if (isRunning()) {
				s_soc.close();
				soc.close();
				s_soc = null;
				soc = null;
			}
		}

		public void run() {
			util.packetProxyLog("Private DNS Server started.");
			while (true) {
				try {
					soc.receive(recvPacket);
					cAddr = recvPacket.getAddress();
					cPort = recvPacket.getPort();

					String spoofingIpStr = "";
					if (spoofingIp.isAuto()) {
						String hostAddrStr = cAddr.getHostAddress();
						if (hostAddrStr.equals("0:0:0:0:0:0:0:1")) {
							hostAddrStr = "127.0.0.1";
						}
						spoofingIpStr = spoofAddrFactry.getSpoofAddr(hostAddrStr);
					} else {
						spoofingIpStr = spoofingIp.get();
					}

					byte[] requestData = recvPacket.getData();

					Message smsg = new Message(requestData);
					byte[] smsgBA = smsg.toWire();

					String queryHostName = smsg.getQuestion().getName().toString(true);

					byte[] res = null;
					try {
						if (smsg.getQuestion().getType() != Type.A) {
							throw new UnsupportedOperationException();
						}

						util.packetProxyLog(String.format("[DNS Query] '%s'", queryHostName));

						String ip = PrivateDNSClient.getByName(queryHostName).getHostAddress();
						if (isTargetHost(queryHostName)) {
							ip = spoofingIpStr;
							util.packetProxyLog("Replaced to " + spoofingIpStr);
						}
						jnamed jn = new jnamed(ip);
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);

					} catch(UnknownHostException e) {
						util.packetProxyLogErr(String.format("[DNS Query] Unknown Host: '%s'", queryHostName));
						jnamed jn = new jnamed();
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);

					} catch(UnsupportedOperationException e) {
						// Not implemented yet
						jnamed jn = new jnamed();
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);

					} catch(Exception e) {
						util.packetProxyLogErr(String.format("[DNS Query] Unknown Error: '%s'", queryHostName));
						jnamed jn = new jnamed();
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);

					}
					sendPacket = new DatagramPacket(res, res.length, cAddr, cPort);
					soc.send(sendPacket);
				} catch (SocketException e) {
					e.printStackTrace();
					finish();
					return;
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
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

	}
}

