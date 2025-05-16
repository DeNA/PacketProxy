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
import packetproxy.model.Database.DatabaseMessage;

public class Filters extends Observable implements Observer {

	private static Filters instance;

	public static Filters getInstance() throws Exception {
		if (instance == null) {
			instance = new Filters();
		}
		return instance;
	}

	private Database database;
	private Dao<Filter, Integer> dao;

	private Filters() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(Filter.class, this);
		if (!isLatestVersion()) {
			RecreateTable();
		}
	}

	public void create(Filter filter) throws Exception {
		dao.createIfNotExists(filter);
		notifyObservers();
	}

	public void delete(Filter filter) throws Exception {
		dao.delete(filter);
		notifyObservers();
	}

	public void deleteByName(String name) throws Exception {
		dao.delete(queryByName(name));
		notifyObservers();
	}

	public Filter query(int id) throws Exception {
		return dao.queryForId(id);
	}

	public List<Filter> queryByName(String name) throws Exception {
		return dao.queryBuilder().where().eq("name", name).query();
	}

	public List<Filter> queryAll() throws Exception {
		return dao.queryBuilder().orderBy("id", false).query();
	}

	public void update(Filter filter) throws Exception {
		dao.update(filter);
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
		DatabaseMessage message = (DatabaseMessage) arg;
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
					dao = database.createTable(Filter.class, this);
					notifyObservers(arg);
					break;
				case RECREATE:
					database = Database.getInstance();
					dao = database.createTable(Filter.class, this);
					break;
				default:
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='filters'").getFirstResult()[0];
		// System.out.println(result);
		return result.equals(
				"CREATE TABLE `filters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `name` VARCHAR , `filter` VARCHAR ,  UNIQUE (`name`))");
	}

	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"filtersテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(Filter.class);
			dao = database.createTable(Filter.class, this);
		}
	}
}
