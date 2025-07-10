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
package packetproxy;

import static packetproxy.model.PropertyChangeEventType.LISTEN_PORTS;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.BindException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import packetproxy.model.ListenPort;
import packetproxy.model.ListenPorts;
import packetproxy.util.PacketProxyUtility;

public class ListenPortManager implements PropertyChangeListener {

	private ListenPorts listenPorts;
	private Map<String, Listen> listen_map;

	private static ListenPortManager instance;

	public static ListenPortManager getInstance() throws Exception {
		if (instance == null) {

			instance = new ListenPortManager();
		}
		return instance;
	}

	private ListenPortManager() throws Exception {
		listen_map = new HashMap<String, Listen>();
		listenPorts = ListenPorts.getInstance();
		listenPorts.addPropertyChangeListener(this);
		listenPorts.refresh();
	}

	public void rebootIfHTTPProxyRunning() throws Exception {
		List<ListenPort> list = listenPorts.queryEnabledHttpProxis();
		for (ListenPort lp : list) {

			Listen listen = listen_map.get(lp.getProtoPort());
			if (listen == null)
				continue;
			listen.close();
			Listen new_listen = new Listen(lp);
			listen_map.put(lp.getProtoPort(), new_listen);
		}
	}

	private void stopIfRunning() throws Exception {
		List<ListenPort> list = listenPorts.queryEnabled();
		for (Iterator<String> port = listen_map.keySet().iterator(); port.hasNext();) {

			boolean found = false;
			String p = port.next();
			for (ListenPort l : list) {

				if (l.getProtoPort().equals(p)) {

					found = true;
					break;
				}
			}
			if (found == false) {

				// System.out.println("## close:"+p);
				listen_map.get(p).close();
				port.remove();
			}
		}
	}

	private void startListen(ListenPort listen_port) throws Exception {
		try {

			Listen listen = listen_map.get(listen_port.getProtoPort());
			if (listen != null) {

				if (!listen.getListenInfo().equals(listen_port)) {

					listen.close();
					Listen new_listen = new Listen(listen_port);
					listen_map.put(listen_port.getProtoPort(), new_listen);
					PacketProxyUtility.getInstance().packetProxyLog("## restart:" + listen_port.getProtoPort());
				}
			} else {

				PacketProxyUtility.getInstance().packetProxyLog("## start:" + listen_port.getProtoPort());
				Listen new_listen = new Listen(listen_port);
				listen_map.put(listen_port.getProtoPort(), new_listen);
			}
		} catch (BindException e) {

			PacketProxyUtility.getInstance()
					.packetProxyLogErr("cannot listen port. (permission issue or already listened)");
			listen_port.setDisabled();
			listenPorts.update(listen_port);
		}
	}

	private void startIfStateChanged() throws Exception {
		List<ListenPort> list = listenPorts.queryEnabled();
		for (ListenPort listen_port : list) {

			startListen(listen_port);
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!LISTEN_PORTS.matches(evt)) {

			return;
		}

		try {

			synchronized (listen_map) {

				stopIfRunning();
				startIfStateChanged();
			}
		} catch (Exception e) {

			e.printStackTrace();
		}
	}
}
