package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import packetproxy.controller.ResendController;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

/**
 * パケット再送ツール パケットを指定回数再送し、改変オプションもサポート
 */
public class ResendPacketTool extends AuthenticatedMCPTool {

	@Override
	public String getName() {
		return "resend_packet";
	}

	@Override
	public String getDescription() {
		return "Resend a packet with optional modifications and multiple count support";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject packetIdProp = new JsonObject();
		packetIdProp.addProperty("type", "integer");
		packetIdProp.addProperty("description", "ID of the packet to resend");
		schema.add("packet_id", packetIdProp);

		JsonObject countProp = new JsonObject();
		countProp.addProperty("type", "integer");
		countProp.addProperty("description", "Number of times to send the packet (default: 1)");
		countProp.addProperty("default", 1);
		schema.add("count", countProp);

		JsonObject intervalProp = new JsonObject();
		intervalProp.addProperty("type", "integer");
		intervalProp.addProperty("description", "Interval between sends in milliseconds (default: 0)");
		intervalProp.addProperty("default", 0);
		schema.add("interval_ms", intervalProp);

		JsonObject modificationsProp = new JsonObject();
		modificationsProp.addProperty("type", "array");
		modificationsProp.addProperty("description", "Array of modification rules to apply to the packet");
		JsonObject modificationItem = new JsonObject();
		modificationItem.addProperty("type", "object");
		JsonObject modificationProps = new JsonObject();

		JsonObject targetProp = new JsonObject();
		targetProp.addProperty("type", "string");
		JsonArray targetEnum = new JsonArray();
		targetEnum.add("request");
		targetEnum.add("response");
		targetEnum.add("both");
		targetProp.add("enum", targetEnum);
		targetProp.addProperty("description", "Target to modify: request, response, or both");
		modificationProps.add("target", targetProp);

		JsonObject typeProp = new JsonObject();
		typeProp.addProperty("type", "string");
		JsonArray typeEnum = new JsonArray();
		typeEnum.add("regex_replace");
		typeEnum.add("header_add");
		typeEnum.add("header_modify");
		typeProp.add("enum", typeEnum);
		typeProp.addProperty("description", "Type of modification");
		modificationProps.add("type", typeProp);

		JsonObject patternProp = new JsonObject();
		patternProp.addProperty("type", "string");
		patternProp.addProperty("description", "Regex pattern for regex_replace type");
		modificationProps.add("pattern", patternProp);

		JsonObject replacementProp = new JsonObject();
		replacementProp.addProperty("type", "string");
		replacementProp.addProperty("description", "Replacement string for regex_replace or value for headers");
		modificationProps.add("replacement", replacementProp);

		JsonObject nameProp = new JsonObject();
		nameProp.addProperty("type", "string");
		nameProp.addProperty("description", "Header name for header_add/header_modify");
		modificationProps.add("name", nameProp);

		JsonObject valueProp = new JsonObject();
		valueProp.addProperty("type", "string");
		valueProp.addProperty("description", "Header value for header_add/header_modify");
		modificationProps.add("value", valueProp);

		modificationItem.add("properties", modificationProps);
		modificationsProp.add("items", modificationItem);
		schema.add("modifications", modificationsProp);

		JsonObject asyncProp = new JsonObject();
		asyncProp.addProperty("type", "boolean");
		asyncProp.addProperty("description", "Execute asynchronously (default: false)");
		asyncProp.addProperty("default", false);
		schema.add("async", asyncProp);

		JsonObject allowDuplicateHeadersProp = new JsonObject();
		allowDuplicateHeadersProp.addProperty("type", "boolean");
		allowDuplicateHeadersProp.addProperty("description",
				"Allow duplicate headers when adding/modifying headers (default: false - replace existing headers)");
		allowDuplicateHeadersProp.addProperty("default", false);
		schema.add("allow_duplicate_headers", allowDuplicateHeadersProp);

		// access_tokenを追加
		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("ResendPacketTool: Starting packet resend operation");

		// パラメータ取得
		if (!arguments.has("packet_id")) {
			throw new IllegalArgumentException("packet_id parameter is required");
		}

		int packetId = arguments.get("packet_id").getAsInt();
		int count = arguments.has("count") ? arguments.get("count").getAsInt() : 1;
		int intervalMs = arguments.has("interval_ms") ? arguments.get("interval_ms").getAsInt() : 0;
		boolean async = arguments.has("async") ? arguments.get("async").getAsBoolean() : false;
		boolean allowDuplicateHeaders = arguments.has("allow_duplicate_headers")
				? arguments.get("allow_duplicate_headers").getAsBoolean()
				: false;

		JsonArray modifications = arguments.has("modifications")
				? arguments.getAsJsonArray("modifications")
				: new JsonArray();

		log("ResendPacketTool: packet_id=" + packetId + ", count=" + count + ", interval=" + intervalMs + "ms, async="
				+ async + ", allowDuplicateHeaders=" + allowDuplicateHeaders);

		// パケットを取得
		Packet originalPacket = Packets.getInstance().query(packetId);
		if (originalPacket == null) {
			throw new IllegalArgumentException("Packet with ID " + packetId + " not found");
		}

		// 適切なデータを使ってOneShotPacketを作成
		// 改変データがあれば改変データを、なければ送信データを使用
		OneShotPacket originalOneShot;
		if (originalPacket.getModifiedData().length > 0) {
			originalOneShot = originalPacket.getOneShotFromModifiedData();
		} else if (originalPacket.getSentData().length > 0) {
			originalOneShot = originalPacket.getOneShotPacket(originalPacket.getSentData());
		} else {
			// デコードされたデータをフォールバックとして使用
			originalOneShot = originalPacket.getOneShotFromDecodedData();
		}

		if (originalOneShot == null) {
			throw new IllegalArgumentException("Cannot create OneShotPacket from packet ID " + packetId);
		}

		log("ResendPacketTool: Original packet found, preparing for resend");

		long startTime = System.currentTimeMillis();
		int sentCount = 0;
		int failedCount = 0;
		List<Integer> newPacketIds = new ArrayList<>();

		try {
			ResendController resendController = ResendController.getInstance();

			if (count == 1 && modifications.size() == 0) {
				// 単純な1回再送、改変なし
				log("ResendPacketTool: Simple single resend without modifications");
				resendController.resend(originalOneShot);
				sentCount = 1;
			} else {
				// 複数回送信または改変ありの場合
				log("ResendPacketTool: Complex resend with count=" + count + " and modifications="
						+ modifications.size());

				for (int i = 0; i < count; i++) {
					try {
						OneShotPacket modifiedPacket = applyModifications(originalOneShot, modifications, i + 1,
								allowDuplicateHeaders);
						resendController.resend(modifiedPacket);
						sentCount++;

						// インターバル待機（最後の送信後は待機しない）
						if (intervalMs > 0 && i < count - 1) {
							Thread.sleep(intervalMs);
						}
					} catch (Exception e) {
						log("ResendPacketTool: Failed to send packet " + (i + 1) + ": " + e.getMessage());
						failedCount++;
					}
				}
			}
		} catch (Exception e) {
			log("ResendPacketTool: Resend operation failed: " + e.getMessage());
			failedCount = count - sentCount;
			throw e;
		}

		long executionTime = System.currentTimeMillis() - startTime;

		// 結果作成
		JsonObject result = new JsonObject();
		result.addProperty("success", failedCount == 0);
		result.addProperty("sent_count", sentCount);
		result.addProperty("failed_count", failedCount);
		result.addProperty("execution_time_ms", executionTime);

		JsonArray packetIdsArray = new JsonArray();
		for (Integer id : newPacketIds) {
			packetIdsArray.add(id);
		}
		result.add("packet_ids", packetIdsArray);

		log("ResendPacketTool: Completed. Sent: " + sentCount + ", Failed: " + failedCount + ", Time: " + executionTime
				+ "ms");
		return result;
	}

