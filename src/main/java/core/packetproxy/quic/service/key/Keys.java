package packetproxy.quic.service.key;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.ConnectionId;

@Getter
public class Keys {

	private final RoleKeys clientKeys = new RoleKeys(Constants.Role.CLIENT);
	private final RoleKeys serverKeys = new RoleKeys(Constants.Role.SERVER);
	private byte[] clientRandom;

	public void computeInitialKey(ConnectionId destConnId) {
		this.clientKeys.computeInitialKey(destConnId);
		this.serverKeys.computeInitialKey(destConnId);
	}

	public void computeZeroRttKey(byte[] secret) {
		this.clientKeys.computeZeroRttKey(secret);
	}

	public void computeHandshakeKey(byte[] clientSecret, byte[] serverSecret) {
		this.clientKeys.computeHandshakeKey(clientSecret);
		this.serverKeys.computeHandshakeKey(serverSecret);
	}

	public void computeApplicationKey(byte[] clientSecret, byte[] serverSecret) {
		this.clientKeys.computeApplicationKey(clientSecret);
		this.serverKeys.computeApplicationKey(serverSecret);
		outputSecretsForWireshark(); /* for wireshark debug */
	}

	public boolean hasInitialKey() {
		return this.clientKeys.hasInitialKey();
	}

	public boolean hasHandshakeKey() {
		return this.clientKeys.hasHandshakeKey();
	}

	public boolean hasApplicationKey() {
		return this.clientKeys.hasApplicationKey();
	}

	public RoleKeys getRoleKeys(Constants.Role role) {
		return (role == Constants.Role.CLIENT) ? this.clientKeys : this.serverKeys;
	}

	public void setClientRandom(byte[] clientRandom) {
		this.clientRandom = clientRandom;
	}

	public void discardInitialKey() {
		this.clientKeys.discardInitialKey();
		this.serverKeys.discardInitialKey();
	}

	public void discardHandshakeKey() {
		this.clientKeys.discardHandshakeKey();
		this.serverKeys.discardHandshakeKey();
	}

	public boolean discardedInitialKey() {
		return this.clientKeys.discardedInitialKey();
	}

	public boolean discardedHandshakeKey() {
		return this.clientKeys.discardedHandshakeKey();
	}

	private static Path logDir = Paths.get(System.getProperty("user.home") + "/.packetproxy/logs");
	private static Path keylogFile = Paths.get(logDir + "/quic_tls.keylog");

	/**
	 * Wiresharkのメニューから、Preference > Advanced > tls.keylog_file に keylog
	 * ファイルのパスを入力した後、 View > Reload で再読み込みするとQUICパケットのペイロードが読めるようになる
	 */
	public void outputSecretsForWireshark() {
		if (this.clientRandom != null && this.clientKeys.hasHandshakeKey() && this.clientKeys.hasApplicationKey()) {
			try {
				if (!Files.exists(logDir)) {
					Files.createDirectories(logDir);
				}
				try (FileWriter file = new FileWriter(keylogFile.toFile())) {
					String clientRandomStr = Hex.encodeHexString(this.clientRandom);
					file.write(String.format("CLIENT_HANDSHAKE_TRAFFIC_SECRET %s %s\n", clientRandomStr,
							Hex.encodeHexString(this.clientKeys.getHandshakeKey().getSecret())));
					file.write(String.format("SERVER_HANDSHAKE_TRAFFIC_SECRET %s %s\n", clientRandomStr,
							Hex.encodeHexString(this.serverKeys.getHandshakeKey().getSecret())));
					file.write(String.format("CLIENT_TRAFFIC_SECRET_0 %s %s\n", clientRandomStr,
							Hex.encodeHexString(this.clientKeys.getApplicationKey().getSecret())));
					file.write(String.format("SERVER_TRAFFIC_SECRET_0 %s %s\n", clientRandomStr,
							Hex.encodeHexString(this.serverKeys.getApplicationKey().getSecret())));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
