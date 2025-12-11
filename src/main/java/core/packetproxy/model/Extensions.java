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
import static packetproxy.model.PropertyChangeEventType.EXTENSIONS;
import static packetproxy.util.Logging.errWithStackTrace;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.JOptionPane;
import packetproxy.extensions.randomness.RandomnessExtension;
import packetproxy.extensions.samplehttp.SampleEncoders;
import packetproxy.model.Database.DatabaseMessage;

public class Extensions implements PropertyChangeListener {

	private static Extensions instance;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public static Extensions getInstance() throws Exception {
		if (instance == null) {

			instance = new Extensions();
		}
		return instance;
	}

	private static Map<String, Class<?>> presetExtensions = new HashMap<>() {

		{
			put((new RandomnessExtension()).getName(), RandomnessExtension.class);
			put((new SampleEncoders()).getName(), SampleEncoders.class);
		}
	};

	// Extensionではなく、継承先のインスタンスを保持する必要がある
	// enabledになっている際にのみext_instancesに保持されるようにする
	private Map<String, Extension> ext_instances;
	private Database database;
	private Dao<Extension, String> dao;
	private DaoQueryCache<Extension> cache;

	private Extensions() throws Exception {
		ext_instances = new HashMap<>();
		database = Database.getInstance();
		dao = database.createTable(Extension.class, this);
		cache = new DaoQueryCache<>();
		if (!isLatestVersion()) {

			RecreateTable();
		}

		// load presets
		for (Class clazz : presetExtensions.values()) {

			Constructor<Extension> constructor = clazz.getConstructor();
			Extension extension = (Extension) constructor.newInstance();
			create(extension);
		}
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	// return loaded extension or null
	public Extension loadExtension(String name, String path) {
		if (presetExtensions.containsKey(name)) {

			Extension extension = null;
			try {

				Class clazz = presetExtensions.get(name);
				Constructor<Extension> constructor = clazz.getConstructor();
				extension = (Extension) constructor.newInstance();
			} catch (Exception e) {

				errWithStackTrace(e);
			}
			return extension;
		}
		try {

			File file = new File(path);
			URL[] urls = {file.toURI().toURL()};
			URLClassLoader urlClassLoader = new URLClassLoader(urls);
			JarFile jar = new JarFile(file);
			Extension extension = null;
			for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {

				JarEntry entry = entries.nextElement();
				String entryName = entry.getName();
				if (!entryName.endsWith(".class"))
					continue;
				String className = entryName.replace("/", ".").substring(0, entryName.length() - 6);
				try {

					Class clazz = urlClassLoader.loadClass(className);
					if (!Extension.class.isAssignableFrom(clazz))
						continue;
					Constructor<Extension> constructor = clazz.getConstructor(String.class, String.class);
					extension = (Extension) constructor.newInstance(name, path);
				} catch (ClassNotFoundException e1) {

					// errWithStackTrace(e1);
				}
			}
			jar.close();
			urlClassLoader.close();
			return extension;
		} catch (Exception e) {

			errWithStackTrace(e);
			return null;
		}
	}

	public void create(Extension ext) throws Exception {
		// 存在しないならListに追加
		if (!dao.idExists(ext.getName())) {

			dao.create(ext);
			if (ext.isEnabled()) {

				ext_instances.put(ext.getName(), ext);
			}
		}
		cache.clear();
		firePropertyChange();
	}

	public void delete(String id) throws Exception {
		dao.deleteById(id);
		ext_instances.remove(id);
		cache.clear();
		firePropertyChange();
	}

	public void delete(Extension ext) throws Exception {
		dao.delete(ext);
		ext_instances.remove(ext.getName());
		cache.clear();
		firePropertyChange();
	}

	public Extension update(Extension ext) throws Exception {
		dao.update(ext);
		if (ext.isEnabled() && !ext_instances.containsKey(ext.getName())) {

			Extension loadedExt = loadExtension(ext.getName(), ext.getPath());
			if (loadedExt != null) {

				loadedExt.setEnabled(true);
				ext_instances.put(loadedExt.getName(), loadedExt);
			}
			ext = loadedExt;
		} else if (!ext.isEnabled()) {

			// remove because of disabled
			ext_instances.remove(ext.getName());
		}
		cache.clear();
		firePropertyChange();
		return ext;
	}

	public void refresh() {
		firePropertyChange();
	}

	public Extension query(String id) throws Exception {
		List<Extension> ret = cache.query("query", id);
		if (ret != null)
			return ret.get(0);
		Extension ext = null;
		if (ext_instances.containsKey(id)) {

			ext = ext_instances.get(id);
		} else {

			ext = dao.queryForId(id);
			if (ext.isEnabled()) {

				// load jar
				ext = loadExtension(ext.getName(), ext.getPath());
				if (ext != null) {

					ext.setEnabled(true);
					ext_instances.put(ext.getName(), ext);
				}
			}
		}
		cache.set("query", id, ext);
		return ext;
	}

	public List<Extension> queryAll() throws Exception {
		List<Extension> ret = cache.query("queryAll", 0);
		if (ret != null) {

			return ret;
		}
		ret = dao.queryBuilder().query();
		Map<String, Extension> newHash = new HashMap<>();
		for (int i = 0; i < ret.size(); i++) {

			Extension ext = ret.get(i);
			if (!ext.isEnabled())
				continue;
			if (ext_instances.containsKey(ext.getName())) {

				Extension loadedExt = ext_instances.get(ext.getName());
				ret.set(i, loadedExt);
				newHash.put(ext.getName(), loadedExt);
				continue;
			}
			Extension loadedExt = loadExtension(ext.getName(), ext.getPath());
			if (loadedExt != null) {

				loadedExt.setEnabled(ext.isEnabled());
				ret.set(i, loadedExt);
				newHash.put(loadedExt.getName(), loadedExt);
			}
		}
		ext_instances = newHash;
		cache.set("queryAll", 0, ret);
		return ret;
	}

	public void firePropertyChange() {
		firePropertyChange(null);
	}

	public void firePropertyChange(Object arg) {
		pcs.firePropertyChange(EXTENSIONS.toString(), null, arg);
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
					// TODO ロックを取る
					break;
				case RESUME :
					// TODO ロックを取る
					break;
				case DISCONNECT_NOW :
					break;
				case RECONNECT :
					database = Database.getInstance();
					dao = database.createTable(Extension.class, this);
					cache.clear();
					firePropertyChange(message);
					break;
				case RECREATE :
					database = Database.getInstance();
					dao = database.createTable(Extension.class, this);
					cache.clear();
					break;
				default :
					break;
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='extensions'").getFirstResult()[0];
		return result.equals(
				"CREATE TABLE `extensions` (`name` VARCHAR , `enabled` BOOLEAN , `path` VARCHAR , PRIMARY KEY (`name`) )");
	}

	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null, "Extensionsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {

			database.dropTable(Extension.class);
			dao = database.createTable(Extension.class, this);
		}
	}
}
