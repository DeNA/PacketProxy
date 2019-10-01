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
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.JOptionPane;
import packetproxy.ListenPortManager;
import packetproxy.model.Database.DatabaseMessage;

public class SSLPassThroughs extends Observable implements Observer
{
	private static SSLPassThroughs incetance;
	
	public static SSLPassThroughs getInstance() throws Exception {
		if (incetance == null) {
			incetance = new SSLPassThroughs();
		}
		return incetance;
	}
	
	private Database database;
	private Dao<SSLPassThrough,Integer> dao;
	private ListenPorts listenPorts;
	
	private SSLPassThroughs() throws Exception {
		database = Database.getInstance();
		listenPorts = ListenPorts.getInstance();
		dao = database.createTable(SSLPassThrough.class, this);
		if (!isLatestVersion()) {
			RecreateTable();
		}
		if (dao.countOf() == 0) {
			create(new SSLPassThrough(".*\\.apple\\.com", SSLPassThrough.ALL_PORTS));
			create(new SSLPassThrough(".*\\.googleapis\\.com", SSLPassThrough.ALL_PORTS));
		}
	}
	public void create(SSLPassThrough sslPassThrough) throws Exception {
		sslPassThrough.setEnabled();
		dao.createIfNotExists(sslPassThrough);
		notifyObservers();
	}
	public void delete(int id) throws Exception {
		dao.deleteById(id);
		notifyObservers();
	}
	public void delete(SSLPassThrough sslPassThrough) throws Exception {
		dao.delete(sslPassThrough);
		notifyObservers();
	}
	public void update(SSLPassThrough sslPassThrough) throws Exception {
		dao.update(sslPassThrough);
		notifyObservers();
	}
	public void refresh() {
		notifyObservers();
	}
	public SSLPassThrough query(int id) throws Exception {
		return dao.queryForId(id);
	}
	public List<SSLPassThrough> queryAll() throws Exception {
		return dao.queryBuilder().query();
	}
	public List<SSLPassThrough> queryEnabled(String serverName) throws Exception {
		return dao.queryBuilder().where()
				.eq("server_name",  serverName)
				.and()
				.eq("enabled", true)
				.query();
	}
	public List<SSLPassThrough> queryEnabled(String serverName, ListenPort listenPort) throws Exception {
		return dao.queryBuilder().where()
				.eq("server_name",  serverName)
				.or()
				.eq("listen_port",  listenPort)
				.and()
				.eq("enabled", true)
				.query();
	}
	public boolean includes(String serverName, int listenPort) throws Exception {
		List<SSLPassThrough> spts = dao.queryBuilder().where()
						.eq("listen_port", listenPort)
						.or()
						.eq("listen_port", SSLPassThrough.ALL_PORTS)
						.and()
						.eq("enabled", true).query();
		for (SSLPassThrough spt : spts) {
			if (serverName.matches(spt.getServerName())) {
				return true;
			}
		}
		return false;
	}
	@Override
	public void notifyObservers(Object arg) {
		try {
			// 設定を反映するためにポートを再起動する
			ListenPortManager.getInstance().rebootIfHTTPProxyRunning();
		} catch (Exception e) {
			e.printStackTrace();
		}
		setChanged();
		super.notifyObservers(arg);
		clearChanged();
	}
	@Override
	public void addObserver(Observer observer) {
		super.addObserver(observer);
		listenPorts.addObserver(observer);
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
				dao = database.createTable(SSLPassThrough.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(SSLPassThrough.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='sslpassthroughs'").getFirstResult()[0];
		return result.equals("CREATE TABLE `sslpassthroughs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `server_name` VARCHAR , `listen_port` INTEGER , UNIQUE (`server_name`,`listen_port`) )");
	}
	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"SSLPassThroughsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(SSLPassThrough.class);
			dao = database.createTable(SSLPassThrough.class, this);
		}
	}
}
