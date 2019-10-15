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

import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class CharSets extends Observable implements Observer {

	private static CharSets instance;
	private List<String> defaultCharSetList = Arrays.asList(new String[]{"UTF-8", "Shift_JIS", "x-euc-jp-linux", "ISO-2022-JP", "ISO-8859-1"});

	public static CharSets getInstance() throws Exception {
		if (instance == null) {
			instance = new CharSets();
		}
		return instance;
	}

	private Database database;
	private Dao<CharSet,Integer> dao;

	private CharSets() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(CharSet.class, this);
		for(String charSetName:defaultCharSetList){
			if(null==queryByCharSetName(charSetName)){
				create(new CharSet(charSetName));
			}
		}
	}
	public void create(CharSet charset) throws Exception {
		dao.createIfNotExists(charset);
		notifyObservers();
	}
	public void delete(CharSet charset) throws Exception {
		dao.delete(charset);
		notifyObservers();
	}
	public CharSet queryByString(String str) throws Exception {
		List<CharSet> all = this.queryAll();
		for (CharSet server : all) {
			if (server.toString().equals(str)) {
				return server;
			}
		}
		return null;
	}
	public CharSet queryByCharSetName(String charsetname) throws Exception {
		return dao.queryBuilder().where().eq("charsetname", charsetname).queryForFirst();
	}
	public CharSet query(int id) throws Exception {
		return dao.queryForId(id);
	}
	public List<CharSet> queryAll() throws Exception {
		return dao.queryBuilder().orderBy("charsetname", true).query();
	}
	public void update(List<CharSet> charsets) throws Exception {
		for(CharSet charset:charsets){
			dao.update(charset);
			notifyObservers();
		}
	}
	public void update(CharSet charset) throws Exception {
		dao.update(charset);
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
				dao = database.createTable(CharSet.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(CharSet.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
