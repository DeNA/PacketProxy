package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import packetproxy.model.ListenPort;
import packetproxy.model.ListenPorts;
import packetproxy.model.Modification;
import packetproxy.model.Modifications;
import packetproxy.model.SSLPassThrough;
import packetproxy.model.SSLPassThroughs;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class ConfigTool implements MCPTool {

	private final Gson gson = new Gson();

	@Override
	public String getName() {
		return "get_configs";
	}

	@Override
	public String getDescription() {
		return "Get PacketProxy configuration settings";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject categoriesProp = new JsonObject();
		categoriesProp.addProperty("type", "array");
		categoriesProp.addProperty("description", "Categories to retrieve (empty for all)");

		JsonObject itemsProp = new JsonObject();
		itemsProp.addProperty("type", "string");
		JsonArray enumValues = new JsonArray();
		enumValues.add("listenPorts");
		enumValues.add("servers");
		enumValues.add("modifications");
		enumValues.add("sslPassThroughs");
		itemsProp.add("enum", enumValues);
		categoriesProp.add("items", itemsProp);

		schema.add("categories", categoriesProp);

		return schema;
	}

	@Override
	public JsonObject call(JsonObject arguments) throws Exception {
		log("ConfigTool called with arguments: " + arguments.toString());

		JsonArray categories = null;
		if (arguments.has("categories")) {
			categories = arguments.getAsJsonArray("categories");
		}

		JsonObject result = new JsonObject();

		try {
			if (shouldIncludeCategory(categories, "listenPorts")) {
				result.add("listenPorts", getListenPorts());
			}

			if (shouldIncludeCategory(categories, "servers")) {
				result.add("servers", getServers());
			}

			if (shouldIncludeCategory(categories, "modifications")) {
				result.add("modifications", getModifications());
			}

			if (shouldIncludeCategory(categories, "sslPassThroughs")) {
				result.add("sslPassThroughs", getSSLPassThroughs());
			}

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", gson.toJson(result));

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject mcpResult = new JsonObject();
			mcpResult.add("content", contentArray);

			log("ConfigTool returning configuration");
			return mcpResult;

		} catch (Exception e) {
			log("ConfigTool error: " + e.getMessage());
			throw new Exception("Failed to get configuration: " + e.getMessage());
		}
	}

	private boolean shouldIncludeCategory(JsonArray categories, String category) {
		if (categories == null || categories.size() == 0) {
			return true; // Include all if not specified
		}

		for (int i = 0; i < categories.size(); i++) {
			if (categories.get(i).getAsString().equals(category)) {
				return true;
			}
		}
		return false;
	}

	private JsonArray getListenPorts() throws Exception {
		JsonArray portsArray = new JsonArray();
		List<ListenPort> ports = ListenPorts.getInstance().queryAll();

		for (ListenPort port : ports) {
			JsonObject portJson = new JsonObject();
			portJson.addProperty("id", port.getId());
			portJson.addProperty("port", port.getPort());
			portJson.addProperty("protocol", port.getProtocol().toString());
			portJson.addProperty("type", port.getType().toString());
			portJson.addProperty("serverId", port.getServerId());
			portJson.addProperty("enabled", port.isEnabled());
			portsArray.add(portJson);
		}

		return portsArray;
	}

	private JsonArray getServers() throws Exception {
		JsonArray serversArray = new JsonArray();
		List<Server> servers = Servers.getInstance().queryAll();

		for (Server server : servers) {
			JsonObject serverJson = new JsonObject();
			serverJson.addProperty("id", server.getId());
			serverJson.addProperty("ip", server.getIp());
			serverJson.addProperty("port", server.getPort());
			serverJson.addProperty("encoder", server.getEncoder());
			serverJson.addProperty("use_ssl", server.getUseSSL());
			serverJson.addProperty("comment", server.getComment());
			serversArray.add(serverJson);
		}

		return serversArray;
	}

	private JsonArray getModifications() throws Exception {
		JsonArray modificationsArray = new JsonArray();
		List<Modification> modifications = Modifications.getInstance().queryAll();

		for (Modification mod : modifications) {
			JsonObject modJson = new JsonObject();
			modJson.addProperty("id", mod.getId());
			modJson.addProperty("direction", mod.getDirection().toString());
			modJson.addProperty("method", mod.getMethod().toString());
			modJson.addProperty("pattern", mod.getPattern());
			modJson.addProperty("replaced", mod.getReplaced());
			modJson.addProperty("serverId", mod.getServerId());
			modificationsArray.add(modJson);
		}

		return modificationsArray;
	}

	private JsonArray getSSLPassThroughs() throws Exception {
		JsonArray passThroughsArray = new JsonArray();
		List<SSLPassThrough> passThroughs = SSLPassThroughs.getInstance().queryAll();

		for (SSLPassThrough passThrough : passThroughs) {
			JsonObject passThroughJson = new JsonObject();
			passThroughJson.addProperty("id", passThrough.getId());
			passThroughJson.addProperty("server_name", passThrough.getServerName());
			passThroughJson.addProperty("listen_port", passThrough.getListenPort());
			passThroughsArray.add(passThroughJson);
		}

		return passThroughsArray;
	}
}
