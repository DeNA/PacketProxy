package packetproxy.extensions.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {

	private final Map<String, MCPTool> tools;

	public ToolRegistry() {
		this.tools = new HashMap<>();
		registerDefaultTools();
	}

	private void registerDefaultTools() {
		// 基本的なツールを登録
		registerTool(new HistoryTool());
		registerTool(new PacketDetailTool());
		registerTool(new ConfigTool());
		registerTool(new UpdateConfigTool());
		registerTool(new RestoreConfigTool());
		registerTool(new LogTool());
	}

	public void registerTool(MCPTool tool) {
		tools.put(tool.getName(), tool);
	}

	public JsonArray getToolsList() {
		JsonArray toolsArray = new JsonArray();

		for (MCPTool tool : tools.values()) {
			JsonObject toolInfo = new JsonObject();
			toolInfo.addProperty("name", tool.getName());
			toolInfo.addProperty("description", tool.getDescription());

			JsonObject inputSchema = new JsonObject();
			inputSchema.addProperty("type", "object");
			inputSchema.add("properties", tool.getInputSchema());

			toolInfo.add("inputSchema", inputSchema);
			toolsArray.add(toolInfo);
		}

		return toolsArray;
	}

	public JsonObject callTool(String toolName, JsonObject arguments) throws Exception {
		MCPTool tool = tools.get(toolName);
		if (tool == null) {
			throw new Exception("Unknown tool: " + toolName);
		}

		return tool.call(arguments);
	}
}
