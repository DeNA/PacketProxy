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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import javax.swing.JOptionPane;
import packetproxy.model.DaoQueryCache;
import packetproxy.model.Database.DatabaseMessage;
import static packetproxy.model.PropertyChangeEventType.MODIFICATIONS_UPDATED;
import static packetproxy.model.PropertyChangeEventType.DATABASE_MESSAGE;

public class Modifications implements PropertyChangeListener {
	private static Modifications instance;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public static Modifications getInstance() throws Exception {
		if (instance == null) {
			instance = new Modifications();
		}
		return instance;
	}

	private Database database;
	private Dao<Modification, Integer> dao;
	private Servers servers;
	private DaoQueryCache<Modification> cache;

	private Modifications() throws Exception {
		database = Database.getInstance();
		servers = Servers.getInstance();
		dao = database.createTable(Modification.class, this);
		cache = new DaoQueryCache<Modification>();
		if (!isLatestVersion()) {
			RecreateTable();
		}
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
		servers.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
		servers.removePropertyChangeListener(listener);
	}

	public void create(Modification modification) throws Exception {
		dao.createIfNotExists(modification);
		cache.clear();
		firePropertyChange();
	}

	public void delete(int id) throws Exception {
		dao.deleteById(id);
		cache.clear();
		firePropertyChange();
	}

	public void delete(Modification modification) throws Exception {
		dao.delete(modification);
		cache.clear();
		firePropertyChange();
	}

	public void update(Modification modification) throws Exception {
		dao.update(modification);
		cache.clear();
		firePropertyChange();
	}

	public void refresh() {
		firePropertyChange();
	}

	public Modification query(int id) throws Exception {
		List<Modification> ret = cache.query("query", 0);
		if (ret != null) {
			return ret.get(0);
		}

		Modification modification = dao.queryForId(id);

		cache.set("query", id, modification);
		return modification;
	}

	public List<Modification> queryAll() throws Exception {
		List<Modification> ret = cache.query("queryAll", 0);
		if (ret != null) {
			return ret;
		}

		ret = dao.queryBuilder().query();

		cache.set("queryAll", 0, ret);
		return ret;
	}

	public List<Modification> queryEnabled(Server server) throws Exception {
		int server_id = Modification.ALL_SERVER;
		if (server != null) {
			server_id = server.getId();
		}

		List<Modification> ret = cache.query("queryEnabled", server_id);
		if (ret != null) {
			return ret;
		}

		ret = dao.queryBuilder().where()
				.eq("server_id", server_id)
				.or()
				.eq("server_id", Modification.ALL_SERVER)
				.and()
				.eq("enabled", true)
				.query();

		cache.set("queryEnabled", server_id, ret);
		return ret;
	}

	public byte[] replaceOnRequest(byte[] data, Server server, Packet client_packet) throws Exception {
		for (Modification mod : queryEnabled(server)) {
			if (mod.getDirection() == Modification.Direction.CLIENT_REQUEST
					|| mod.getDirection() == Modification.Direction.ALL)
				data = mod.replace(data, client_packet);
		}
		return data;
	}

	public byte[] replaceOnResponse(byte[] data, Server server, Packet server_packet) throws Exception {
		for (Modification mod : queryEnabled(server)) {
			if (mod.getDirection() == Modification.Direction.SERVER_RESPONSE
					|| mod.getDirection() == Modification.Direction.ALL)
				data = mod.replace(data, server_packet);
		}
		return data;
	}

	private void firePropertyChange() {
		pcs.firePropertyChange(MODIFICATIONS_UPDATED.toString(), null, null);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!(evt.getSource() instanceof Database)) {
			return;
		}

		DatabaseMessage message = (DatabaseMessage) evt.getNewValue();
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
					dao = database.createTable(Modification.class, this);
					cache.clear();
					firePropertyChange();
					break;
				case RECREATE:
					database = Database.getInstance();
					dao = database.createTable(Modification.class, this);
					cache.clear();
					break;
				default:
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='modifications'").getFirstResult()[0];
		// System.out.println(result);
		return result.equals(
				"CREATE TABLE `modifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `server_id` INTEGER , `direction` VARCHAR , `pattern` VARCHAR , `method` VARCHAR , `replaced` VARCHAR , UNIQUE (`server_id`,`direction`,`pattern`,`method`) )");
	}

	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"Modificationsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(Modification.class);
			dao = database.createTable(Modification.class, this);
			cache.clear();
		}
	}
}
