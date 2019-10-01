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

import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.JOptionPane;
import packetproxy.common.ClientKeyManager;
import packetproxy.model.Database.DatabaseMessage;
import com.j256.ormlite.dao.Dao;

/**
 * DAO for ClientCertificate
 */
public class ClientCertificates extends Observable implements Observer
{
	private static ClientCertificates instance;

	public static ClientCertificates getInstance() throws Exception {
		if (instance == null) {
			instance = new ClientCertificates();
		}
		return instance;
	}
	
	private Database database;
	private Dao<ClientCertificate,Integer> dao;
	private Servers servers;

	private ClientCertificates() throws Exception {
		database = Database.getInstance();
		servers = Servers.getInstance();
		dao = database.createTable(ClientCertificate.class, this);
		if (!isLatestVersion()) {
			RecreateTable();
		}
	}
	public void create(ClientCertificate certificate) throws Exception {
		certificate.setEnabled();
		dao.createIfNotExists(certificate);
		ClientKeyManager.setKeyManagers(certificate.getServer(), certificate.load());
		notifyObservers();
	}
	public void delete(ClientCertificate certificate) throws Exception {
		dao.delete(certificate);
		ClientKeyManager.removeKeyManagers(certificate.getServer());
		notifyObservers();
	}
	public void update(ClientCertificate certificate) throws Exception {
		dao.update(certificate);
		if(certificate.isEnabled())
			ClientKeyManager.setKeyManagers(certificate.getServer(), certificate.load());
		else
			ClientKeyManager.removeKeyManagers(certificate.getServer());
		notifyObservers();
	}
	public void refresh() {
		notifyObservers();
	}
	public ClientCertificate query(int id) throws Exception {
		return dao.queryForId(id);
	}
	public List<ClientCertificate> queryAll() throws Exception {
		return dao.queryBuilder().query();
	}

	public List<ClientCertificate> queryEnabled() throws Exception {
		return dao.queryBuilder().where()
				.eq("enabled", true)
				.query();
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
				dao = database.createTable(ClientCertificate.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(ClientCertificate.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='clientCertificates'").getFirstResult()[0];
		return result.equals("CREATE TABLE `clientCertificates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `type` VARCHAR , `serverId` INTEGER , `subject` VARCHAR , `issuer` VARCHAR , `path` VARCHAR , `storePassword` VARCHAR , `keyPassword` VARCHAR , UNIQUE (`type`,`serverId`,`path`) )");
	}
	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"client_certificatesテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(ClientCertificate.class);
			dao = database.createTable(ClientCertificate.class, this);
		}
	}
}
