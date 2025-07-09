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
import static packetproxy.model.PropertyChangeEventType.INTERCEPT_OPTIONS;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import packetproxy.model.Database.DatabaseMessage;
import packetproxy.model.InterceptOption.Direction;

public class InterceptOptions implements PropertyChangeListener {
	private static InterceptOptions instance;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public static InterceptOptions getInstance() throws Exception {
		if (instance == null) {
			instance = new InterceptOptions();
		}
		return instance;
	}

	private Database database;
	private Dao<InterceptOption, Integer> dao;
	private Servers servers;
	private ConfigBoolean enabled;
	private DaoQueryCache<InterceptOption> cache;

	private void setDefaultRulesIfNotFound() throws Exception {
		int i1Num = dao.queryBuilder().where().eq("Direction", InterceptOption.Direction.ALL_THE_OTHER_REQUESTS).query()
				.size();
		if (i1Num == 0) {
			InterceptOption i1 = new InterceptOption(InterceptOption.Direction.ALL_THE_OTHER_REQUESTS,
					InterceptOption.Type.REQUEST, InterceptOption.Relationship.ARE_INTERCEPTED, "",
					InterceptOption.Method.UNDEFINED, null);
			i1.setEnabled();
			dao.create(i1);
			cache.clear();
		}
		int i2Num = dao.queryBuilder().where().eq("Direction", InterceptOption.Direction.ALL_THE_OTHER_RESPONSES)
				.query().size();
		if (i2Num == 0) {
			InterceptOption i2 = new InterceptOption(InterceptOption.Direction.ALL_THE_OTHER_RESPONSES,
					InterceptOption.Type.REQUEST, InterceptOption.Relationship.ARE_INTERCEPTED, "",
					InterceptOption.Method.UNDEFINED, null);
			i2.setEnabled();
			dao.create(i2);
			cache.clear();
		}
	}

