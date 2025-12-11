package packetproxy.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import fi.iki.elonen.NanoHTTPD;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import packetproxy.gui.GUIMain;
import packetproxy.model.*;

public class ConfigHttpServer extends NanoHTTPD {

	private final String allowedAccessToken;

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

	public ConfigHttpServer(String hostname, int port, String allowedAccessToken) {
		super(hostname, port);
		this.allowedAccessToken = allowedAccessToken;
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

	@Override
	public Response serve(IHTTPSession session) {
		Method method = session.getMethod();
		String uri = session.getUri();
		String token = session.getHeaders().get("authorization");

		if (method.equals(Method.OPTIONS) && uri.equals("/config")) {

			Response res = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_HTML, null);
			res.addHeader("Access-Control-Allow-Origin", "*");
			res.addHeader("Access-Control-Allow-Headers", "Authorization,Content-Type");
			res.addHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
			res.addHeader("Access-Control-Allow-Private-Network", "true");
			res.addHeader("Access-Control-Max-Age", "86400");
			return res;
		}

		if (!allowedAccessToken.equals(token)) {

			return NanoHTTPD.newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_HTML, null);
		}

		if (method.equals(Method.GET) && uri.equals("/config")) {

			try {

				DaoHub daoHub = new DaoHub();

				daoHub.listenPortList = ListenPorts.getInstance().queryAll();
				daoHub.serverList = Servers.getInstance().queryAll();
				daoHub.modificationList = Modifications.getInstance().queryAll();
				daoHub.sslPassThroughList = SSLPassThroughs.getInstance().queryAll();

				fixUp(daoHub);

				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String json = gson.toJson(daoHub);

				Response res = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_HTML, json);
				res.addHeader("Access-Control-Allow-Origin", "*");
				return res;

			} catch (Exception e) {

				return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, null);
			}
		}

		if (method.equals(Method.POST) && uri.equals("/config")) {

			try {

				GUIMain.getInstance().setAlwaysOnTop(true);
				GUIMain.getInstance().setVisible(true);

				GUIMain.getInstance().getTabbedPane().setSelectedIndex(GUIMain.Panes.OPTIONS.ordinal());

				int option = JOptionPane.showConfirmDialog(GUIMain.getInstance(),
						I18nString.get("Do you want to overwrite config?"), I18nString.get("Loading config"),
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

				GUIMain.getInstance().setAlwaysOnTop(false);

				if (option == JOptionPane.NO_OPTION) {

					return NanoHTTPD.newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_HTML, null);
				}

				HashMap<String, String> map = new HashMap<String, String>();
				session.parseBody(map);
				String json = map.get("postData");

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

				Response res = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json",
						"{\"status\": \"ok\"}");
				res.addHeader("Access-Control-Allow-Origin", "*");
				return res;

			} catch (Exception e) {

				return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, null);
			}
		}

		return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, null);
	}
}
