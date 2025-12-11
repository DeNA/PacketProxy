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
import static packetproxy.model.PropertyChangeEventType.FORWARD_PORTS;
import static packetproxy.util.Logging.errWithStackTrace;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import javax.swing.JOptionPane;
import packetproxy.model.Database.DatabaseMessage;

public class OpenVPNForwardPorts implements PropertyChangeListener {

	private static OpenVPNForwardPorts instance;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public static OpenVPNForwardPorts getInstance() throws Exception {
		if (instance == null) {

			instance = new OpenVPNForwardPorts();
		}
		return instance;
	}

	private Database database;
	private Dao<OpenVPNForwardPort, Integer> dao;
	private DaoQueryCache<OpenVPNForwardPort> cache;

	private OpenVPNForwardPorts() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(OpenVPNForwardPort.class, this);
		cache = new DaoQueryCache<>();
		if (!isLatestVersion()) {

			RecreateTable();
		}
		if (dao.countOf() == 0) {

			create(new OpenVPNForwardPort(OpenVPNForwardPort.TYPE.TCP, 80, 8080));
			create(new OpenVPNForwardPort(OpenVPNForwardPort.TYPE.TCP, 443, 8443));
		}
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public void create(OpenVPNForwardPort forwardPort) throws Exception {
		dao.createIfNotExists(forwardPort);
		cache.clear();
		firePropertyChange();
	}

	public void delete(int id) throws Exception {
		dao.deleteById(id);
		cache.clear();
		firePropertyChange();
	}

	public void delete(OpenVPNForwardPort forwardPort) throws Exception {
		dao.delete(forwardPort);
		cache.clear();
		firePropertyChange();
	}

	public void update(OpenVPNForwardPort forwardPort) throws Exception {
		dao.update(forwardPort);
		cache.clear();
		firePropertyChange();
	}

	public void refresh() {
		firePropertyChange();
	}

	public OpenVPNForwardPort query(int id) throws Exception {
		List<OpenVPNForwardPort> ret = cache.query("query", id);
		if (ret != null) {

			return ret.get(0);
		}

		OpenVPNForwardPort forwardPort = dao.queryForId(id);

		cache.set("query", id, forwardPort);
		return forwardPort;
	}

	public List<OpenVPNForwardPort> queryAll() throws Exception {
		List<OpenVPNForwardPort> ret = cache.query("queryAll", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().query();

		cache.set("queryAll", 0, ret);
		return ret;
	}

	public void firePropertyChange() {
		firePropertyChange(null);
	}

	public void firePropertyChange(Object arg) {
		pcs.firePropertyChange(FORWARD_PORTS.toString(), null, arg);
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
					dao = database.createTable(OpenVPNForwardPort.class, this);
					cache.clear();
					firePropertyChange(message);
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(OpenVPNForwardPort.class, this);
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
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='openvpn_forward_ports'")
				.getFirstResult()[0];
		return result.equals(
				"CREATE TABLE `openvpn_forward_ports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `type` VARCHAR , `fromPort` INTEGER , `toPort` INTEGER , UNIQUE (`type`,`fromPort`,`toPort`) )");
	}

	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"OpenVPNForwardPortsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？", "テーブルの更新", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {

			database.dropTable(OpenVPNForwardPort.class);
			dao = database.createTable(OpenVPNForwardPort.class, this);
		}
	}
}