	private InterceptOptions() throws Exception {
		database = Database.getInstance();
		servers = Servers.getInstance();
		dao = database.createTable(InterceptOption.class, this);
		cache = new DaoQueryCache<InterceptOption>();
		enabled = new ConfigBoolean("InterceptOptions");
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

	public void create(InterceptOption intercept_option) throws Exception {
		intercept_option.setEnabled();
		dao.createIfNotExists(intercept_option);
		cache.clear();
		firePropertyChange();
	}

	public void delete(int id) throws Exception {
		dao.deleteById(id);
		cache.clear();
		firePropertyChange();
	}

	public void delete(InterceptOption intercept_option) throws Exception {
		dao.delete(intercept_option);
		cache.clear();
		firePropertyChange();
	}

	public void update(InterceptOption intercept_option) throws Exception {
		dao.update(intercept_option);
		cache.clear();
		firePropertyChange();
	}

	public void refresh() {
		firePropertyChange();
	}

	public InterceptOption query(int id) throws Exception {
		return dao.queryForId(id);
	}

	private List<InterceptOption> sort(List<InterceptOption> list) {
		List<InterceptOption> sorted = new ArrayList<>();
		for (InterceptOption l : list) {
			if (l.isDirection(Direction.REQUEST)) {
				sorted.add(l);
			}
		}
		for (InterceptOption l : list) {
			if (l.isDirection(Direction.ALL_THE_OTHER_REQUESTS)) {
				sorted.add(l);
			}
		}
		for (InterceptOption l : list) {
			if (l.isDirection(Direction.RESPONSE)) {
				sorted.add(l);
			}
		}
		for (InterceptOption l : list) {
			if (l.isDirection(Direction.ALL_THE_OTHER_RESPONSES)) {
				sorted.add(l);
			}
		}
		return sorted;
	}

	public List<InterceptOption> queryAll() throws Exception {
		try {
			List<InterceptOption> ret = cache.query("queryAll", 0);
			if (ret != null) {
				return ret;
			}

			setDefaultRulesIfNotFound();
			List<InterceptOption> list = dao.queryBuilder().query();
			ret = sort(list);

			cache.set("queryAll", 0, ret);
			return ret;
		} catch (Exception e) {
			database.dropTable(InterceptOption.class);
			dao = database.createTable(InterceptOption.class, this);
			setDefaultRulesIfNotFound();
			return dao.queryBuilder().query();
		}
	}

	public List<InterceptOption> queryEnabled(Server server) throws Exception {
		int server_id = InterceptOption.ALL_SERVER;
		if (server != null) {
			server_id = server.getId();
		}

		List<InterceptOption> ret = cache.query("queryEnabled", server_id);
		if (ret != null) {
			return ret;
		}

		setDefaultRulesIfNotFound();
		List<InterceptOption> list = dao.queryBuilder().where().eq("server_id", server_id).or()
				.eq("server_id", InterceptOption.ALL_SERVER).and().eq("enabled", true).query();
		ret = sort(list);

		cache.set("queryEnabled", server_id, ret);
		return ret;
	}

	public boolean interceptOnRequest(Server server, Packet client_packet) throws Exception {
		for (InterceptOption intercept : queryEnabled(server)) {
			if (intercept.isDirection(InterceptOption.Direction.REQUEST)) {
				switch (intercept.getRelationship()) {
					case IS_INTERCEPTED_IF_IT_MATCHES :
						if (intercept.match(client_packet, null)) {
							return true;
						}
						break;
					case IS_NOT_INTERCEPTED_IF_IT_MATCHES :
						if (intercept.match(client_packet, null)) {
							return false;
						}
						break;
					default :
						break;
				}
			} else if (intercept.isDirection(Direction.ALL_THE_OTHER_REQUESTS)) {
				switch (intercept.getRelationship()) {
					case ARE_INTERCEPTED :
						return true;
					case ARE_NOT_INTERCEPTED :
						return false;
					default :
						return true;
				}
			}
		}
		return true;
	}

	public boolean interceptOnResponse(Server server, Packet client_packet, Packet server_packet) throws Exception {
		for (InterceptOption intercept : queryEnabled(server)) {
			if (intercept.getDirection() == InterceptOption.Direction.RESPONSE) {
				switch (intercept.getRelationship()) {
					case IS_INTERCEPTED_IF_IT_MATCHES :
						if (intercept.match(client_packet, server_packet)) {
							return true;
						}
						break;
					case IS_NOT_INTERCEPTED_IF_IT_MATCHES :
						if (intercept.match(client_packet, server_packet)) {
							return false;
						}
						break;
					case IS_INTERCEPTED_IF_REQUEST_WAS_INTERCEPTED :
						// パケットにフラグを立てた方がいいけどDBが変わるので一旦これで
						if (interceptOnRequest(server, client_packet)) {
							return true;
						}
						break;
					default :
						break;
				}
			} else if (intercept.isDirection(Direction.ALL_THE_OTHER_RESPONSES)) {
				switch (intercept.getRelationship()) {
					case ARE_INTERCEPTED :
						return true;
					case ARE_NOT_INTERCEPTED :
						return false;
					default :
						break;
				}
			}
		}
		return true;
	}

	public void firePropertyChange() {
		firePropertyChange(null);
	}

	public void firePropertyChange(Object arg) {
		pcs.firePropertyChange(INTERCEPT_OPTIONS.toString(), null, arg);
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
					dao = database.createTable(InterceptOption.class, this);
					cache.clear();
					firePropertyChange(message);
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(InterceptOption.class, this);
					cache.clear();
					break;
				default :
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='interceptOptions'").getFirstResult()[0];
		// System.out.println(result);
		return result.equals(
				"CREATE TABLE `interceptOptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `direction` VARCHAR , `type` VARCHAR , `relationship` VARCHAR , `method` VARCHAR , `pattern` VARCHAR , `server_id` INTEGER , UNIQUE (`direction`,`type`,`relationship`,`method`,`pattern`,`server_id`) )");
	}

	public void setEnabled(boolean enabled) throws Exception {
		this.enabled.setState(enabled);
	}

	public boolean isEnabled() throws Exception {
		return this.enabled.getState();
	}

	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null, "InterceptOptionsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(InterceptOption.class);
			dao = database.createTable(InterceptOption.class, this);
		}
	}
}