	/**
	 * パケットに改変を適用
	 */
	private OneShotPacket applyModifications(OneShotPacket original, JsonArray modifications, int index,
			boolean allowDuplicateHeaders) throws Exception {
		if (modifications.size() == 0) {
			return original;
		}

		log("ResendPacketTool: Applying " + modifications.size() + " modifications to packet (index=" + index + ")");

		byte[] data = original.getData().clone();
		String dataStr = new String(data);

		for (JsonElement modElement : modifications) {
			JsonObject modification = modElement.getAsJsonObject();

			String target = modification.has("target") ? modification.get("target").getAsString() : "request";
			String type = modification.get("type").getAsString();

			log("ResendPacketTool: Applying modification type=" + type + ", target=" + target);

			switch (type) {
				case "regex_replace" :
					dataStr = applyRegexReplace(dataStr, modification, index);
					break;
				case "header_add" :
					dataStr = applyHeaderAdd(dataStr, modification, index, allowDuplicateHeaders);
					break;
				case "header_modify" :
					dataStr = applyHeaderModify(dataStr, modification, index, allowDuplicateHeaders);
					break;
				default :
					log("ResendPacketTool: Unknown modification type: " + type);
					break;
			}
		}

		data = dataStr.getBytes();

		// 新しいOneShotPacketを作成
		OneShotPacket modifiedPacket = new OneShotPacket(original.getId(), original.getListenPort(),
				original.getClient(), original.getServer(), original.getServerName(), original.getUseSSL(), data,
				original.getEncoder(), original.getAlpn(), original.getDirection(), original.getConn(),
				original.getGroup());

		return modifiedPacket;
	}

