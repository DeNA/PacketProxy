package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UpdateConfigTool extends AuthenticatedMCPTool {

	private final Gson gson = new Gson();
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	@Override
	public String getName() {
		return "update_config";
	}

	@Override
	public String getDescription() {
		return "Update PacketProxy configuration settings with complete configuration object. IMPORTANT: Requires a complete configuration object, not partial updates.";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject configJsonProp = new JsonObject();
		configJsonProp.addProperty("type", "object");
		configJsonProp.addProperty("description",
				"PacketProxyHub-compatible configuration JSON containing COMPLETE configuration object. Must include all required arrays: listenPorts, servers, modifications, sslPassThroughs (can be empty arrays). Partial configurations will cause null pointer errors. Recommended workflow: 1) Call get_config() first, 2) Modify specific fields in the returned object, 3) Pass the entire modified object here.");
		schema.add("config_json", configJsonProp);

		JsonObject backupProp = new JsonObject();
		backupProp.addProperty("type", "boolean");
		backupProp.addProperty("description", "Create backup of existing configuration (default: true)");
		backupProp.addProperty("default", true);
		schema.add("backup", backupProp);

		JsonObject suppressDialogProp = new JsonObject();
		suppressDialogProp.addProperty("type", "boolean");
		suppressDialogProp.addProperty("description",
				"Suppress confirmation dialog for configuration update (default: false)");
		suppressDialogProp.addProperty("default", false);
		schema.add("suppress_dialog", suppressDialogProp);

		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("UpdateConfigTool called with arguments: " + arguments.toString());

		if (!arguments.has("config_json")) {
			throw new Exception("config_json parameter is required");
		}

		JsonObject configJson = arguments.getAsJsonObject("config_json");
		boolean backup = arguments.has("backup") ? arguments.get("backup").getAsBoolean() : true;
		boolean suppressDialog = arguments.has("suppress_dialog")
				? arguments.get("suppress_dialog").getAsBoolean()
				: false;

		try {
			log("UpdateConfigTool step 1: Starting configuration update");

			JsonObject backupInfo = null;

			if (backup) {
				log("UpdateConfigTool step 2: Creating backup");
				backupInfo = createConfigBackup();
				log("UpdateConfigTool step 3: Backup created successfully");
			}

			log("UpdateConfigTool step 4: Updating configuration");
			updateConfiguration(configJson, suppressDialog);
			log("UpdateConfigTool step 5: Configuration updated successfully");

			log("UpdateConfigTool step 6: Building response data");
			JsonObject data = new JsonObject();
			data.addProperty("success", true);
			data.addProperty("backup_created", backup);
			if (backupInfo != null) {
				data.add("backup_info", backupInfo);
			}
			data.addProperty("config_updated", true);

			String jsonText = data.toString();
			log("UpdateConfigTool step 7: Response data JSON: " + jsonText);

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", jsonText);

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject result = new JsonObject();
			result.add("content", contentArray);

			String resultJson = result.toString();
			log("UpdateConfigTool step 8: Final result JSON length: " + resultJson.length());
			log("UpdateConfigTool step 9: Configuration update completed successfully");
			return result;

		} catch (Exception e) {
			log("UpdateConfigTool error: " + e.getMessage());
			e.printStackTrace();
			throw new Exception("Failed to update configuration: " + e.getMessage());
		}
	}

	private JsonObject createConfigBackup() throws Exception {
		Date now = new Date();
		String timestamp = dateFormat.format(now);
		String backupId = "backup_" + timestamp.replace(":", "").replace("-", "").replace("T", "_").replace("Z", "");

		// Create backup directory if it doesn't exist
		File backupDir = new File("backup");
		if (!backupDir.exists()) {
			backupDir.mkdirs();
		}

		String backupPath = backupDir.getPath() + File.separator + backupId + ".json";

		try {
			// HTTP APIで設定を直接取得（認証チェックを回避）
			String configText = getConfigFromHttpApiForBackup();
			JsonObject backupConfig = gson.fromJson(configText, JsonObject.class);

			// Write backup to file
			try (FileWriter writer = new FileWriter(backupPath)) {
				gson.toJson(backupConfig, writer);
				writer.flush();
			}

			log("Configuration backed up to: " + backupPath);
			log("Backup content size: " + configText.length() + " characters");

		} catch (IOException e) {
			log("Failed to write backup file: " + e.getMessage());
			throw new Exception("Failed to create backup file: " + e.getMessage());
		} catch (Exception e) {
			log("Failed to create backup: " + e.getMessage());
			throw new Exception("Failed to create configuration backup: " + e.getMessage());
		}

		JsonObject backupInfo = new JsonObject();
		backupInfo.addProperty("backup_id", backupId);
		backupInfo.addProperty("backup_path", backupPath);
		backupInfo.addProperty("timestamp", timestamp);

		log("Created configuration backup: " + backupId);
		log("Backup info JSON: " + backupInfo.toString());
		return backupInfo;
	}

	private void updateConfiguration(JsonObject configJson, boolean suppressDialog) throws Exception {
		log("UpdateConfigTool starting configuration update using HTTP API");

		// HTTP POST APIで設定を更新（削除処理も自動実行）
		updateConfigViaHttpApi(configJson.toString(), suppressDialog);

		log("UpdateConfigTool configuration update completed using HTTP API");
	}

	private void updateConfigViaHttpApi(String configJsonString, boolean suppressDialog) throws Exception {
		// 設定済みAccessTokenを取得（HTTPリクエスト用）
		String accessToken = getConfiguredAccessToken();

		// HTTP POSTリクエスト
		URL url = new URL("http://localhost:32349/config");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Authorization", accessToken);
		conn.setRequestProperty("Content-Type", "application/json");
		if (suppressDialog) {
			conn.setRequestProperty("X-Suppress-Dialog", "true");
		}
		conn.setDoOutput(true);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(60000);

		// リクエストボディを送信
		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = configJsonString.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		int responseCode = conn.getResponseCode();
		if (responseCode != 200) {
			// エラーレスポンスがある場合は読み取り
			String errorMessage = "HTTP API returned status: " + responseCode;
			if (conn.getErrorStream() != null) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
					StringBuilder error = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) {
						error.append(line);
					}
					if (error.length() > 0) {
						errorMessage += ". Error: " + error.toString();
					}
				}
			}
			throw new Exception(
					errorMessage + ". Check if config sharing is enabled and user confirmed the operation.");
		}

		// 成功レスポンスを読み取り（必要に応じて）
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			log("HTTP API response: " + response.toString());
		}

		conn.disconnect();
	}

	private String getConfigFromHttpApiForBackup() throws Exception {
		// 設定済みAccessTokenを取得（HTTPリクエスト用）
		String accessToken = getConfiguredAccessToken();

		// HTTP GETリクエスト
		URL url = new URL("http://localhost:32349/config");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Authorization", accessToken);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(60000);

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

}
