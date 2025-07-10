package packetproxy.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import packetproxy.model.*;

public class ConfigIO {

	private static class DaoHub {

		@SerializedName(value = "listenPorts")
		List<ListenPort> listenPortList;
		@SerializedName(value = "servers")
		List<Server> serverList;
		@SerializedName(value = "modifications")
		List<Modification> modificationList;
		@SerializedName(value = "sslPassThroughs")
		List<SSLPassThrough> sslPassThroughList;
	}

	public ConfigIO() {

	}

	private void fixUpServerList(Map<Integer, Integer> serverMap, List<Server> serverList) {
		serverMap.put(-1, -1); /* means all servers */
		serverMap.put(0, 0); /* means no entry */
		int i = 1;
		for (Server server : serverList) {

			serverMap.put(server.getId(), i);
			server.setId(i);
			i++;
		}
	}

	private void fixUpListenPortList(Map<Integer, Integer> serverMap, List<ListenPort> listenPortList) {
		int i = 1;
		for (ListenPort listenPort : listenPortList) {

			int id = listenPort.getServerId();
			if (serverMap.containsKey(id)) {

				listenPort.setServerId(serverMap.get(id));
			} else {

				listenPort.setServerId(-1);
			}
			listenPort.setId(i);
			i++;
		}
	}

	private void fixUpModificationList(Map<Integer, Integer> serverMap, List<Modification> modificationList) {
		int i = 1;
		for (Modification mod : modificationList) {

			mod.setServerId(serverMap.get(mod.getServerId()));
			mod.setId(i);
			i++;
		}
	}

	private void fixUp(DaoHub daoHub) {
		Map<Integer, Integer> serverMap = new HashMap<>();
		fixUpServerList(serverMap, daoHub.serverList);
		fixUpListenPortList(serverMap, daoHub.listenPortList);
		fixUpModificationList(serverMap, daoHub.modificationList);
	}

	public String getOptions() throws Exception {
		DaoHub daoHub = new DaoHub();

		daoHub.listenPortList = ListenPorts.getInstance().queryAll();
		daoHub.serverList = Servers.getInstance().queryAll();
		daoHub.modificationList = Modifications.getInstance().queryAll();
		daoHub.sslPassThroughList = SSLPassThroughs.getInstance().queryAll();

		fixUp(daoHub);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(daoHub);

		return json;
	}

	public void setOptions(String json) throws Exception {
		DaoHub daoHub = new Gson().fromJson(json, DaoHub.class);

		Database.getInstance().dropConfigs();

		for (ListenPort listenPort : daoHub.listenPortList) {

			ListenPorts.getInstance().create(listenPort);
		}
		for (Server server : daoHub.serverList) {

			Servers.getInstance().create(server);
		}
		for (Modification mod : daoHub.modificationList) {

			Modifications.getInstance().create(mod);
		}
		for (SSLPassThrough passThrough : daoHub.sslPassThroughList) {

			SSLPassThroughs.getInstance().create(passThrough);
		}
	}
}
