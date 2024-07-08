package packetproxy.model;

import java.net.InetSocketAddress;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "resender_packets")
public class ResenderPacket {
	@DatabaseField(generatedId = true)
	private int id;
	@DatabaseField(uniqueCombo = true)
	private int resends_index; // 上側のタブの番号
	@DatabaseField(uniqueCombo = true)
	private int resend_index; // 下側のタブの番号
	@DatabaseField(dataType = DataType.ENUM_STRING, uniqueCombo = true)
	private Packet.Direction direction;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] data;
	@DatabaseField
	private int listen_port;
	@DatabaseField
	private String client_ip;
	@DatabaseField
	private int client_port;
	@DatabaseField
	private String server_ip;
	@DatabaseField
	private int server_port;
	@DatabaseField
	private String server_name;
	@DatabaseField
	private boolean use_ssl;
	@DatabaseField
	private String encoder_name;
	@DatabaseField
	private String alpn;
	@DatabaseField
	private boolean auto_modified;
	@DatabaseField
	private int conn;
	@DatabaseField
	private long group;

	public ResenderPacket() {
		// ORMLite needs a no-arg constructor
	}

	public ResenderPacket(int resends_index, int resend_index, Packet.Direction direction, byte[] data, int listen_port, String client_ip, int client_port, String server_ip, int server_port, String server_name, boolean use_ssl, String encoder_name, String alpn, boolean auto_modified, int conn, long group) {
		this.resends_index = resends_index;
		this.resend_index = resend_index;
		this.direction = direction;
		this.data = data;
		this.listen_port = listen_port;
		this.client_ip = client_ip;
		this.client_port = client_port;
		this.server_ip = server_ip;
		this.server_port = server_port;
		this.server_name = server_name;
		this.use_ssl = use_ssl;
		this.encoder_name = encoder_name;
		this.alpn = alpn;
		this.auto_modified = auto_modified;
		this.conn = conn;
		this.group = group;
	}

	public int getResendsIndex() {
		return this.resends_index;
	}

	public int getResendIndex() {
		return this.resend_index;
	}

	public Packet.Direction getDirection() {
		return this.direction;
	}

	public OneShotPacket getOneShotPacket() {
		OneShotPacket oneShotPacket = new OneShotPacket(-1, listen_port, new InetSocketAddress(client_ip, client_port), new InetSocketAddress(server_ip, server_port), server_name, use_ssl, data, encoder_name, alpn, direction, conn, group);
		return oneShotPacket;
	}
}
