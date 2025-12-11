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
import static packetproxy.model.PropertyChangeEventType.SSL_PASS_THROUGHS;
import static packetproxy.util.Logging.errWithStackTrace;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import javax.swing.JOptionPane;
import packetproxy.ListenPortManager;
import packetproxy.model.Database.DatabaseMessage;

public class SSLPassThroughs implements PropertyChangeListener {

	private static SSLPassThroughs instance;
	private PropertyChangeSupport changes = new PropertyChangeSupport(this);

	public static SSLPassThroughs getInstance() throws Exception {
		if (instance == null) {

			instance = new SSLPassThroughs();
		}
		return instance;
	}

	private Database database;
	private Dao<SSLPassThrough, Integer> dao;
	private DaoQueryCache<SSLPassThrough> cache;
	private ListenPorts listenPorts;

	private SSLPassThroughs() throws Exception {
		database = Database.getInstance();
		listenPorts = ListenPorts.getInstance();
		dao = database.createTable(SSLPassThrough.class, this);
		cache = new DaoQueryCache<>();
		if (!isLatestVersion()) {

			RecreateTable();
		}
		if (dao.countOf() == 0) {

			create(new SSLPassThrough(".*\\.apple\\.com", SSLPassThrough.ALL_PORTS));
			create(new SSLPassThrough(".*\\.googleapis\\.com", SSLPassThrough.ALL_PORTS));
		}
	}

	public void create(SSLPassThrough sslPassThrough) throws Exception {
		dao.createIfNotExists(sslPassThrough);
		cache.clear();
		firePropertyChange();
	}

	public void delete(int id) throws Exception {
		dao.deleteById(id);
		cache.clear();
		firePropertyChange();
	}

	public void delete(SSLPassThrough sslPassThrough) throws Exception {
		dao.delete(sslPassThrough);
		cache.clear();
		firePropertyChange();
	}

	public void update(SSLPassThrough sslPassThrough) throws Exception {
		dao.update(sslPassThrough);
		cache.clear();
		firePropertyChange();
	}

	public void refresh() {
		firePropertyChange();
	}

	public SSLPassThrough query(int id) throws Exception {
		List<SSLPassThrough> ret = cache.query("query", id);
		if (ret != null) {

			return ret.get(0);
		}

		SSLPassThrough ssl_pass_through = dao.queryForId(id);

		cache.set("query", id, ssl_pass_through);
		return ssl_pass_through;
	}

	public List<SSLPassThrough> queryAll() throws Exception {
		List<SSLPassThrough> ret = cache.query("queryAll", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().query();

		cache.set("queryAll", 0, ret);
		return ret;
	}

	public List<SSLPassThrough> queryEnabled(String serverName) throws Exception {
		List<SSLPassThrough> ret = cache.query("queryEnabled", serverName);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().where().eq("server_name", serverName).and().eq("enabled", true).query();

		cache.set("queryEnabled", serverName, ret);
		return ret;
	}

	public List<SSLPassThrough> queryEnabled(String serverName, ListenPort listenPort) throws Exception {
		String cache_key = serverName + String.valueOf(listenPort.hashCode());
		List<SSLPassThrough> ret = cache.query("queryEnabled2", cache_key);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().where().eq("server_name", serverName).or().eq("listen_port", listenPort).and()
				.eq("enabled", true).query();

		cache.set("queryEnabled2", cache_key, ret);
		return ret;
	}

	public boolean includes(String serverName, int listenPort) throws Exception {
		String cache_key = serverName + String.valueOf(listenPort);
		List<SSLPassThrough> spts = cache.query("includes", cache_key);
		if (spts == null) {

			spts = dao.queryBuilder().where().eq("listen_port", listenPort).or()
					.eq("listen_port", SSLPassThrough.ALL_PORTS).and().eq("enabled", true).query();
			cache.set("includes", cache_key, spts);
		}
		for (SSLPassThrough spt : spts) {

			if (serverName.matches(spt.getServerName())) {

				return true;
			}
		}
		return false;
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		changes.addPropertyChangeListener(listener);
		listenPorts.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changes.removePropertyChangeListener(listener);
		listenPorts.removePropertyChangeListener(listener);
	}

	private void firePropertyChange() {
		firePropertyChange(null);
	}

	private void firePropertyChange(Object value) {
		try {

			// 設定を反映するためにポートを再起動する
			ListenPortManager.getInstance().rebootIfHTTPProxyRunning();
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		changes.firePropertyChange(SSL_PASS_THROUGHS.toString(), null, value);
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
					dao = database.createTable(SSLPassThrough.class, this);
					cache.clear();
					firePropertyChange(message);
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(SSLPassThrough.class, this);
					cache.clear();
					break;
				default :
					break;
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='sslpassthroughs'").getFirstResult()[0];
		return result.equals(
				"CREATE TABLE `sslpassthroughs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `server_name` VARCHAR , `listen_port` INTEGER , UNIQUE (`server_name`,`listen_port`) )");
	}

	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null, "SSLPassThroughsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {

			database.dropTable(SSLPassThrough.class);
			dao = database.createTable(SSLPassThrough.class, this);
		}
	}
}
