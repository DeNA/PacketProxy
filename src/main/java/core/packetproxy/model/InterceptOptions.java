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

public class InterceptOptions extends Observable implements Observer
{
	private static InterceptOptions incetance;
	
	public static InterceptOptions getInstance() throws Exception {
		if (incetance == null) {
			incetance = new InterceptOptions();
		}
		return incetance;
	}
	
	private Database database;
	private Dao<InterceptOption,Integer> dao;
	private Servers servers;
	
	private InterceptOptions() throws Exception {
		database = Database.getInstance();
		servers = Servers.getInstance();
		dao = database.createTable(InterceptOption.class, this);
		if (!isLatestVersion()) {
			RecreateTable();
		}
	}
	public void create(InterceptOption intercept_option) throws Exception {
		intercept_option.setEnabled();
		dao.createIfNotExists(intercept_option);
		notifyObservers();
	}
	public void delete(int id) throws Exception {
		dao.deleteById(id);
		notifyObservers();
	}
	public void delete(InterceptOption intercept_option) throws Exception {
		dao.delete(intercept_option);
		notifyObservers();
	}
	public void update(InterceptOption intercept_option) throws Exception {
		dao.update(intercept_option);
		notifyObservers();
	}
	public void refresh() {
		notifyObservers();
	}
	public InterceptOption query(int id) throws Exception {
		return dao.queryForId(id);
	}
	public List<InterceptOption> queryAll() throws Exception {
		return dao.queryBuilder().query();
	}

	public List<InterceptOption> queryEnabled(Server server) throws Exception {
		int server_id = InterceptOption.ALL_SERVER;
		if (server != null) { server_id = server.getId(); }
		return dao.queryBuilder().where()
				.eq("server_id",  server_id)
				.or()
				.eq("server_id",  InterceptOption.ALL_SERVER)
				.and()
				.eq("enabled", true)
				.query();
	}
	public boolean interceptOnRequest(Server server, Packet client_packet) throws Exception {
		for (InterceptOption intercept : queryEnabled(server)) {
			if (intercept.getDirection() == InterceptOption.Direction.CLIENT_REQUEST) {
				if (intercept.getRelationship() == InterceptOption.Relationship.WAS_INTERCEPTED) { continue; }
				if (!intercept.match(client_packet, null)) { return false; }
			}
		}
		return true;
	}
	public boolean interceptOnResponse(Server server, Packet client_packet, Packet server_packet) throws Exception {
		for (InterceptOption intercept : queryEnabled(server)) {
			if (intercept.getDirection() == InterceptOption.Direction.SERVER_RESPONSE) {
				if (intercept.getRelationship() == InterceptOption.Relationship.WAS_INTERCEPTED) {
					 // パケットにフラグを立てた方がいいけどDBが変わるので一旦これで
					if (!interceptOnRequest(server, client_packet)) { return false; }
				} else if (!intercept.match(client_packet, server_packet)) {
					return false;
				}
			}
		}
		return true;
	}
	@Override
	public void notifyObservers(Object arg) {
		setChanged();
		super.notifyObservers(arg);
		clearChanged();
	}
	@Override
	public void addObserver(Observer observer) {
		super.addObserver(observer);
		servers.addObserver(observer);
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
				dao = database.createTable(InterceptOption.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(InterceptOption.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='interceptOptions'").getFirstResult()[0];
		// System.out.println(result);
		return result.equals("CREATE TABLE `interceptOptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `direction` VARCHAR , `type` VARCHAR , `relationship` VARCHAR , `method` VARCHAR , `pattern` VARCHAR , `server_id` INTEGER , UNIQUE (`direction`,`type`,`relationship`,`method`,`pattern`,`server_id`) )");
	}
	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"InterceptOptionsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(InterceptOption.class);
			dao = database.createTable(InterceptOption.class, this);
		}
	}
}
