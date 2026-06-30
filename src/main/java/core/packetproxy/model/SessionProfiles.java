/*
 * Copyright 2026 DeNA Co., Ltd.
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
import static packetproxy.model.PropertyChangeEventType.SESSION_PROFILES;
import static packetproxy.util.Logging.errWithStackTrace;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import packetproxy.model.Database.DatabaseMessage;

public class SessionProfiles implements PropertyChangeListener {

	private static SessionProfiles instance;
	private final PropertyChangeSupport changes = new PropertyChangeSupport(this);

	private Database database;
	private Dao<SessionProfile, Integer> dao;

	public static SessionProfiles getInstance() throws Exception {
		if (instance == null) {
			instance = new SessionProfiles();
		}
		return instance;
	}

	private SessionProfiles() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(SessionProfile.class, this);
	}

	public void create(SessionProfile profile) throws Exception {
		dao.create(profile);
		firePropertyChange();
	}

	public void delete(SessionProfile profile) throws Exception {
		dao.delete(profile);
		firePropertyChange();
	}

	public void update(SessionProfile profile) throws Exception {
		dao.update(profile);
		firePropertyChange();
	}

	public SessionProfile query(int id) throws Exception {
		return dao.queryForId(id);
	}

	public SessionProfile queryByName(String name) throws Exception {
		return dao.queryBuilder().where().eq("name", name).queryForFirst();
	}

	public List<SessionProfile> queryAll() throws Exception {
		return dao.queryBuilder().orderBy("name", true).query();
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		changes.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changes.removePropertyChangeListener(listener);
	}

	private void firePropertyChange() {
		changes.firePropertyChange(SESSION_PROFILES.toString(), null, null);
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
					break;
				case RESUME :
					break;
				case DISCONNECT_NOW :
					break;
				case RECONNECT :
					database = Database.getInstance();
					dao = database.createTable(SessionProfile.class, this);
					firePropertyChange();
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(SessionProfile.class, this);
					break;
				default :
					break;
			}
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}
}
