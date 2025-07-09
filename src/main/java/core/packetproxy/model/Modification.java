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

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Binary;
import packetproxy.common.Binary.HexString;
import packetproxy.common.Utils;

@DatabaseTable(tableName = "modifications")
public class Modification {
	public static final int ALL_SERVER = -1;

	public enum Direction {
		CLIENT_REQUEST, SERVER_RESPONSE, ALL
	};
	public enum Method {
		SIMPLE, REGEX, BINARY
	};

	@DatabaseField(generatedId = true)
	private int id;
	@DatabaseField
	private Boolean enabled;
	@DatabaseField(uniqueCombo = true)
	private int server_id;
	@DatabaseField(uniqueCombo = true)
	private Direction direction;
	@DatabaseField(uniqueCombo = true)
	private String pattern;
	@DatabaseField(uniqueCombo = true)
	private Method method;
	@DatabaseField
	private String replaced;

	public Modification() {
		// ORMLite needs a no-arg constructor
	}
	public Modification(Direction direction, String pattern, String replaced, Method method, Server server) {
		this.enabled = false;
		this.server_id = server != null ? server.getId() : ALL_SERVER;
		this.direction = direction;
		this.pattern = pattern;
		this.replaced = replaced;
		this.method = method;
	}
	public boolean isEnabled() {
		return this.enabled;
	}
	public void setEnabled() {
		this.enabled = true;
	}
	public void setDisabled() {
		this.enabled = false;
	}
	public int getServerId() {
		return this.server_id;
	}
	public void setServerId(int server_id) {
		this.server_id = server_id;
	}
	public Server getServer() throws Exception {
		return Servers.getInstance().query(this.server_id);
	}
	public String getServerName() throws Exception {
		if (this.server_id == ALL_SERVER) {
			return "*";
		}
		Server server = Servers.getInstance().query(this.server_id);
		return server != null ? server.toString() : "";
	}
	public Direction getDirection() {
		return this.direction;
	}
	public void setDirection(Direction direction) {
		this.direction = direction;
	}
	public String getPattern() {
		return this.pattern;
	}
	public void setPattern(String pattern) {
		this.pattern = pattern;
	}
	public String getReplaced() {
		return this.replaced;
	}
	public void setReplaced(String replaced) {
		this.replaced = replaced;
	}
	public Method getMethod() {
		return this.method;
	}
	public void setMethod(Method method) {
		this.method = method;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public byte[] replace(byte[] data, Packet packet) throws Exception {
		if (method == Method.SIMPLE) {
			return replaceText(data, packet);
		} else if (method == Method.REGEX) {
			return replaceRegex(data, packet);
		} else if (method == Method.BINARY) {
			return replaceBinary(data, packet);
		} else {
			throw new Exception("未定義の置換方法");
		}
	}
	private byte[] replaceText(byte[] data, Packet packet) {
		return replaceBinary(data, pattern.getBytes(), replaced.getBytes(), packet);
	}
	private byte[] replaceRegex(byte[] data, Packet packet) {
		Pattern pattern = Pattern.compile(this.pattern, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(new String(data));
		String result = new String(data);
		boolean matched = false;
		while (matcher.find()) {
			matched = true;
			result = matcher.replaceAll(this.replaced);
			packet.setModified();
		}
		if (!matched) {
			// バイナリデータが壊れる可能性があるので、マッチしなかった場合はそのまま返す
			return data;
		}
		return result.getBytes();
	}
	private byte[] replaceBinary(byte[] data, Packet packet) throws Exception {
		byte[] binPattern = new Binary(new HexString(pattern)).toByteArray();
		byte[] binReplaced = new Binary(new HexString(replaced)).toByteArray();
		return replaceBinary(data, binPattern, binReplaced, packet);
	}
	private byte[] replaceBinary(byte[] data, byte[] binPattern, byte[] binReplaced, Packet packet) {
		int idx = 0;
		while (idx < data.length) {
			if ((idx = Utils.indexOf(data, idx, data.length, binPattern)) < 0) {
				return data;
			}
			byte[] front_data = ArrayUtils.subarray(data, 0, idx);
			byte[] back_data = ArrayUtils.subarray(data, idx + binPattern.length, data.length);
			data = ArrayUtils.addAll(front_data, binReplaced);
			data = ArrayUtils.addAll(data, back_data);
			idx += binReplaced.length;
			packet.setModified();
		}
		return data;
	}
	@Override
	public int hashCode() {
		return this.getId();
	}
	public boolean equals(Modification obj) {
		return this.getId() == obj.getId() ? true : false;
	}
}
