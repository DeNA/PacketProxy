package packetproxy.extensions.mcp.tools;

import com.google.gson.JsonObject;

public interface MCPTool {

	/**
	 * ツール名を取得
	 */
	String getName();

	/**
	 * ツールの説明を取得
	 */
	String getDescription();

	/**
	 * 入力スキーマを取得 (JSON Schema properties形式)
	 */
	JsonObject getInputSchema();

	/**
	 * ツールを実行
	 */
	JsonObject call(JsonObject arguments) throws Exception;
}
