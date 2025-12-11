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
import static packetproxy.util.Logging.errWithStackTrace;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import packetproxy.PrivateDNSClient;

@DatabaseTable(tableName = "servers")
public class Server {

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField(uniqueCombo = true)
	private String ip;

	@DatabaseField(uniqueCombo = true)
	private int port;

	@DatabaseField(uniqueCombo = true)
	private String encoder;

	@DatabaseField
	private boolean use_ssl;
	@DatabaseField
	private boolean resolved_by_dns;
	@DatabaseField
	private boolean resolved_by_dns6;
	@DatabaseField
	private boolean http_proxy;
	@DatabaseField
	private String comment;

	private boolean specifiedByHostName;

	public Server() {
		// ORMLite needs a no-arg constructor
	}

	public Server(String ip, int port, String encoder) {
		initialize(ip, port, false, encoder, false, false, false, "");
	}

	public Server(String ip, int port, boolean use_ssl, String encoder, boolean resolved_by_dns,
			boolean resolved_by_dns6, boolean http_proxy, String comment) {
		initialize(ip, port, use_ssl, encoder, resolved_by_dns, resolved_by_dns6, http_proxy, comment);
	}

	private void initialize(String ip, int port, boolean use_ssl, String encoder, boolean resolved_by_dns,
			boolean resolved_by_dns6, boolean http_proxy, String comment) {
		this.ip = ip;
		this.port = port;
		this.use_ssl = use_ssl;
		this.encoder = encoder;
		this.resolved_by_dns = resolved_by_dns;
		this.resolved_by_dns6 = resolved_by_dns6;
		this.http_proxy = http_proxy;
		this.comment = comment;
		this.specifiedByHostName = isHostName(ip);
	}

	private static boolean isHostName(String host) {
		try {

			return !(InetAddress.getByName(host).getHostAddress().equals(host));
		} catch (UnknownHostException e) {

			errWithStackTrace(e);
			return true;
		}
	}

	@Override
	public String toString() {
		return String.format("%s:%d(%s)", ip, port, encoder);
	}

	public InetSocketAddress getAddress() throws Exception {
		return new InetSocketAddress(PrivateDNSClient.getByName(ip), port);
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getIp() {
		return this.ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getEncoder() {
		return encoder;
	}

	public void setEncoder(String encoder) {
		this.encoder = encoder;
	}

	public void setUseSSL(boolean ssl) {
		this.use_ssl = ssl;
	}

	public boolean getUseSSL() {
		return this.use_ssl;
	}

	public void setHttpProxy(boolean http_proxy) {
		this.http_proxy = http_proxy;
	}

	public boolean isHttpProxy() {
		return this.http_proxy;
	}

	public void enableResolved() {
		this.resolved_by_dns = true;
	}

	public void disableResolved() {
		this.resolved_by_dns = false;
	}

	public boolean isResolved() {
		return this.resolved_by_dns;
	}

	public void setResolved(boolean resolved_by_dns) {
		this.resolved_by_dns = resolved_by_dns;
	}

	public void enableResolved6() {
		this.resolved_by_dns6 = true;
	}

	public void disableResolved6() {
		this.resolved_by_dns6 = false;
	}

	public boolean isResolved6() {
		return this.resolved_by_dns6;
	}

	public void setResolved6(boolean resolved_by_dns6) {
		this.resolved_by_dns6 = resolved_by_dns6;
	}

	public String getComment() {
		return this.comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public List<InetAddress> getIps() {
		try {

			if (specifiedByHostName) {

				List<InetAddress> ips = Arrays.asList(PrivateDNSClient.getAllByName(ip));
				return ips;
			} else {

				List<InetAddress> ips = new ArrayList<InetAddress>();
				ips.add(InetAddress.getByName(ip));
				return ips;
			}
		} catch (UnknownHostException e) {

			err("Nonexistent server '%s' is specified in config [DNS resolv error]", ip);
			return new ArrayList<InetAddress>();
		} catch (Exception e) {

			// TODO Auto-generated catch block
			errWithStackTrace(e);
			return new ArrayList<InetAddress>();
		}
	}
}
