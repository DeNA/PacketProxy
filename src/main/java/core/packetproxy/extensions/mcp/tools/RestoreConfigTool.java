package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class RestoreConfigTool extends AuthenticatedMCPTool {

	private final Gson gson = new Gson();

	@Override
	public String getName() {
		return "restore_config";
	}

	@Override
	public String getDescription() {
		return "Restore PacketProxy configuration from backup file with optional dialog suppression";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject backupIdProp = new JsonObject();
		backupIdProp.addProperty("type", "string");
		backupIdProp.addProperty("description", "Backup ID to restore from (e.g., backup_20250103_120000)");
		schema.add("backup_id", backupIdProp);

		JsonObject suppressDialogProp = new JsonObject();
		suppressDialogProp.addProperty("type", "boolean");
		suppressDialogProp.addProperty("description", "Suppress confirmation dialog for configuration restore (default: false)");
		suppressDialogProp.addProperty("default", false);
		schema.add("suppress_dialog", suppressDialogProp);

		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("RestoreConfigTool called with arguments: " + arguments.toString());

		if (!arguments.has("backup_id")) {
			throw new Exception("backup_id parameter is required");
		}

		String backupId = arguments.get("backup_id").getAsString();
		boolean suppressDialog = arguments.has("suppress_dialog") ? arguments.get("suppress_dialog").getAsBoolean() : false;

		try {
			log("RestoreConfigTool step 1: Loading backup configuration");
			JsonObject backupConfig = loadBackupConfig(backupId);
			log("RestoreConfigTool step 2: Backup configuration loaded successfully");

			log("RestoreConfigTool step 3: Restoring configuration using UpdateConfigTool");
			JsonObject updateArgs = new JsonObject();
			updateArgs.add("config_json", backupConfig);
			updateArgs.addProperty("backup", true);
			updateArgs.addProperty("suppress_dialog", suppressDialog);
			updateArgs.addProperty("access_token", arguments.get("access_token").getAsString());

			UpdateConfigTool updateTool = new UpdateConfigTool();
			JsonObject updateResult = updateTool.call(updateArgs);
			log("RestoreConfigTool step 4: Configuration restored successfully");

			log("RestoreConfigTool step 5: Building response data");
			JsonObject data = new JsonObject();
			data.addProperty("success", true);
			data.addProperty("backup_id_restored", backupId);
			data.addProperty("config_restored", true);

			String jsonText = data.toString();
			log("RestoreConfigTool step 6: Response data JSON: " + jsonText);

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", jsonText);

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject result = new JsonObject();
			result.add("content", contentArray);

			String resultJson = result.toString();
			log("RestoreConfigTool step 7: Final result JSON length: " + resultJson.length());
			log("RestoreConfigTool step 8: Configuration restore completed successfully");
			return result;

		} catch (Exception e) {
			log("RestoreConfigTool error: " + e.getMessage());
			e.printStackTrace();
			throw new Exception("Failed to restore configuration: " + e.getMessage());
		}
	}

	private JsonObject loadBackupConfig(String backupId) throws Exception {
		// Construct backup file path
		File backupDir = new File("backup");
		if (!backupDir.exists()) {
			throw new Exception("Backup directory does not exist");
		}

		String backupFileName = backupId + ".json";
		File backupFile = new File(backupDir, backupFileName);

		if (!backupFile.exists()) {
			throw new Exception("Backup file not found: " + backupFileName);
		}

		log("Loading backup from: " + backupFile.getAbsolutePath());

		try (FileReader reader = new FileReader(backupFile)) {
			JsonObject backupConfig = gson.fromJson(reader, JsonObject.class);

			if (backupConfig == null) {
				throw new Exception("Invalid backup file format");
			}

			log("Backup configuration loaded successfully from: " + backupFileName);
			return backupConfig;

		} catch (IOException e) {
			log("Failed to read backup file: " + e.getMessage());
			throw new Exception("Failed to read backup file: " + e.getMessage());
		} catch (Exception e) {
			log("Failed to parse backup file: " + e.getMessage());
			throw new Exception("Failed to parse backup file: " + e.getMessage());
		}
	}
}
