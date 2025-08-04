package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConfigTool extends AuthenticatedMCPTool {

	private final Gson gson = new Gson();

	@Override
	public String getName() {
		return "get_config";
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
		enumValues.add("resolutions");
		enumValues.add("interceptOptions");
		enumValues.add("clientCertificates");
		enumValues.add("generalConfigs");
		enumValues.add("extensions");
		enumValues.add("filters");
		enumValues.add("openVPNForwardPorts");
		enumValues.add("charSets");
		itemsProp.add("enum", enumValues);
		categoriesProp.add("items", itemsProp);

		schema.add("categories", categoriesProp);

		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("ConfigTool called with arguments: " + arguments.toString());

		try {
			// HTTP APIで設定を取得
			String configJson = getConfigFromHttpApi();

			// categoriesでフィルタリングが指定されている場合はフィルタリングを適用
			JsonObject allConfig = gson.fromJson(configJson, JsonObject.class);
			JsonObject filteredConfig = filterByCategories(allConfig, arguments);

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", gson.toJson(filteredConfig));

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject mcpResult = new JsonObject();
			mcpResult.add("content", contentArray);

			log("ConfigTool returning configuration from HTTP API");
			return mcpResult;

		} catch (Exception e) {
			log("ConfigTool error: " + e.getMessage());
			throw new Exception("Failed to get configuration: " + e.getMessage());
		}
	}

	private String getConfigFromHttpApi() throws Exception {
		// 設定済みAccessTokenを取得（HTTPリクエスト用）
		String accessToken = getConfiguredAccessToken();

		// HTTP GETリクエスト
		URL url = new URL("http://localhost:32349/config");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Authorization", accessToken);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(10000);

		int responseCode = conn.getResponseCode();
		if (responseCode != 200) {
			throw new Exception("HTTP API returned status: " + responseCode + ". Check if config sharing is enabled.");
		}

		// レスポンスを読み取り
		BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();
		conn.disconnect();

		return response.toString();
	}

	private JsonObject filterByCategories(JsonObject config, JsonObject arguments) {
		if (!arguments.has("categories")) {
			return config; // カテゴリ指定がない場合は全て返す
		}

		JsonArray categories = arguments.getAsJsonArray("categories");
		if (categories.size() == 0) {
			return config; // 空の場合は全て返す
		}

		JsonObject filtered = new JsonObject();
		for (int i = 0; i < categories.size(); i++) {
			String category = categories.get(i).getAsString();
			if (config.has(category)) {
				filtered.add(category, config.get(category));
			}
		}

		return filtered;
	}

}
