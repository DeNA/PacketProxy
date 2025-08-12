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
		registerTool(new LogTool());
		registerTool(new ConfigTool());
		registerTool(new UpdateConfigTool());
		registerTool(new RestoreConfigTool());
		registerTool(new ResendPacketTool());
		registerTool(new BulkSendTool());
		registerTool(new VulCheckHelperTool());
		registerTool(new JobStatusTool());
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

		JsonObject toolResult = tool.call(arguments);

		// MCP仕様に準拠した応答形式に変換
		JsonObject mcpResponse = new JsonObject();

		// content配列を作成 (必須)
		JsonArray content = new JsonArray();
		JsonObject textContent = new JsonObject();
		textContent.addProperty("type", "text");
		textContent.addProperty("text", toolResult.toString());
		content.add(textContent);

		mcpResponse.add("content", content);
		mcpResponse.addProperty("isError", false);

		// 元の結果をstructuredContentとして保持（オプション）
		mcpResponse.add("structuredContent", toolResult);

		return mcpResponse;
	}
}
