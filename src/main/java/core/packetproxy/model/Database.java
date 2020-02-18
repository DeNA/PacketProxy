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

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Observable;
import java.util.Observer;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.logger.LocalLog;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;

import packetproxy.util.PacketProxyUtility;

public class Database extends Observable
{
	private static Database instance;

	public static Database getInstance() throws Exception
	{
		if (instance == null) {
			instance = new Database();
		}
		return instance;
	}

	private static int ALERT_DB_FILE_SIZE_MB = 1536;//1.5GB (LIMIT = 2GB)
	private Path databaseDir  = Paths.get(System.getProperty("user.home")+"/.packetproxy/db");//FileSystems.getDefault().getPath("db");
	private Path databasePath = Paths.get(databaseDir.toString() + "/resources.sqlite3");
	private ConnectionSource source;

	private Database() throws Exception
	{
		createDB();
	}

	private void createDB() throws Exception
	{
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		if (! Files.exists(databaseDir)) {
			util.packetProxyLog(databaseDir.toAbsolutePath() + " directory is not found...");
			util.packetProxyLog("creating the directory...");
			Files.createDirectories(databaseDir);
			System.out.println("success!");
		} else {
			if (! Files.isDirectory(databaseDir)) {
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

	public <T,ID> Dao<T, ID> createTable(Class<T> c, Observer observer) throws Exception
	{
		addObserver(observer);
		TableUtils.createTableIfNotExists(source, c);
		Dao<T, ID> dao = DaoManager.createDao(source, c);
		return dao;
	}

	public void dropPacketTableFaster()throws Exception{
		Path src = Paths.get(instance.databasePath.getParent().toAbsolutePath().toString()+"/tmp.sqlite3");
		Path dst = instance.databasePath.toAbsolutePath();
		setChanged();
		notifyObservers(DatabaseMessage.DISCONNECT_NOW);
		DatabaseConnection conn = source.getReadWriteConnection();
		conn.close();
		Files.move(dst, src, StandardCopyOption.REPLACE_EXISTING);
		clearChanged();

		setChanged();
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
		createTable(MockResponse.class, MockResponses.getInstance());
		notifyObservers(DatabaseMessage.RECREATE);

		migrateTableWithoutHistory(src, dst);
		notifyObservers(DatabaseMessage.RECONNECT);
		clearChanged();
		Files.delete(src);
	}

	public <T> void dropTable(Class<T> c) throws Exception
	{
		if(c==Packet.class){
			dropPacketTableFaster();
		}else {
			TableUtils.dropTable(source, c, true);
		}
	}

	private static void migrateTableWithoutHistory(Path srcDBPath, Path dstDBPath){
		try {
			ConnectionSource source = new JdbcConnectionSource("jdbc:sqlite:"+srcDBPath);
			DatabaseConnection conn = source.getReadWriteConnection();
			conn.executeStatement("attach database '" + dstDBPath.toAbsolutePath() + "' as 'dstDB'", DatabaseConnection.DEFAULT_RESULT_FLAGS);
			conn.executeStatement("attach database '" + srcDBPath.toAbsolutePath() + "' as 'srcDB'", DatabaseConnection.DEFAULT_RESULT_FLAGS);
			String querys[] = {
					"DELETE FROM dstDB.interceptOptions",
					"DELETE FROM dstDB.charsets",
					"INSERT OR REPLACE INTO dstDB.filters (id, name, filter) SELECT id, name, filter FROM srcDB.filters",
					"INSERT OR REPLACE INTO dstDB.listenports (id, enabled, ca_name, port, type, server_id) SELECT id, enabled, ca_name, port, type, server_id FROM srcDB.listenports",
					"INSERT OR REPLACE INTO dstDB.configs (key, value) SELECT key, value FROM srcDB.configs",
					"INSERT OR REPLACE INTO dstDB.servers (id, ip, port, encoder, use_ssl, resolved_by_dns, http_proxy, comment) SELECT id, ip, port, encoder, use_ssl, resolved_by_dns, http_proxy, comment FROM srcDB.servers",
					"INSERT OR REPLACE INTO dstDB.clientCertificates (id, enabled, type, serverId, subject, issuer, path, storePassword, keyPassword) SELECT id, enabled, type, serverId, subject, issuer, path, storePassword, keyPassword FROM srcDB.clientCertificates",
					"INSERT OR REPLACE INTO dstDB.interceptOptions (id, enabled, direction, type, relationship, method, pattern, server_id) SELECT id, enabled, direction, type, relationship, method, pattern, server_id FROM srcDB.interceptOptions",
					"INSERT OR REPLACE INTO dstDB.modifications (id, enabled, server_id, direction, pattern, method, replaced) SELECT id, enabled, server_id, direction, pattern, method, replaced FROM srcDB.modifications",
					"INSERT OR REPLACE INTO dstDB.sslpassthroughs (id, enabled, server_name, listen_port) SELECT id, enabled, server_name, listen_port FROM srcDB.sslpassthroughs",
					"INSERT OR REPLACE INTO dstDB.charsets (id, charsetname) SELECT id, charsetname FROM srcDB.charsets",
					"INSERT OR REPLACE INTO dstDB.mock_responses (id, enabled, ip, port, path, mockResponse, comment) SELECT id, enabled, ip, port, path, mockResponse, comment FROM srcDB.mock_responses",
			};
			for (String query : querys){
				try {
					conn.executeStatement(query, DatabaseConnection.DEFAULT_RESULT_FLAGS);
				} catch (Exception e) {
					PacketProxyUtility.getInstance().packetProxyLog("Database format may have been changed. Simply ignore this type of errors.");
					PacketProxyUtility.getInstance().packetProxyLog(String.format("[Error] %s", query));
				}
			}
			conn.close();
		}catch (Exception e){
			e.getStackTrace();
		}
	}

	public void close() throws Exception
	{
		source.close();
	}

	@Override
	public void notifyObservers(Object arg) {
		setChanged();
		super.notifyObservers(arg);
		clearChanged();
	}

	public enum DatabaseMessage {
		PAUSE,
		RESUME,
		DISCONNECT_NOW,
		RECONNECT,
		RECREATE,
	}
	// TODO 保存中にファイルが更新されない様にする
	public void Save(String path) throws Exception
	{
		setChanged();
		notifyObservers(DatabaseMessage.PAUSE);
		clearChanged();

		Path src = databasePath;
		Path dest = FileSystems.getDefault().getPath(path);
		Files.copy(src,  dest, StandardCopyOption.REPLACE_EXISTING);

		setChanged();
		notifyObservers(DatabaseMessage.RESUME);
		clearChanged();
	}
	public void Load(String path) throws Exception
	{
		setChanged();
		notifyObservers(DatabaseMessage.DISCONNECT_NOW);
		clearChanged();
		source.close();

		Path src = FileSystems.getDefault().getPath(path);
		Path dest = FileSystems.getDefault().getPath(databaseDir.toString() +"/resources_temp.sqlite3");
		Files.copy(src,  dest, StandardCopyOption.REPLACE_EXISTING);
		databasePath = dest;
		source = new JdbcConnectionSource(getDatabaseURL());

		setChanged();
		notifyObservers(DatabaseMessage.RECONNECT);
		clearChanged();
	}
	public void saveWithoutLog(String path) throws Exception
	{
		setChanged();
		notifyObservers(DatabaseMessage.PAUSE);
		clearChanged();

		Path src = databasePath;
		Path dest = FileSystems.getDefault().getPath(path);
		Files.copy(src,  dest, StandardCopyOption.REPLACE_EXISTING);
		JdbcConnectionSource new_db = new JdbcConnectionSource("jdbc:sqlite:"+dest);
		DatabaseConnection conn = new_db.getReadWriteConnection();
		conn.executeStatement("delete from packets", DatabaseConnection.DEFAULT_RESULT_FLAGS);
		conn.close();
		new_db.close();

		setChanged();
		notifyObservers(DatabaseMessage.RESUME);
		clearChanged();
	}
	public void LoadAndReplace(String path) throws Exception
	{
		setChanged();
		notifyObservers(DatabaseMessage.DISCONNECT_NOW);
		clearChanged();

		source.close();
		Path src = FileSystems.getDefault().getPath(path);
		Path dest = databasePath;
		Files.move(src,  dest, StandardCopyOption.REPLACE_EXISTING);
		databasePath = dest;
		source = new JdbcConnectionSource(getDatabaseURL());

		setChanged();
		notifyObservers(DatabaseMessage.RECONNECT);
		clearChanged();
	}
	public Path getDatabasePath()
	{
		return databasePath;
	}
	private String getDatabaseURL() {
		return "jdbc:sqlite:" + databasePath;
	}

	public boolean isAlertFileSize(){
		File dbFile = new File(databasePath.toString());
		return dbFile.length()/1048576>ALERT_DB_FILE_SIZE_MB;
	}
}
