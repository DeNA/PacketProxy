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
import static packetproxy.model.PropertyChangeEventType.LISTEN_PORTS;
import static packetproxy.util.Logging.errWithStackTrace;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.stream.Collectors;
import packetproxy.model.Database.DatabaseMessage;

public class ListenPorts implements PropertyChangeListener {

	private static ListenPorts instance;
	private PropertyChangeSupport changes = new PropertyChangeSupport(this);

	public static ListenPorts getInstance() throws Exception {
		if (instance == null) {

			instance = new ListenPorts();
		}
		return instance;
	}

	private Database database;
	private Dao<ListenPort, Integer> dao;
	private Servers servers;

	private ListenPorts() throws Exception {
		database = Database.getInstance();
		servers = Servers.getInstance();
		dao = database.createTable(ListenPort.class, this);
	}

	public void create(ListenPort listen) throws Exception {
		if (isAlreadyEnabled(listen)) { // 他ポートが既にListenしていたら、Enableにさせない

			listen.setDisabled();
		}
		dao.createIfNotExists(listen);
		firePropertyChange();
	}

	public void delete(int id) throws Exception {
		dao.deleteById(id);
		firePropertyChange();
	}

	public void delete(ListenPort listen) throws Exception {
		dao.delete(listen);
		firePropertyChange();
	}

	public void update(ListenPort listen) throws Exception {
		if (listen.isEnabled() && isAlreadyEnabled(listen))
			return;
		dao.update(listen);
		firePropertyChange();
	}

	public void refresh() {
		firePropertyChange();
	}

	public ListenPort query(int id) throws Exception {
		return dao.queryForId(id);
	}

	public List<ListenPort> queryAll() throws Exception {
		return dao.queryBuilder().orderBy("port", true).query();
	}

	public List<ListenPort> queryEnabled() throws Exception {
		return dao.queryBuilder().where().eq("enabled", true).query();
	}

	public boolean isAlreadyEnabled(ListenPort port) throws Exception {
		return dao.queryBuilder().where().ne("id", port.getId()).and().eq("port", port.getPort()).and()
				.eq("enabled", true).query().stream()
				.anyMatch(listenPort -> listenPort.getProtocol() == port.getProtocol());
	}

	public ListenPort queryEnabledByPort(ListenPort.Protocol protocol, int port) throws Exception {
		List<ListenPort> rets = dao.queryBuilder().where().eq("port", port).and().eq("enabled", true).query().stream()
				.filter(listenPort -> listenPort.getProtocol() == protocol).collect(Collectors.toList());
		return rets.size() > 0 ? rets.get(0) : null;
	}

	public ListenPort queryByPortServer(ListenPort.Protocol protocol, int port, int server_id) throws Exception {
		List<ListenPort> rets = dao.queryBuilder().where().eq("port", port).and().eq("server_id", server_id).query()
				.stream().filter(listenPort -> listenPort.getProtocol() == protocol).collect(Collectors.toList());
		return rets.size() > 0 ? rets.get(0) : null;
	}

	public ListenPort queryByHttpProxyPort(int port) throws Exception {
		List<ListenPort> rets = dao.queryBuilder().where().eq("type", ListenPort.TYPE.HTTP_PROXY).and().eq("port", port)
				.query();
		return rets.size() > 0 ? rets.get(0) : null;
	}

	public List<ListenPort> queryEnabledHttpProxis() throws Exception {
		return dao.queryBuilder().where().eq("type", ListenPort.TYPE.HTTP_PROXY).and().eq("enabled", true).query();
	}

	public List<ListenPort> queryAllOfHttpProxis() throws Exception {
		return dao.queryBuilder().where().eq("type", ListenPort.TYPE.HTTP_PROXY).query();
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		changes.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changes.removePropertyChangeListener(listener);
	}

	private void firePropertyChange() {
		changes.firePropertyChange(LISTEN_PORTS.toString(), null, null);
	}

	private void firePropertyChange(Object value) {
		changes.firePropertyChange(LISTEN_PORTS.toString(), null, value);
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
					dao = database.createTable(ListenPort.class, this);
					firePropertyChange(message);
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(ListenPort.class, this);
					break;
				default :
					break;
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}
}
