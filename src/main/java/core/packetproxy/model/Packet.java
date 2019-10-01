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

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.net.InetSocketAddress;
import java.util.Date;
import packetproxy.EncoderManager;
import packetproxy.encode.Encoder;
import packetproxy.util.PacketProxyUtility;

@DatabaseTable(tableName = "packets")
public class Packet implements PacketInfo
{
	public enum Direction { SERVER, CLIENT };

	@DatabaseField(generatedId = true)
	private int id;
	@DatabaseField(dataType = DataType.ENUM_STRING)
	private Direction direction;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] decoded_data;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] modified_data;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] sent_data;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] received_data;
	@DatabaseField
	private int listen_port;
	@DatabaseField
	private String client_ip;
	@DatabaseField
	private int client_port;
	@DatabaseField
	private String server_ip;
	@DatabaseField
	private String server_name;
	@DatabaseField
	private int server_port;
	@DatabaseField
	private boolean use_ssl;
	@DatabaseField
	private String content_type;
	@DatabaseField
	private String encoder_name;
	@DatabaseField
	private boolean modified;
	@DatabaseField
	private boolean resend;
	@DatabaseField(dataType = DataType.DATE_LONG)
	private Date date;
	@DatabaseField
	private int conn;
	@DatabaseField
	private long group;

	public Packet() {
		// ORMLite needs a no-arg constructor 
	}
	public Packet(int listen_port, InetSocketAddress client_addr, InetSocketAddress server_addr, String server_name, boolean use_ssl, String encoder, Direction dir, int conn, long group) {
		initialize(listen_port,
				client_addr.getAddress().getHostAddress(), client_addr.getPort(),
				server_addr.getAddress().getHostAddress(), server_addr.getPort(), server_name, use_ssl, encoder, dir, conn, group);
	}
	public Packet(int listen_port, String client_ip, int client_port, String server_ip, int server_port, String server_name, boolean use_ssl, String encoder, Direction dir, int conn, long group) {
		initialize(listen_port, client_ip, client_port, server_ip, server_port, server_name, use_ssl, encoder, dir, conn, group);
	}
	private void initialize(int listen_port, String client_ip, int client_port, String server_ip, int server_port, String server_name, boolean use_ssl, String encoder, Direction dir, int conn, long group)
	{
		this.listen_port = listen_port;
		this.client_ip = client_ip;
		this.client_port = client_port;
		this.server_ip = server_ip;
		this.server_port = server_port;
		this.server_name = server_name;
		this.content_type = "";
		this.use_ssl = use_ssl;
		this.encoder_name = encoder;
		this.direction = dir;
		this.received_data = new byte[]{};
		this.decoded_data = new byte[]{};
		this.modified_data = new byte[]{};
		this.sent_data = new byte[]{};
		this.modified = false;
		this.resend = false;
		this.date = new Date();
		this.conn = conn;
		this.group = group;
	}
	public Direction getDirection() {
		return this.direction;
	}
	public int getId() {
		return this.id;
	}
	public OneShotPacket getOneShotPacket(byte[] data) {
		return new OneShotPacket(
				getId(),
				getListenPort(),
				getClient(),
				getServer(),
				getServerName(),
				getUseSSL(),
				data,
				getEncoder(),
				getDirection(),
				getConn(),
				getGroup());

	}
	public void setModifiedData(byte[] data) {
		this.modified_data = data;
	}
	public byte[] getModifiedData() {
		return this.modified_data == null ? new byte[]{} : this.modified_data;
	}
	public OneShotPacket getOneShotFromModifiedData() {
		return new OneShotPacket(
				getId(),
				getListenPort(),
				getClient(),
				getServer(),
				getServerName(),
				getUseSSL(),
				getModifiedData(),
				getEncoder(),
				getDirection(),
				getConn(),
				getGroup());
	}
	public void setSentData(byte[] data) {
		this.sent_data = data;
	}
	public byte[] getSentData() {
		return this.sent_data == null ? new byte[]{} : this.sent_data;
	}
	public void setReceivedData(byte[] data) {
		this.received_data = data;
	}
	public byte[] getReceivedData() {
		return this.received_data == null ? new byte[]{} : this.received_data;
	}
	public OneShotPacket getOneShotFromReceivedData() {
		return new OneShotPacket(
				getId(),
				getListenPort(),
				getClient(),
				getServer(),
				getServerName(),
				getUseSSL(),
				getReceivedData(),
				getEncoder(),
				getDirection(),
				getConn(),
				getGroup());
	}
	public void setDecodedData(byte[] data) {
		this.decoded_data = data;
	}
	public byte[] getDecodedData() {
		return this.decoded_data == null ? new byte[]{} : this.decoded_data;
	}
	public void setModified() {
		this.modified = true;
	}
	public boolean getModified() {
		return this.modified;
	}
	public void setResend() {
		this.resend = true;
	}
	public boolean getResend() {
		return this.resend;
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
	public String getContentType() {
		return content_type;
	}
	public void setContentType(String content_type) {
		this.content_type = content_type;
	}
	public String getEncoder() {
		return this.encoder_name;
	}
	public Date getDate() {
		return this.date;
	}
	public int getConn() {
		return this.conn;
	}
	public long getGroup() {
		return this.group;
	}
	public String getSummarizedRequest() throws Exception {
		Encoder encoder = EncoderManager.getInstance().createInstance(encoder_name);
		if (encoder == null) {
			PacketProxyUtility.getInstance().packetProxyLogErr(String.format("エンコードモジュール: %s が見当たらないので、Sample とみなしました", encoder_name));
			encoder = EncoderManager.getInstance().createInstance("Sample");
		}
		return (getDirection() == Direction.CLIENT) ? encoder.getSummarizedRequest(this) : "";
	}
	public String getSummarizedResponse() throws Exception {
		Encoder encoder = EncoderManager.getInstance().createInstance(encoder_name);
		if (encoder == null) {
			PacketProxyUtility.getInstance().packetProxyLogErr(String.format("エンコードモジュール: %s が見当たらないので、Sample とみなしました", encoder_name));
			encoder = EncoderManager.getInstance().createInstance("Sample");
		}
		return (getDirection() == Direction.SERVER) ? encoder.getSummarizedResponse(this) : "";
	}
	public void decode(){

	}
}
