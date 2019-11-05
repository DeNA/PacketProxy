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

import com.j256.ormlite.dao.Dao;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import packetproxy.model.Database.DatabaseMessage;

public class Servers extends Observable implements Observer {

	private static Servers instance;
	
	public static Servers getInstance() throws Exception {
		if (instance == null) {
			instance = new Servers();
		}
		return instance;
	}
	
	private Database database;
	private Dao<Server,Integer> dao;
	
	private Servers() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(Server.class, this);
	}
	public void create(Server server) throws Exception {
		dao.createIfNotExists(server);
		notifyObservers();
	}
	public void delete(Server server) throws Exception {
		dao.delete(server);
		notifyObservers();
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
		List<Server> servers = dao.queryBuilder().where().eq("ip", hostname).and().eq("port", port).query();
		if (servers.isEmpty()) {
			return queryByHostName(hostname);
		}
		return servers.get(0);
	}
	public Server queryByAddress(InetSocketAddress addr) throws Exception {
		List<Server> all = this.queryAll();
		if (addr.getAddress() == null) {
			throw new Exception(String.format("cannot resolv hostname: %s", addr.getHostName()));
		}
		if (addr.getPort() == 0) {
			throw new Exception(String.format("cannot resolv portnumber: %s", addr.getPort()));
		}
		String target = addr.getAddress().getHostAddress();
		for (Server server : all) {
			List<InetAddress> ips = server.getIps();
			for ( InetAddress ip : ips) {
				if (ip.getHostAddress().equals(target) && server.getPort()==addr.getPort()){
					return server;
				}
			}
		}
		return null;
	}
	public Server queryByHostName(String hostname) throws Exception {
		return dao.queryBuilder().where().eq("ip", hostname).queryForFirst();
	}
	public Server query(int id) throws Exception {
		return dao.queryForId(id);
	}
	public List<Server> queryAll() throws Exception {
		return dao.queryBuilder().orderBy("ip", true).query();
	}
	public List<Server> queryNonHttpProxies() throws Exception {
		return dao.queryBuilder().orderBy("ip", true).where().eq("http_proxy", false).query();
	}
	public List<Server> queryHttpProxies() throws Exception {
		return dao.queryBuilder().orderBy("ip", true).where().eq("http_proxy", true).query();
	}
	public List<Server> queryResolvedByDNS() throws Exception {
		return dao.queryBuilder().where().eq("resolved_by_dns", true).query();
	}
	public void update(Server server) throws Exception {
		dao.update(server);
		notifyObservers();
	}
	@Override
	public void notifyObservers(Object arg) {
		setChanged();
		super.notifyObservers(arg);
		clearChanged();
	}
	@Override
	public void update(Observable o, Object arg) {
		DatabaseMessage message = (DatabaseMessage)arg;
		try {
			switch (message) {
			case PAUSE:
				// TODO ロックを取る
				break;
			case RESUME:
				// TODO ロックを解除
				break;
			case DISCONNECT_NOW:
				break;
			case RECONNECT:
				database = Database.getInstance();
				dao = database.createTable(Server.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(Server.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
