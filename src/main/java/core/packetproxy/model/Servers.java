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

import static packetproxy.model.PropertyChangeEventType.DATABASE_MESSAGE;
import static packetproxy.model.PropertyChangeEventType.SERVERS;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import packetproxy.model.Database.DatabaseMessage;

public class Servers implements PropertyChangeListener {

	private static Servers instance;
	private PropertyChangeSupport changes = new PropertyChangeSupport(this);

	public static Servers getInstance() throws Exception {
		if (instance == null) {

			instance = new Servers();
		}
		return instance;
	}

	private Database database;
	private Dao<Server, Integer> dao;
	private DaoQueryCache<Server> cache;

	private Servers() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(Server.class, this);
		cache = new DaoQueryCache();
	}

	public void create(Server server) throws Exception {
		dao.createIfNotExists(server);
		cache.clear();
		firePropertyChange();
	}

	public void delete(Server server) throws Exception {
		dao.delete(server);
		cache.clear();
		firePropertyChange();
	}

	public Server queryByString(String str) throws Exception {
		List<Server> all = this.queryAll();
		for (Server server : all) {

			if (server.toString().equals(str)) {

				return server;
			}
		}
		return null;
	}

	public Server queryByHostNameAndPort(String hostname, int port) throws Exception {
		String cache_key = hostname + String.valueOf(port);
		List<Server> ret = cache.query("queryByHostNameAndPort", cache_key);
		if (ret != null) {

			return ret.get(0);
		}

		List<Server> servers = dao.queryBuilder().where().eq("ip", hostname).and().eq("port", port).query();
		Server server = null;
		if (servers.isEmpty()) {

			server = queryByHostName(hostname);
		} else {

			server = servers.get(0);
		}

		cache.set("queryByHostNameAndPort", cache_key, server);
		return server;
	}

	public Server queryByAddress(InetSocketAddress addr) throws Exception {
		List<Server> all = this.queryAll();
		if (addr.getAddress() == null) {

			throw new Exception(String.format("cannot resolv hostname: %s", addr.getHostName()));
		}
		if (addr.getPort() == 0) {

			throw new Exception("cannot resolv portnumber: 0");
		}
		String target = addr.getAddress().getHostAddress();
		for (Server server : all) {

			List<InetAddress> ips = server.getIps();
			for (InetAddress ip : ips) {

				if (ip.getHostAddress().equals(target) && server.getPort() == addr.getPort()) {

					return server;
				}
			}
		}
		return null;
	}

	public Server queryByHostName(String hostname) throws Exception {
		List<Server> ret = cache.query("queryByHostName", hostname);
		if (ret != null) {

			return ret.get(0);
		}

		Server server = dao.queryBuilder().where().eq("ip", hostname).queryForFirst();

		cache.set("queryByHostName", hostname, server);
		return server;
	}

	public Server query(int id) throws Exception {
		List<Server> ret = cache.query("query", id);
		if (ret != null) {

			return ret.get(0);
		}

		Server server = dao.queryForId(id);

		cache.set("query", id, server);
		return server;
	}

	public List<Server> queryAll() throws Exception {
		List<Server> ret = cache.query("queryAll", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().orderBy("ip", true).query();

		cache.set("queryAll", 0, ret);
		return ret;
	}

	public List<Server> queryNonHttpProxies() throws Exception {
		List<Server> ret = cache.query("queryNonHttpProxies", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().orderBy("ip", true).where().eq("http_proxy", false).query();

		cache.set("queryNonHttpProxies", 0, ret);
		return ret;
	}

	public List<Server> queryHttpProxies() throws Exception {
		List<Server> ret = cache.query("queryHttpProxies", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().orderBy("ip", true).where().eq("http_proxy", true).query();

		cache.set("queryHttpProxies", 0, ret);
		return ret;
	}

	public List<Server> queryResolvedByDNS() throws Exception {
		List<Server> ret = cache.query("queryResolvedByDNS", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().where().eq("resolved_by_dns", true).query();

		cache.set("queryResolvedByDNS", 0, ret);
		return ret;
	}

	public List<Server> queryResolvedByDNS6() throws Exception {
		List<Server> ret = cache.query("queryResolvedByDNS6", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().where().eq("resolved_by_dns6", true).query();

		cache.set("queryResolvedByDNS6", 0, ret);
		return ret;
	}

	public void update(Server server) throws Exception {
		dao.update(server);
		cache.clear();
		firePropertyChange();
	}

	private void firePropertyChange() {
		changes.firePropertyChange(SERVERS.toString(), null, null);
	}

	private void firePropertyChange(Object arg) {
		changes.firePropertyChange(SERVERS.toString(), null, arg);
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		changes.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changes.removePropertyChangeListener(listener);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!DATABASE_MESSAGE.matches(evt)) {

			return;
		}

		DatabaseMessage message = (DatabaseMessage) evt.getNewValue();
		try {

			switch (message) {

				case PAUSE :
					// TODO ロックを取る
					break;
				case RESUME :
					// TODO ロックを解除
					break;
				case DISCONNECT_NOW :
					break;
				case RECONNECT :
					database = Database.getInstance();
					dao = database.createTable(Server.class, this);
					cache.clear();
					firePropertyChange(message);
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(Server.class, this);
					cache.clear();
					break;
				default :
					break;
			}
		} catch (Exception e) {

			e.printStackTrace();
		}
	}
}
