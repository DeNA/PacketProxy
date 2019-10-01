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
import packetproxy.common.Binary;
import packetproxy.common.Binary.HexString;
import packetproxy.common.Utils;

@DatabaseTable(tableName = "interceptOptions")
public class InterceptOption
{
	public static final int ALL_SERVER = -1;
	public enum Type { REQUEST, /* TODO HOST, URL,*/  };
	public enum Direction { CLIENT_REQUEST, SERVER_RESPONSE };  // 両方同じルールで捕まえたい事はないのでALLは無し
	public enum Relationship { MATCHES, DOES_NOT_MATCH, WAS_INTERCEPTED };
	public enum Method { SIMPLE, REGEX, BINARY };

	@DatabaseField(generatedId = true)
	private int id;
	@DatabaseField
	private Boolean enabled;
	@DatabaseField(uniqueCombo = true)
	private Direction direction;
	@DatabaseField(uniqueCombo = true)
	private Type type;
	@DatabaseField(uniqueCombo = true)
	private Relationship relationship;
	@DatabaseField(uniqueCombo = true)
	private Method method;
	@DatabaseField(uniqueCombo = true)
	private String pattern;
	@DatabaseField(uniqueCombo = true)
	private int server_id;

	public InterceptOption() {
		// ORMLite needs a no-arg constructor
	}
	public InterceptOption(Direction direction, Type type, Relationship relationship, String pattern, Method method, Server server) {
		this.enabled = false;
		this.direction = direction;
		this.type = type;
		this.relationship = relationship;
		this.method = method;
		this.pattern = pattern;
		this.server_id = server != null ? server.getId() : ALL_SERVER;
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
		if (this.server_id == ALL_SERVER) { return "*"; }
		Server server = Servers.getInstance().query(this.server_id);
		return server != null ? server.toString() : "";
	}
	public Direction getDirection() {
		return this.direction;
	}
	public void setDirection(Direction direction) {
		this.direction = direction;
	}
	public Type getType() {
		return this.type;
	}
	public void setType(Type type) {
		this.type = type;
	}
	public Relationship getRelationship() {
		return this.relationship;
	}
	public void setRelationship(Relationship relationship) {
		this.relationship = relationship;
	}
	public Method getMethod() {
		return this.method;
	}
	public void setMethod(Method method) {
		this.method = method;
	}
	public String getPattern() {
		return this.pattern;
	}
	public void setPattern(String pattern) {
		this.pattern = pattern;
	}
	public int getId() {
		return id;
	}
	public boolean match(Packet client_packet, Packet server_packet) throws Exception {
		assert(this.relationship != Relationship.WAS_INTERCEPTED);
		byte[] data = null;
		if (this.direction == Direction.CLIENT_REQUEST) {
			assert(client_packet != null);
			data = client_packet.getDecodedData();
		} else if (this.direction == Direction.SERVER_RESPONSE) {
			assert(server_packet != null);
			data = server_packet.getDecodedData();
		}
		if (data == null) { return false; }
		// TODO typeがREQUEST以外の場合に該当の箇所を取る
		boolean result = false;
		if (method == Method.SIMPLE) {
			result = matchText(data);
		} else if (method == Method.REGEX) {
			result = matchRegex(data);
		} else if (method == Method.BINARY) {
			result = matchBinary(data);
		} else {
			throw new Exception("未定義の検索方法");
		}
		if (this.relationship == Relationship.DOES_NOT_MATCH) {
			result = !result;
		}
		return result;
	}
	private boolean matchText(byte[] data) {
		return matchBinary(data, pattern.getBytes());
	}
	private boolean matchRegex(byte[] data) {
		Pattern pattern = Pattern.compile(this.pattern, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(new String(data));
		return matcher.find();
	}
	private boolean matchBinary(byte[] data) throws Exception {
		byte[] binPattern = new Binary(new HexString(pattern)).toByteArray();
		return matchBinary(data, binPattern);
	}
	private boolean matchBinary(byte[] data, byte[] binPattern) {
		return Utils.indexOf(data, 0, data.length, binPattern) >= 0;
	}
	@Override
	public int hashCode() {
		return this.getId();
	}
	public boolean equals(InterceptOption obj) {
		return this.getId() == obj.getId() ? true : false;
	}
}