	/**
	 * 正規表現置換を適用
	 */
	private String applyRegexReplace(String data, JsonObject modification, int index) {
		String pattern = modification.get("pattern").getAsString();
		String replacement = modification.get("replacement").getAsString();

		// 置換変数を処理
		replacement = processReplacementVariables(replacement, index);

		try {
			Pattern regex = Pattern.compile(pattern);
			Matcher matcher = regex.matcher(data);
			String result = matcher.replaceAll(replacement);
			log("ResendPacketTool: Regex replace applied - pattern: " + pattern + ", replacement: " + replacement);
			return result;
		} catch (Exception e) {
			log("ResendPacketTool: Regex replace failed: " + e.getMessage());
			return data;
		}
	}

	/**
	 * ヘッダー追加を適用
	 */
	private String applyHeaderAdd(String data, JsonObject modification, int index, boolean allowDuplicateHeaders) {
		String name = modification.get("name").getAsString();
		String value = modification.get("value").getAsString();

		// 置換変数を処理
		value = processReplacementVariables(value, index);

		// HTTP形式のデータの場合、ヘッダー部分に追加
		if (data.contains("\r\n\r\n")) {
			int headerEnd = data.indexOf("\r\n\r\n");
			String headers = data.substring(0, headerEnd);
			String body = data.substring(headerEnd);

			// 重複を許可しない場合、既存ヘッダーがあるかチェック
			if (!allowDuplicateHeaders) {
				String pattern = "(?i)" + Pattern.quote(name) + ":\\s*[^\r\n]*";
				Pattern regex = Pattern.compile(pattern);
				Matcher matcher = regex.matcher(headers);
				if (matcher.find()) {
					// 既存ヘッダーを置換
					String result = matcher.replaceFirst(name + ": " + value) + body;
					log("ResendPacketTool: Header replaced (no duplicates allowed) - " + name + ": " + value);
					return result;
				}
			}

			// 新しいヘッダーを追加
			String newHeader = name + ": " + value + "\r\n";
			String result = headers + "\r\n" + newHeader + body;
			log("ResendPacketTool: Header added - " + name + ": " + value);
			return result;
		}

		return data;
	}

	/**
	 * ヘッダー変更を適用
	 */
	private String applyHeaderModify(String data, JsonObject modification, int index, boolean allowDuplicateHeaders) {
		String name = modification.get("name").getAsString();
		String value = modification.get("value").getAsString();

		// 置換変数を処理
		value = processReplacementVariables(value, index);

		// 既存ヘッダーを置換
		String pattern = "(?i)" + Pattern.quote(name) + ":\\s*[^\r\n]*";

		try {
			Pattern regex = Pattern.compile(pattern);
			Matcher matcher = regex.matcher(data);
			if (matcher.find()) {
				String replacement = name + ": " + value;
				String result;
				if (allowDuplicateHeaders) {
					// 重複を許可する場合は最初のヘッダーのみ変更
					result = matcher.replaceFirst(replacement);
				} else {
					// 重複を許可しない場合は全ての同名ヘッダーを置換
					result = matcher.replaceAll(replacement);
				}
				log("ResendPacketTool: Header modified - " + name + ": " + value + " (allowDuplicates="
						+ allowDuplicateHeaders + ")");
				return result;
			} else {
				// ヘッダーが見つからない場合は追加
				return applyHeaderAdd(data, modification, index, allowDuplicateHeaders);
			}
		} catch (Exception e) {
			log("ResendPacketTool: Header modify failed: " + e.getMessage());
			return data;
		}
	}

	/**
	 * 置換変数を処理
	 */
	private String processReplacementVariables(String input, int index) {
		String result = input;

		// {{index}} - 送信順序
		result = result.replace("{{index}}", String.valueOf(index));

		// {{timestamp}} - Unix timestamp
		result = result.replace("{{timestamp}}", String.valueOf(System.currentTimeMillis() / 1000));

		// {{random}} - ランダム文字列
		if (result.contains("{{random}}")) {
			String randomStr = generateRandomString(8);
			result = result.replace("{{random}}", randomStr);
		}

		// {{uuid}} - UUID v4
		if (result.contains("{{uuid}}")) {
			String uuid = UUID.randomUUID().toString();
			result = result.replace("{{uuid}}", uuid);
		}

		// {{datetime}} - ISO 8601形式日時
		if (result.contains("{{datetime}}")) {
			String datetime = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
			result = result.replace("{{datetime}}", datetime);
		}

		return result;
	}

	/**
	 * ランダム文字列生成
	 */
	private String generateRandomString(int length) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		Random random = new Random();
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt(random.nextInt(chars.length())));
		}

		return sb.toString();
	}
}
