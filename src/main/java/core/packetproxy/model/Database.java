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

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.logger.LocalLog;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import packetproxy.util.PacketProxyUtility;

public class Database {
	private static Database instance;
	private PropertyChangeSupport changes = new PropertyChangeSupport(this);

	public static Database getInstance() throws Exception {
		if (instance == null) {
			instance = new Database();
		}
		return instance;
	}

	private static int ALERT_DB_FILE_SIZE_MB = 1536;// 1.5GB (LIMIT = 2GB)
	private Path databaseDir = Paths.get(System.getProperty("user.home") + "/.packetproxy/db");// FileSystems.getDefault().getPath("db");
	private Path databasePath = Paths.get(databaseDir.toString() + "/resources.sqlite3");
	private ConnectionSource source;

	private Database() throws Exception {
		createDB();
	}

	private void createDB() throws Exception {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		if (!Files.exists(databaseDir)) {
			util.packetProxyLog(databaseDir.toAbsolutePath() + " directory is not found...");
			util.packetProxyLog("creating the directory...");
			Files.createDirectories(databaseDir);
			System.out.println("success!");
		} else {
			if (!Files.isDirectory(databaseDir)) {
				util.packetProxyLogErr(databaseDir.toAbsolutePath() + " file is not directory...");
				util.packetProxyLogErr("Must be a directory");
				System.exit(1);
			}
		}

		System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "error");
		source = new JdbcConnectionSource(getDatabaseURL());
		DatabaseConnection conn = source.getReadWriteConnection();
		conn.executeStatement("pragma auto_vacuum = full", DatabaseConnection.DEFAULT_RESULT_FLAGS);
	}

	public <T, ID> Dao<T, ID> createTable(Class<T> c, PropertyChangeListener listener) throws Exception {
		addPropertyChangeListener(listener);
		return createTable(c);
	}

	public <T, ID> Dao<T, ID> createTable(Class<T> c) throws Exception {
		TableUtils.createTableIfNotExists(source, c);
		Dao<T, ID> dao = DaoManager.createDao(source, c);
		return dao;
	}

	public void dropFilters() throws Exception {
		firePropertyChange(DatabaseMessage.DISCONNECT_NOW);

		dropTable(Filter.class);

		createTable(Filter.class, Filters.getInstance());

		firePropertyChange(DatabaseMessage.RECONNECT);
	}

	public void dropConfigs() throws Exception {
		firePropertyChange(DatabaseMessage.DISCONNECT_NOW);

		dropTable(ListenPort.class);
		dropTable(Server.class);
		dropTable(Modification.class);
		dropTable(SSLPassThrough.class);

		createTable(ListenPort.class, ListenPorts.getInstance());
		createTable(Server.class, Servers.getInstance());
		createTable(Modification.class, Modifications.getInstance());
		createTable(SSLPassThrough.class, SSLPassThroughs.getInstance());

		firePropertyChange(DatabaseMessage.RECONNECT);
	}

	public void dropPacketTableFaster() throws Exception {
		Path src = Paths.get(instance.databasePath.getParent().toAbsolutePath().toString() + "/tmp.sqlite3");
		Path dst = instance.databasePath.toAbsolutePath();
		firePropertyChange(DatabaseMessage.DISCONNECT_NOW);
		DatabaseConnection conn = source.getReadWriteConnection();
		conn.close();
		Files.move(dst, src, StandardCopyOption.REPLACE_EXISTING);

		createDB();
		createTable(Filter.class, Filters.getInstance());
		createTable(ListenPort.class, ListenPorts.getInstance());
		createTable(Config.class, Configs.getInstance());
		createTable(Server.class, Servers.getInstance());
		createTable(ClientCertificate.class, ClientCertificates.getInstance());
		createTable(InterceptOption.class, InterceptOptions.getInstance());
		createTable(Modification.class, Modifications.getInstance());
		createTable(SSLPassThrough.class, SSLPassThroughs.getInstance());
		createTable(CharSet.class, CharSets.getInstance());
		createTable(ResenderPacket.class, ResenderPackets.getInstance());
		firePropertyChange(DatabaseMessage.RECREATE);

		migrateTableWithoutHistory(src, dst);
		firePropertyChange(DatabaseMessage.RECONNECT);
		Files.delete(src);
	}

	public <T> void dropTable(Class<T> c) throws Exception {
		if (c == Packet.class) {
			dropPacketTableFaster();
		} else {
			TableUtils.dropTable(source, c, true);
		}
	}

	private static void migrateTableWithoutHistory(Path srcDBPath, Path dstDBPath) {
		try {
			ConnectionSource source = new JdbcConnectionSource("jdbc:sqlite:" + srcDBPath);
			DatabaseConnection conn = source.getReadWriteConnection();
			conn.executeStatement("attach database '" + dstDBPath.toAbsolutePath() + "' as 'dstDB'",
					DatabaseConnection.DEFAULT_RESULT_FLAGS);
			conn.executeStatement("attach database '" + srcDBPath.toAbsolutePath() + "' as 'srcDB'",
					DatabaseConnection.DEFAULT_RESULT_FLAGS);
			String queries[] = {"DELETE FROM dstDB.interceptOptions", "DELETE FROM dstDB.charsets",
					"INSERT OR REPLACE INTO dstDB.filters (id, name, filter) SELECT id, name, filter FROM srcDB.filters",
					"INSERT OR REPLACE INTO dstDB.listenports (id, enabled, ca_name, port, type, server_id) SELECT id, enabled, ca_name, port, type, server_id FROM srcDB.listenports",
					"INSERT OR REPLACE INTO dstDB.configs (key, value) SELECT key, value FROM srcDB.configs",
					"INSERT OR REPLACE INTO dstDB.servers (id, ip, port, encoder, use_ssl, resolved_by_dns, resolved_by_dns6, http_proxy, comment) SELECT id, ip, port, encoder, use_ssl, resolved_by_dns, resolved_by_dns6, http_proxy, comment FROM srcDB.servers",
					"INSERT OR REPLACE INTO dstDB.clientCertificates (id, enabled, type, serverId, subject, issuer, path, storePassword, keyPassword) SELECT id, enabled, type, serverId, subject, issuer, path, storePassword, keyPassword FROM srcDB.clientCertificates",
					"INSERT OR REPLACE INTO dstDB.interceptOptions (id, enabled, direction, type, relationship, method, pattern, server_id) SELECT id, enabled, direction, type, relationship, method, pattern, server_id FROM srcDB.interceptOptions",
					"INSERT OR REPLACE INTO dstDB.modifications (id, enabled, server_id, direction, pattern, method, replaced) SELECT id, enabled, server_id, direction, pattern, method, replaced FROM srcDB.modifications",
					"INSERT OR REPLACE INTO dstDB.sslpassthroughs (id, enabled, server_name, listen_port) SELECT id, enabled, server_name, listen_port FROM srcDB.sslpassthroughs",
					"INSERT OR REPLACE INTO dstDB.charsets (id, charsetname) SELECT id, charsetname FROM srcDB.charsets",
					"INSERT OR REPLACE INTO dstDB.resender_packets (id, resends_index, resend_index, direction, data, listen_port, client_ip, client_port, server_ip, server_port, server_name, use_ssl, encoder_name, alpn, auto_modified, conn, `group`) SELECT id, resends_index, resend_index, direction, data, listen_port, client_ip, client_port, server_ip, server_port, server_name, use_ssl, encoder_name, alpn, auto_modified, conn, `group` FROM srcDB.resender_packets",};
			for (String query : queries) {
				try {
					conn.executeStatement(query, DatabaseConnection.DEFAULT_RESULT_FLAGS);
				} catch (Exception e) {
					PacketProxyUtility.getInstance().packetProxyLog(
							"Database format may have been changed. Simply ignore this type of errors.");
					PacketProxyUtility.getInstance().packetProxyLog("[Error] %s", query);
				}
			}
			conn.close();
		} catch (Exception e) {
			e.getStackTrace();
		}
	}

	public void close() throws Exception {
		source.close();
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		changes.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changes.removePropertyChangeListener(listener);
	}

	private void firePropertyChange(DatabaseMessage message) {
		changes.firePropertyChange(DATABASE_MESSAGE.toString(), null, message);
	}

	public enum DatabaseMessage {
		PAUSE, RESUME, DISCONNECT_NOW, RECONNECT, RECREATE,
	}

	// TODO 保存中にファイルが更新されない様にする
	public void Save(String path) throws Exception {
		firePropertyChange(DatabaseMessage.PAUSE);

		Path src = databasePath;
		Path dest = FileSystems.getDefault().getPath(path);
		Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);

		firePropertyChange(DatabaseMessage.RESUME);
	}

	public void Load(String path) throws Exception {
		firePropertyChange(DatabaseMessage.DISCONNECT_NOW);
		source.close();

		Path src = FileSystems.getDefault().getPath(path);
		Path dest = FileSystems.getDefault().getPath(databaseDir.toString() + "/resources_temp.sqlite3");
		Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
		databasePath = dest;
		source = new JdbcConnectionSource(getDatabaseURL());

		firePropertyChange(DatabaseMessage.RECONNECT);
	}

	public void saveWithoutLog(String path) throws Exception {
		firePropertyChange(DatabaseMessage.PAUSE);

		Path src = databasePath;
		Path dest = FileSystems.getDefault().getPath(path);
		Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
		JdbcConnectionSource new_db = new JdbcConnectionSource("jdbc:sqlite:" + dest);
		DatabaseConnection conn = new_db.getReadWriteConnection();
		conn.executeStatement("delete from packets", DatabaseConnection.DEFAULT_RESULT_FLAGS);
		conn.close();
		new_db.close();

		firePropertyChange(DatabaseMessage.RESUME);
	}

	public void LoadAndReplace(String path) throws Exception {
		firePropertyChange(DatabaseMessage.DISCONNECT_NOW);

		source.close();
		Path src = FileSystems.getDefault().getPath(path);
		Path dest = databasePath;
		Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
		databasePath = dest;
		source = new JdbcConnectionSource(getDatabaseURL());

		firePropertyChange(DatabaseMessage.RECONNECT);
	}

	public Path getDatabasePath() {
		return databasePath;
	}

	private String getDatabaseURL() {
		return "jdbc:sqlite:" + databasePath;
	}

	public boolean isAlertFileSize() {
		File dbFile = new File(databasePath.toString());
		return dbFile.length() / 1048576 > ALERT_DB_FILE_SIZE_MB;
	}
}
