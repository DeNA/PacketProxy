package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.JsonObject;
import packetproxy.model.ConfigString;

/**
 * 認証機能付きMCPツールの基底クラス
 */
public abstract class AuthenticatedMCPTool implements MCPTool {

	/**
	 * AccessTokenの検証を行う
	 */
	protected void validateAccessToken(JsonObject arguments) throws Exception {
		// MCP clientから渡されたAccessTokenを取得
		if (!arguments.has("access_token")) {
			throw new Exception(
					"access_token parameter is required. Please provide your PacketProxy access token from Settings.");
		}

		String providedToken = arguments.get("access_token").getAsString();
		if (providedToken == null) {
			throw new Exception(
					"access_token parameter is required. Please provide your PacketProxy access token from Settings or leave empty (\"\") to use environment variable.");
		}

		// 空文字列の場合は環境変数から取得する想定なのでvalidationをスキップ
		if (providedToken.trim().isEmpty()) {
			log("Empty access_token provided, assuming environment variable usage");
			return;
		}

		// PacketProxy設定からAccessTokenを取得
		String configuredToken = new ConfigString("SharingConfigsAccessToken").getString();
		if (configuredToken.isEmpty()) {
			throw new Exception(
					"Access token not configured in PacketProxy. Please enable 'Import/Export configs' in PacketProxy Settings and copy the generated access token.");
		}

		// トークンの照合
		if (!configuredToken.equals(providedToken)) {
			log("Access token validation failed. Provided: " + providedToken + ", Expected: " + configuredToken);
			throw new Exception(
					"Invalid access token. Please check your access token from PacketProxy Settings > Import/Export configs section.");
		}

		log("Access token validation successful");
	}

	/**
	 * 設定済みAccessTokenを取得（HTTPリクエスト用）
	 */
	protected String getConfiguredAccessToken() throws Exception {
		String accessToken = new ConfigString("SharingConfigsAccessToken").getString();
		if (accessToken.isEmpty()) {
			throw new Exception("Access token not configured. Please enable config sharing in settings.");
		}
		return accessToken;
	}

	/**
	 * 入力スキーマにaccess_tokenパラメータを追加
	 */
	protected JsonObject addAccessTokenToSchema(JsonObject schema) {
		JsonObject accessTokenProp = new JsonObject();
		accessTokenProp.addProperty("type", "string");
		accessTokenProp.addProperty("description",
				"Access token for authentication. Leave empty (\"\") to use environment variable (handled by scripts/mcp-http-bridge.js), or provide explicit token string");
		schema.add("access_token", accessTokenProp);
		return schema;
	}

	/**
	 * サブクラスで実装する認証後の実際の処理
	 */
	protected abstract JsonObject executeAuthenticated(JsonObject arguments) throws Exception;

	/**
	 * 認証チェック付きでツールを実行
	 */
	@Override
	public final JsonObject call(JsonObject arguments) throws Exception {
		validateAccessToken(arguments);
		return executeAuthenticated(arguments);
	}
}
