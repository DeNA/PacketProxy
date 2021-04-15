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
import packetproxy.model.DaoQueryCache;
import packetproxy.model.Database.DatabaseMessage;

public class Configs extends Observable implements Observer {

	private static Configs instance;
	
	public static Configs getInstance() throws Exception {
		if (instance == null) {
			instance = new Configs();
		}
		return instance;
	}
	
	private Database database;
	private Dao<Config,String> dao;
	private DaoQueryCache<Config> cache;
	
	private Configs() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(Config.class, this);
		cache = new DaoQueryCache();
	}
	public void create(Config config) throws Exception {
		dao.createIfNotExists(config);
		cache.clear();
		notifyObservers();
	}
	public void delete(Config config) throws Exception {
		dao.delete(config);
		cache.clear();
		notifyObservers();
	}
	public Config query(String key) throws Exception {
		List<Config> ret = cache.query("query", key);
		if (ret != null) { return ret.get(0); }

		Config config = dao.queryForId(key);

		cache.set("query", key, config);
		return config;
	}
	public List<Config> queryAll() throws Exception {
		List<Config> ret = cache.query("queryAll", 0);
		if (ret != null) { return ret; }

		ret = dao.queryForAll();

		cache.set("queryAll", 0, ret);
		return ret;
	}
	public void update(Config config) throws Exception {
		dao.update(config);
		cache.clear();
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
				instance = null;
				break;
			case RECONNECT:
				database = Database.getInstance();
				dao = database.createTable(Config.class, this);
				cache.clear();
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(Config.class, this);
				cache.clear();
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
