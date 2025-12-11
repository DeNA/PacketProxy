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
package packetproxy.gui;

import static packetproxy.util.Logging.errWithStackTrace;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import packetproxy.EncoderManager;
import packetproxy.encode.Encoder;
import packetproxy.model.Extension;
import packetproxy.model.Extensions;

public class GUIExtensions {

	private static GUIExtensions instance;
	private static JFrame owner;
	private JPanel main_panel;
	private JTabbedPane tabs;
	private int previousTabIndex;
	// relations between extension name and menu index
	private Map<String, JMenuItem> extensionMenus;

	public static JFrame getOwner() {
		return owner;
	}

	public static GUIExtensions getInstance() throws Exception {
		if (instance == null) {

			instance = new GUIExtensions();
		}
		return instance;
	}

	private GUIExtensions() throws Exception {
		extensionMenus = new HashMap<>();

		main_panel = new JPanel();
		tabs = new JTabbedPane();
		tabs.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				int currentTabIndex = tabs.getSelectedIndex();
				previousTabIndex = currentTabIndex;
			}
		});
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		main_panel.add(tabs);
	}

	private void loadJars() throws Exception {
		File directory = new File(System.getProperty("user.home") + "/.packetproxy/extensions");
		if (!directory.exists()) {

			directory.mkdirs();
		}
		File[] jarFiles = directory.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}
		});
		URL[] urls = new URL[jarFiles.length];
		for (int i = 0; i < jarFiles.length; i++) {

			urls[i] = jarFiles[i].toURI().toURL();
		}
		URLClassLoader urlClassLoader = new URLClassLoader(urls);
		for (File jarFile : jarFiles) {

			JarFile jar = new JarFile(jarFile);
			for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {

				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.endsWith(".class"))
					continue;

				String className = name.replace("/", ".").substring(0, name.length() - 6);
				try {

					Class clazz = urlClassLoader.loadClass(className);
					if (!Extension.class.isAssignableFrom(clazz))
						continue;
					Constructor constructor = clazz.getConstructor();
					Extension ext = (Extension) constructor.newInstance();
					if (ext.getName() == null) {

						ext.setName(className);
					}
					if (ext.getPath() == null) {

						ext.setPath(jarFile.toPath().toString());
					}
					Extensions.getInstance().create(ext);
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
			jar.close();
		}
		urlClassLoader.close();

		for (Extension extension : Extensions.getInstance().queryAll()) {

			if (extension.isEnabled()) {

				addExtension(extension);
			}
		}
	}

	public void addExtension(Extension ext) throws Exception {
		// encoder
		Map<String, Class<?>> encoders = ext.getEncoders();
		if (encoders != null) {

			for (String name : encoders.keySet()) {

				Class encodeClass = encoders.get(name);
				if (!Encoder.class.isAssignableFrom(encodeClass)) {

					continue;
				}
				EncoderManager.getInstance().addEncoder(name, encodeClass);
			}
		}

		// extension page
		JComponent component = ext.createPanel();
		if (component != null) {

			tabs.addTab(ext.getName(), ext.createPanel());
		}

		// history right click
		JMenuItem extensionItem = ext.historyClickHandler();
		if (extensionItem != null) {

			GUIHistory.getInstance().addMenu(extensionItem);
			extensionMenus.put(ext.getName(), extensionItem);
		}
	}

	public void removeExtension(Extension ext) throws Exception {
		// encoder
		Map<String, Class<?>> encoders = ext.getEncoders();
		if (encoders != null) {

			for (String name : encoders.keySet()) {

				Class<?> encodeClass = encoders.get(name);
				if (!Encoder.class.isAssignableFrom(encodeClass)) {

					continue;
				}
				EncoderManager.getInstance().removeEncoder(name);
			}
		}

		JMenuItem extensionItem = extensionMenus.get(ext.getName());
		if (extensionItem != null) {

			GUIHistory.getInstance().removeMenu(extensionItem);
			extensionMenus.remove(ext.getName());
		}

		int index = tabs.indexOfTab(ext.getName());
		if (index >= 0) {

			tabs.removeTabAt(index);
		}
	}

	public JComponent createPanel() throws Exception {
		loadJars();
		return main_panel;
	}
}
