package packetproxy.common;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import packetproxy.model.*;

/** リモート設定 JSON を DB に反映する。 */
public class ConfigSettingsWriter {

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

	public void applyFromJson(String json) throws Exception {
		var daoHub = new Gson().fromJson(json, DaoHub.class);

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
