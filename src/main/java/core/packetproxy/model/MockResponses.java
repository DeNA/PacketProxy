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
import packetproxy.model.Database.DatabaseMessage;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class MockResponses extends Observable implements Observer {

	private static MockResponses instance;

	public static MockResponses getInstance() throws Exception {
		if (instance == null) {
			instance = new MockResponses();
		}
		return instance;
	}

	private Database database;
	private Dao<MockResponse,Integer> dao;

	private MockResponses() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(MockResponse.class, this);
	}
	public void create(MockResponse mockResponse) throws Exception {
		if (isAlreadyEnabled(mockResponse) == true) {
			mockResponse.setDisabled();
		} else {
			mockResponse.setEnabled();
		}
		dao.createIfNotExists(mockResponse);
		notifyObservers();
	}
	public void delete(MockResponse mockResponse) throws Exception {
		dao.delete(mockResponse);
		notifyObservers();
	}
	public MockResponse query(int id) throws Exception {
		return dao.queryForId(id);
	}
	public List<MockResponse> queryAll() throws Exception {
		return dao.queryBuilder().orderBy("ip", true).query();
	}

	public List<MockResponse> queryEnabled() throws Exception {
		return dao.queryBuilder().where().eq("enabled", true).query();
	}
	public boolean isAlreadyEnabled(MockResponse port) throws Exception {
		return (dao.queryBuilder().where()
				.ne("id", port.getId())
				.and()
				.eq("port", port.getPort())
				.and()
				.eq("enabled", true).countOf() > 0) ? true : false;
	}
	public void update(MockResponse mockResponse) throws Exception {
		if (mockResponse.isEnabled() && isAlreadyEnabled(mockResponse))
			return;
		dao.update(mockResponse);
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
				dao = database.createTable(MockResponse.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(MockResponse.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
