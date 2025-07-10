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

import static packetproxy.model.PropertyChangeEventType.RESOLUTIONS_UPDATED;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import packetproxy.model.Database.DatabaseMessage;
import packetproxy.util.PacketProxyUtility;

public class Resolutions implements PropertyChangeListener {

	private static Resolutions instance;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public static Resolutions getInstance() throws Exception {
		if (instance == null) {

			instance = new Resolutions();
		}
		return instance;
	}

	private Database database;
	private Dao<Resolution, Integer> dao;
	private DaoQueryCache<Resolution> cache;

	private Resolutions() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(Resolution.class, this);
		cache = new DaoQueryCache();
		if (dao.countOf() == 0) {

			setResolutionsBySystem();
		}
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public void setResolutionsBySystem() throws Exception {
		List<String> fileLines;
		if (PacketProxyUtility.getInstance().isWindows()) {

			fileLines = Files.readAllLines(Paths.get("C:\\Windows\\System32\\drivers\\etc\\hosts"));
		} else {

			fileLines = Files.readAllLines(Paths.get("/etc/hosts"));
		}
		fileLines.stream().forEach(line -> {

			if (!(line.startsWith("#"))) {

				try {

					String[] parts = line.split("[\\s]+");
					if (parts.length >= 2) {

						String ip = parts[0];
						String hostname = parts[1];
						create(new Resolution(ip, hostname));
					}
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
	}

	public void create(Resolution resolution) throws Exception {
		dao.createIfNotExists(resolution);
		cache.clear();
		firePropertyChange();
	}

	public void delete(Resolution resolution) throws Exception {
		dao.delete(resolution);
		cache.clear();
		firePropertyChange();
	}

	public Resolution queryByString(String str) throws Exception {
		List<Resolution> all = this.queryAll();
		for (Resolution resolution : all) {

			if (resolution.toString().equals(str)) {

				return resolution;
			}
		}
		return null;
	}

	public Resolution queryByHostName(String hostname) throws Exception {
		List<Resolution> ret = cache.query("queryByHostName", hostname);
		if (ret != null) {

			return ret.get(0);
		}

		Resolution resolution = dao.queryBuilder().where().eq("ip", hostname).queryForFirst();

		cache.set("queryByHostName", hostname, resolution);
		return resolution;
	}

	public Resolution query(int id) throws Exception {
		List<Resolution> ret = cache.query("query", id);
		if (ret != null) {

			return ret.get(0);
		}

		Resolution resolution = dao.queryForId(id);

		cache.set("query", id, resolution);
		return resolution;
	}

	public List<Resolution> queryAll() throws Exception {
		List<Resolution> ret = cache.query("queryAll", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().orderBy("ip", true).query();

		cache.set("queryAll", 0, ret);
		return ret;
	}

	public List<Resolution> queryEnabled() throws Exception {
		List<Resolution> ret = cache.query("queryEnabled", 0);
		if (ret != null) {

			return ret;
		}

		ret = dao.queryBuilder().where().eq("enabled", true).query();

		cache.set("queryEnabled", 0, ret);
		return ret;
	}

	public void update(Resolution resolution) throws Exception {
		dao.update(resolution);
		cache.clear();
		firePropertyChange();
	}

	private void firePropertyChange() {
		pcs.firePropertyChange(RESOLUTIONS_UPDATED.toString(), null, null);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!(evt.getSource() instanceof Database)) {

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
					dao = database.createTable(Resolution.class, this);
					cache.clear();
					firePropertyChange();
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(Resolution.class, this);
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
