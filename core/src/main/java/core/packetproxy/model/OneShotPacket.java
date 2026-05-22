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
package packetproxy.model;

import static packetproxy.util.Logging.err;

import java.net.InetSocketAddress;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.EncoderManager;
import packetproxy.common.Range;
import packetproxy.common.Utils;
import packetproxy.encode.Encoder;
import packetproxy.model.Packet.Direction;

public class OneShotPacket implements PacketInfo, Cloneable {

	private int id;
	private Packet.Direction direction;
	private byte[] data;
	private int listen_port;
	private String client_ip;
	private int client_port;
	private String server_ip;
	private int server_port;
	private String server_name;
	private boolean use_ssl;
	private String encoder_name;
	private String alpn;
	private boolean auto_modified;
	private int conn;
	private long group;

	public OneShotPacket() {
	}

	public OneShotPacket(int id, int listen_port, InetSocketAddress client_addr, InetSocketAddress server_addr,
			String server_name, boolean use_ssl, byte[] data, String encoder_name, String alpn, Packet.Direction dir,
			int conn, long group) {
		initialize(id, listen_port, client_addr.getAddress().getHostAddress(), client_addr.getPort(),
				server_addr.getAddress().getHostAddress(), server_addr.getPort(), server_name, use_ssl, data,
				encoder_name, alpn, dir, conn, group);
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	private void initialize(int id, int listen_port, String client_ip, int client_port, String server_ip,
			int server_port, String server_name, boolean use_ssl, byte[] data, String encoder_name, String alpn,
			Packet.Direction dir, int conn, long group) {
		this.id = id;
		this.listen_port = listen_port;
		this.client_ip = client_ip;
		this.client_port = client_port;
		this.server_ip = server_ip;
		this.server_port = server_port;
		this.server_name = server_name;
		this.use_ssl = use_ssl;
		this.data = data;
		this.encoder_name = encoder_name;
		this.alpn = alpn;
		this.direction = dir;
		this.auto_modified = false;
		this.conn = conn;
		this.group = group;
	}

	public Packet.Direction getDirection() {
		return this.direction;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public void replaceData(Range area, byte[] replacer) {
		this.data = Utils.replaceArray(this.data, area, replacer);
	}

	public byte[] getData(Range area) {
		return ArrayUtils.subarray(this.data, area.getPositionStart(), area.getPositionEnd());
	}

	public byte[] getData() {
		return this.data == null ? new byte[]{} : this.data;
	}

	public void setAutoModified() {
		this.auto_modified = true;
	}

	public boolean getAutoModified() {
		return this.auto_modified;
	}

	public int getListenPort() {
		return this.listen_port;
	}

	public String getClientIP() {
		return this.client_ip;
	}

	public int getClientPort() {
		return this.client_port;
	}

	public String getServerIP() {
		return this.server_ip;
	}

	public int getServerPort() {
		return this.server_port;
	}

	public String getServerName() {
		return this.server_name;
	}

	public boolean getUseSSL() {
		return this.use_ssl;
	}

	public InetSocketAddress getClient() {
		return new InetSocketAddress(this.client_ip, this.client_port);
	}

	public InetSocketAddress getServer() {
		return new InetSocketAddress(this.server_ip, this.server_port);
	}

	public String getEncoder() {
		return this.encoder_name;
	}

	public void setEncoder(String encoder_name) {
		this.encoder_name = encoder_name;
	}

	public String getAlpn() {
		return this.alpn;
	}

	public void setAlpn(String alpn) {
		this.alpn = alpn;
	}

	public int getConn() {
		return this.conn;
	}

	public long getGroup() {
		return this.group;
	}

	public void encode() {
	}

	public Packet toPacket() throws Exception {
		Packet packet = new Packet(listen_port, client_ip, client_port, server_ip, server_port, server_name, use_ssl,
				encoder_name, alpn, direction, conn, group);
		packet.setDecodedData(getData());
		return packet;
	}

	public String getSummarizedRequest() throws Exception {
		Encoder encoder = EncoderManager.getInstance().createInstance(encoder_name, alpn);
		if (encoder == null) {

			err("エンコードモジュール: %s が見当たらないので、Sample とみなしました", encoder_name);
			encoder = EncoderManager.getInstance().createInstance("Sample", alpn);
		}
		return (getDirection() == Direction.CLIENT) ? encoder.getSummarizedRequest(toPacket()) : "";
	}

	public String getSummarizedResponse() throws Exception {
		Encoder encoder = EncoderManager.getInstance().createInstance(encoder_name, alpn);
		if (encoder == null) {

			err("エンコードモジュール: %s が見当たらないので、Sample とみなしました", encoder_name);
			encoder = EncoderManager.getInstance().createInstance("Sample", alpn);
		}
		return (getDirection() == Direction.SERVER) ? encoder.getSummarizedResponse(toPacket()) : "";
	}

	public ResenderPacket getResenderPacket(int resends_index, int resend_index) throws Exception {
		ResenderPacket resenderPacket = new ResenderPacket(resends_index, resend_index, direction, data, listen_port,
				client_ip, client_port, server_ip, server_port, server_name, use_ssl, encoder_name, alpn, auto_modified,
				conn, group);
		return resenderPacket;
	}
}
