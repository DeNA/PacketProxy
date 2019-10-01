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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

import org.xbill.DNS.DNSSpoofingIPGetter;
import org.xbill.DNS.Message;
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

	public void start(DNSSpoofingIPGetter dnsSpoofingIPGetter){
		synchronized (lock) {
			if (dns == null) {
				try {
					dns = new PrivateDNSImp(dnsSpoofingIPGetter);
					dns.start();
					state.setState(true);
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
			this.spoofingIp = dnsSpoofingIPGetter;
			soc = new DatagramSocket(PORT);
			recvPacket = new DatagramPacket(buf, BUFSIZE);
			sendPacket = null;

			s_sAddr = InetAddress.getByName(dnsServer);
			s_soc = new DatagramSocket();
			s_recvPacket = new DatagramPacket(buf, BUFSIZE);
			s_sendPacket = null;
		}

		public void finish() {
			s_soc.close();
			soc.close();
		}

		public void run() {
			util.packetProxyLog("Private DNS Server started.");
			String lastSpoofingIp = "";
			while (true) {
				try {
					if(!lastSpoofingIp.equals(spoofingIp.get())){
						lastSpoofingIp = spoofingIp.get();
						util.packetProxyLog("spoofing IP: " + lastSpoofingIp);
					}
					soc.receive(recvPacket);
					cAddr = recvPacket.getAddress();
					cPort = recvPacket.getPort();

					byte[] requestData = recvPacket.getData();

					Message smsg = new Message(requestData);
					byte[] smsgBA = smsg.toWire();

					String queryHostName = smsg.getQuestion().getName().toString(true);

					byte[] res = null;
					try {
						util.packetProxyLog(String.format("[DNS Query] '%s'", queryHostName));
						String ip = Inet4Address.getByName(queryHostName).getHostAddress();
						if (isTargetHost(queryHostName)) {
							ip = spoofingIp.get();
						}
						jnamed jn = new jnamed(ip);
						res = jn.generateReply(smsg, smsgBA, smsgBA.length, null);
					} catch(UnknownHostException e) {
						util.packetProxyLogErr(String.format("[DNS Query] Unknown Host: '%s'", queryHostName));
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
					util.packetProxyLog("Replaced to " + spoofingIp.get());
					return true;
				}
			}
			return false;
		}

	}
}

