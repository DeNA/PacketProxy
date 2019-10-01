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
import packetproxy.common.Logger;
import packetproxy.model.Database.DatabaseMessage;
import packetproxy.util.PacketProxyUtility;

public class Packets extends Observable implements Observer {
	private static Packets instance;
	
	public static Packets getInstance(boolean restore) throws Exception {
		if (instance == null) {
			instance = new Packets(restore);
		}
		return instance;
	}

	public static Packets getInstance() throws Exception {
		if (instance == null) {
			// 初期化前に通信が走り実行されるとDrop Tableされるので例外を投げる
			throw new Exception("Packets インスタンスが作成されていません。");
		}
		return instance;
	}
	
	private PacketProxyUtility util;
	private Database database;
	private Dao<Packet,Integer> dao;

	private Packets(boolean restore) throws Exception {
		util = PacketProxyUtility.getInstance();
		database = Database.getInstance();
		if(!restore){
			util.packetProxyLog("drop history...");
			database.dropPacketTableFaster();
		}
		dao = database.createTable(Packet.class, this);
		if(restore){
			if (!isLatestVersion()) {
				RecreateTable();
			}
			util.packetProxyLog("load history...");
			util.packetProxyLog("load" + dao.countOf() + " records.");
		}
	}
	// TODO できれば非同期でやる（大きいデータのときに数秒止まってしまうので）
	public void create(Packet packet) throws Exception {
		synchronized (dao) {
			dao.createIfNotExists(packet);
		}
		notifyObservers();
	}
	public void refresh() {
		notifyObservers();
	}
	public void update(Packet packet) throws Exception {
		if(database.isAlertFileSize()){
			notifyObservers(true);
		}
		Dao.CreateOrUpdateStatus status;
		synchronized (dao) {
			status = dao.createOrUpdate(packet);
		}
		if (status.isCreated()) {
			notifyObservers(packet.getId() * -1);
		} else {
			notifyObservers(packet.getId());
		}
	}
	public void deleteAll() throws Exception {
		synchronized (dao) {
			dao.deleteBuilder().delete();
		}
		notifyObservers();
	}
	public void delete(Packet packet) throws Exception {
		synchronized (dao) {
			dao.delete(packet);
		}
		notifyObservers();
	}
	public long countOf() throws Exception {
		return dao.countOf();
	}
	public Packet query(int id) throws Exception {
		return dao.queryForId(id);
	}
	
	public List<Packet> queryAllIds() throws Exception {
		return dao.queryBuilder().selectColumns("id").orderBy("id", true).query();
	}

	public List<Packet> queryRange(long offset, long limit) throws Exception {
		return dao.queryBuilder().offset(offset).limit(limit).orderBy("id", true).query();
	}

	public List<Packet> queryAll() throws Exception {
		return dao.queryBuilder().orderBy("id", true).query();
	}
	public List<Packet> queryMoreThan(int date) throws Exception {
		return dao.queryBuilder().where().gt("id", date).query();
	}
	public List<Packet> queryFullText(String search, int start) throws Exception {
		return dao.queryBuilder().selectColumns("group").where()
				.ge("id", start)
				.and()
				.like("decoded_data",String.format("%%%s%%", search)).query();
	}
	public List<Packet> queryFullTextById(String search, int id) throws Exception {
		return dao.queryBuilder().selectColumns("group").where()
				.eq("id", id)
				.and()
				.like("decoded_data",String.format("%%%s%%", search)).query();
	}
	//case sensitive full text search
	public List<Packet> queryFullText(String search) throws Exception {
		//ORMLite does not support glob statement.
		String query = String.format("SELECT `group`,`id` FROM `packets` WHERE `decoded_data` GLOB '*%s*';", search);
		return dao.queryRaw(query,dao.getRawRowMapper()).getResults();
	}
	//case insensitive full text search
	public List<Packet> queryFullText_i(String search) throws Exception {
		return dao.queryBuilder().selectColumns("group").where()
				.like("decoded_data",String.format("%%%s%%", search)).query();
	}

	@Override
	public void notifyObservers(Object arg) {
		setChanged();
		super.notifyObservers(arg);
		clearChanged();
	}
	public String outputAllPackets(String filename) throws Exception {
		Logger logger = new Logger(queryAll());
		return logger.outputToFile(filename);
	}
	public boolean isEmpty() throws Exception {
		return dao.queryBuilder().limit(1L).query().isEmpty();
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
				dao = database.createTable(Packet.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(Packet.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='packets'").getFirstResult()[0];
		//System.out.println(result);
		return result.equals("CREATE TABLE `packets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `direction` VARCHAR , `decoded_data` BLOB , `modified_data` BLOB , `sent_data` BLOB , `received_data` BLOB , `listen_port` INTEGER , `client_ip` VARCHAR , `client_port` INTEGER , `server_ip` VARCHAR , `server_name` VARCHAR , `server_port` INTEGER , `use_ssl` BOOLEAN , `content_type` VARCHAR , `encoder_name` VARCHAR , `modified` BOOLEAN , `resend` BOOLEAN , `date` BIGINT , `conn` INTEGER , `group` BIGINT )");
	}
	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"packetsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(Packet.class);
			dao = database.createTable(Packet.class, this);
		}
	}
}
