package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import packetproxy.controller.ResendController;
import packetproxy.controller.ResendController.ResendWorker;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

/**
 * 複数パケット一括送信ツール フェーズ2: 順次送信モード、modifications適用、regex_params機能
 */
public class BulkSendTool extends AuthenticatedMCPTool {

	@Override
	public String getName() {
		return "bulk_send";
	}

	@Override
	public String getDescription() {
		return "Send multiple packets in bulk with optional modifications. "
				+ "Use packet_ids array to specify which packets to send (can repeat same ID for multiple variations). "
				+ "Use regex_params to apply different modifications to each packet based on packet_index (0-based). "
				+ "For full header replacement, use patterns like 'User-Agent: [^\\r\\n]*'. "
				+ "For partial replacement, use capture groups like 'Content-Length: ([0-9]+)'. "
				+ "The value_template supports variables like {{timestamp}}, {{random}}, {{uuid}}, {{packet_index}}. "
				+ "Note: avoid using both packet_ids array with duplicates AND count parameter simultaneously to prevent unexpected multiplication of packets.";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		// packet_ids (required)
		JsonObject packetIdsProp = new JsonObject();
		packetIdsProp.addProperty("type", "array");
		packetIdsProp.addProperty("description", "Array of packet IDs to send (1-100 packets)");
		JsonObject packetIdsItems = new JsonObject();
		packetIdsItems.addProperty("type", "integer");
		packetIdsProp.add("items", packetIdsItems);
		schema.add("packet_ids", packetIdsProp);

		// mode (optional)
		JsonObject modeProp = new JsonObject();
		modeProp.addProperty("type", "string");
		JsonArray modeEnum = new JsonArray();
		modeEnum.add("parallel");
		modeEnum.add("sequential");
		modeProp.add("enum", modeEnum);
		modeProp.addProperty("description", "Sending mode: parallel (fast) or sequential (controlled)");
		modeProp.addProperty("default", "parallel");
		schema.add("mode", modeProp);

		// count (optional)
		JsonObject countProp = new JsonObject();
		countProp.addProperty("type", "integer");
		countProp.addProperty("description", "Number of times to send each packet (default: 1)");
		countProp.addProperty("default", 1);
		countProp.addProperty("minimum", 1);
		countProp.addProperty("maximum", 1000);
		schema.add("count", countProp);

		// interval_ms (optional)
		JsonObject intervalProp = new JsonObject();
		intervalProp.addProperty("type", "integer");
		intervalProp.addProperty("description",
				"Interval between sends in milliseconds (sequential mode only, default: 0, maximum: 60000)");
		intervalProp.addProperty("default", 0);
		intervalProp.addProperty("minimum", 0);
		intervalProp.addProperty("maximum", 60000);
		schema.add("interval_ms", intervalProp);

		// regex_params (optional)
		JsonObject regexParamsProp = new JsonObject();
		regexParamsProp.addProperty("type", "array");
		regexParamsProp.addProperty("description", "Regex parameters for dynamic value replacement across packets");
		JsonObject regexParamItem = new JsonObject();
		regexParamItem.addProperty("type", "object");
		JsonObject regexParamProps = new JsonObject();

		JsonObject packetIndexProp = new JsonObject();
		packetIndexProp.addProperty("type", "integer");
		packetIndexProp.addProperty("description", "Target packet index (0-based)");
		regexParamProps.add("packet_index", packetIndexProp);

		JsonObject regexPatternProp = new JsonObject();
		regexPatternProp.addProperty("type", "string");
		regexPatternProp.addProperty("description", "Regex pattern to match");
		regexParamProps.add("pattern", regexPatternProp);

		JsonObject valueTemplateProp = new JsonObject();
		valueTemplateProp.addProperty("type", "string");
		valueTemplateProp.addProperty("description",
				"Template with variables: {{packet_index}}, {{timestamp}}, {{random}}, {{uuid}}");
		regexParamProps.add("value_template", valueTemplateProp);

		JsonObject regexTargetProp = new JsonObject();
		regexTargetProp.addProperty("type", "string");
		JsonArray regexTargetEnum = new JsonArray();
		regexTargetEnum.add("request");
		regexTargetEnum.add("response");
		regexTargetEnum.add("both");
		regexTargetProp.add("enum", regexTargetEnum);
		regexTargetProp.addProperty("description", "Target: request, response, or both (default: request)");
		regexTargetProp.addProperty("default", "request");
		regexParamProps.add("target", regexTargetProp);

		regexParamItem.add("properties", regexParamProps);
		regexParamsProp.add("items", regexParamItem);
		schema.add("regex_params", regexParamsProp);

		// modifications (optional) - ResendPacketToolと同じ形式
		JsonObject modificationsProp = new JsonObject();
		modificationsProp.addProperty("type", "array");
		modificationsProp.addProperty("description", "Array of modification rules to apply to all packets");
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

		// allow_duplicate_headers (optional)
		JsonObject allowDuplicateHeadersProp = new JsonObject();
		allowDuplicateHeadersProp.addProperty("type", "boolean");
		allowDuplicateHeadersProp.addProperty("description",
				"Allow duplicate headers when adding/modifying headers (default: false - replace existing headers)");
		allowDuplicateHeadersProp.addProperty("default", false);
		schema.add("allow_duplicate_headers", allowDuplicateHeadersProp);

		// timeout_ms (optional)
		JsonObject timeoutProp = new JsonObject();
		timeoutProp.addProperty("type", "integer");
		timeoutProp.addProperty("description", "Timeout for entire bulk operation in milliseconds (default: 30000)");
		timeoutProp.addProperty("default", 30000);
		timeoutProp.addProperty("minimum", 1000);
		timeoutProp.addProperty("maximum", 300000);
		schema.add("timeout_ms", timeoutProp);

		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("BulkSendTool: Starting bulk send operation");

		// パラメータ取得
		if (!arguments.has("packet_ids")) {
			throw new IllegalArgumentException("packet_ids parameter is required");
		}

		JsonArray packetIdsArray = arguments.getAsJsonArray("packet_ids");
		if (packetIdsArray.size() == 0) {
			throw new IllegalArgumentException("packet_ids array cannot be empty");
		}
		if (packetIdsArray.size() > 100) {
			throw new IllegalArgumentException("packet_ids array cannot exceed 100 packets");
		}

		String mode = arguments.has("mode") ? arguments.get("mode").getAsString() : "parallel";
		int count = arguments.has("count") ? arguments.get("count").getAsInt() : 1;
		int intervalMs = arguments.has("interval_ms") ? arguments.get("interval_ms").getAsInt() : 0;
		boolean allowDuplicateHeaders = arguments.has("allow_duplicate_headers")
				? arguments.get("allow_duplicate_headers").getAsBoolean()
				: false;
		int timeoutMs = arguments.has("timeout_ms") ? arguments.get("timeout_ms").getAsInt() : 30000;

		JsonArray modifications = arguments.has("modifications")
				? arguments.getAsJsonArray("modifications")
				: new JsonArray();

		JsonArray regexParams = arguments.has("regex_params")
				? arguments.getAsJsonArray("regex_params")
				: new JsonArray();

		// 送信モードの検証
		if (!"parallel".equals(mode) && !"sequential".equals(mode)) {
			throw new IllegalArgumentException("mode must be 'parallel' or 'sequential'");
		}

		// 順次送信の場合、interval_msをチェック
		if ("sequential".equals(mode) && intervalMs < 0) {
			throw new IllegalArgumentException("interval_ms must be non-negative for sequential mode");
		}

		log("BulkSendTool: packet_ids=" + packetIdsArray.size() + ", mode=" + mode + ", count=" + count + ", interval="
				+ intervalMs + "ms, allowDuplicateHeaders=" + allowDuplicateHeaders + ", timeout=" + timeoutMs
				+ "ms, modifications=" + modifications.size() + ", regex_params=" + regexParams.size());

		// パケットIDを取得
		List<Integer> packetIds = new ArrayList<>();
		for (JsonElement element : packetIdsArray) {
			packetIds.add(element.getAsInt());
		}

		long startTime = System.currentTimeMillis();
		int totalPackets = packetIds.size();
		int totalCount = totalPackets * count;
		int sentCount = 0;
		int failedCount = 0;
		List<BulkSendResult> results = new ArrayList<>();
		List<RegexParamApplied> regexParamsApplied = new ArrayList<>();
		Map<String, String> extractedValues = new HashMap<>(); // regex_paramsで抽出された値を保存

		try {
			if ("parallel".equals(mode)) {
				// 並列送信
				for (int i = 0; i < packetIds.size(); i++) {
					int packetId = packetIds.get(i);
					BulkSendResult result = processSinglePacket(packetId, i, count, modifications, regexParams,
							extractedValues, allowDuplicateHeaders);
					results.add(result);
					sentCount += result.sentCount;
					failedCount += result.failedCount;
					regexParamsApplied.addAll(result.regexParamsApplied);
				}
			} else {
				// 順次送信
				for (int i = 0; i < packetIds.size(); i++) {
					int packetId = packetIds.get(i);
					BulkSendResult result = processSinglePacketSequential(packetId, i, count, modifications,
							regexParams, extractedValues, allowDuplicateHeaders, intervalMs);
					results.add(result);
					sentCount += result.sentCount;
					failedCount += result.failedCount;
					regexParamsApplied.addAll(result.regexParamsApplied);

					// 次のパケットまでインターバル（最後のパケット以外）
					if (intervalMs > 0 && i < packetIds.size() - 1) {
						Thread.sleep(intervalMs);
					}
				}
			}

		} catch (Exception e) {
			log("BulkSendTool: Bulk send operation failed: " + e.getMessage());
			throw e;
		}

		long executionTime = System.currentTimeMillis() - startTime;

		// 結果作成
		JsonObject result = new JsonObject();
		result.addProperty("success", failedCount == 0);
		result.addProperty("mode", mode);
		result.addProperty("total_packets", totalPackets);
		result.addProperty("total_count", totalCount);
		result.addProperty("sent_count", sentCount);
		result.addProperty("failed_count", failedCount);
		result.addProperty("execution_time_ms", executionTime);

		// 詳細結果
		JsonArray resultsArray = new JsonArray();
		for (BulkSendResult r : results) {
			JsonObject resultObj = new JsonObject();
			resultObj.addProperty("original_packet_id", r.originalPacketId);
			resultObj.addProperty("packet_index", r.packetIndex);
			resultObj.addProperty("success", r.success);
			resultObj.addProperty("sent_count", r.sentCount);
			resultObj.addProperty("failed_count", r.failedCount);

			JsonArray newPacketIds = new JsonArray();
			for (Integer id : r.newPacketIds) {
				newPacketIds.add(id);
			}
			resultObj.add("new_packet_ids", newPacketIds);

			if (r.error != null) {
				resultObj.addProperty("error", r.error);
			}
			resultObj.addProperty("execution_time_ms", r.executionTimeMs);

			resultsArray.add(resultObj);
		}
		result.add("results", resultsArray);

		// regex_params適用結果
		JsonArray regexParamsAppliedArray = new JsonArray();
		for (RegexParamApplied rpa : regexParamsApplied) {
			JsonObject rpaObj = new JsonObject();
			rpaObj.addProperty("packet_index", rpa.packetIndex);
			rpaObj.addProperty("pattern", rpa.pattern);
			rpaObj.addProperty("extracted_value", rpa.extractedValue);
			rpaObj.addProperty("applied_count", rpa.appliedCount);
			regexParamsAppliedArray.add(rpaObj);
		}
		result.add("regex_params_applied", regexParamsAppliedArray);

		// パフォーマンス統計
		JsonObject performance = new JsonObject();
		double packetsPerSecond = totalCount > 0 ? (double) sentCount / (executionTime / 1000.0) : 0.0;
		double avgResponseTime = results.size() > 0
				? results.stream().mapToLong(r -> r.executionTimeMs).average().orElse(0.0)
				: 0.0;

		performance.addProperty("packets_per_second", Math.round(packetsPerSecond * 100.0) / 100.0);
		performance.addProperty("average_response_time_ms", Math.round(avgResponseTime));
		performance.addProperty("concurrent_connections", totalPackets);
		result.add("performance", performance);

		result.add("job_id", null); // 非同期実行はフェーズ3で実装

		log("BulkSendTool: Completed. Sent: " + sentCount + ", Failed: " + failedCount + ", Time: " + executionTime
				+ "ms");
		return result;
	}

	/**
	 * 単一パケットの処理（並列送信）
	 */
	private BulkSendResult processSinglePacket(int packetId, int packetIndex, int count, JsonArray modifications,
			JsonArray regexParams, Map<String, String> extractedValues, boolean allowDuplicateHeaders) {

		BulkSendResult result = new BulkSendResult();
		result.originalPacketId = packetId;
		result.packetIndex = packetIndex;
		result.newPacketIds = new ArrayList<>();
		result.regexParamsApplied = new ArrayList<>();

		long startTime = System.currentTimeMillis();

		try {
			// パケットを取得
			Packet originalPacket = Packets.getInstance().query(packetId);
			if (originalPacket == null) {
				result.success = false;
				result.failedCount = count;
				result.error = "Packet with ID " + packetId + " not found";
				return result;
			}

			// OneShotPacketを作成
			OneShotPacket originalOneShot = createOneShotPacket(originalPacket);
			if (originalOneShot == null) {
				result.success = false;
				result.failedCount = count;
				result.error = "Cannot create OneShotPacket from packet ID " + packetId;
				return result;
			}

			// regex_paramsを適用
			OneShotPacket regexModifiedPacket = applyRegexParams(originalOneShot, regexParams, packetIndex,
					extractedValues, result.regexParamsApplied);

			// modificationsを適用
			OneShotPacket modifiedPacket = applyModifications(regexModifiedPacket, modifications, packetIndex + 1,
					allowDuplicateHeaders);

			// 複数回送信用のパケット配列を作成
			OneShotPacket[] packetsToSend = new OneShotPacket[count];
			for (int i = 0; i < count; i++) {
				packetsToSend[i] = modifiedPacket;
			}

			// ResendControllerを使用して並列送信
			CountDownLatch latch = new CountDownLatch(1);
			List<OneShotPacket> receivedPackets = new ArrayList<>();
			List<Exception> sendErrors = new ArrayList<>();

			ResendController.getInstance().resend(new ResendWorker(packetsToSend) {
				@Override
				protected void process(List<OneShotPacket> chunks) {
					synchronized (receivedPackets) {
						receivedPackets.addAll(chunks);
					}
				}

				@Override
				protected void done() {
					try {
						get(); // 例外があれば取得
					} catch (Exception e) {
						synchronized (sendErrors) {
							sendErrors.add(e);
						}
					}
					latch.countDown();
				}
			});

			// 完了を待機
			boolean completed = latch.await(30, TimeUnit.SECONDS);
			if (!completed) {
				result.success = false;
				result.failedCount = count;
				result.error = "Timeout waiting for packet sending completion";
				return result;
			}

			// 結果を設定
			if (!sendErrors.isEmpty()) {
				result.success = false;
				result.failedCount = count;
				result.error = "Send failed: " + sendErrors.get(0).getMessage();
			} else {
				result.success = true;
				result.sentCount = count;
				// 新しいパケットIDは実際の実装では取得困難なため空のままとする
			}

		} catch (Exception e) {
			result.success = false;
			result.failedCount = count;
			result.error = e.getMessage();
			log("BulkSendTool: Failed to process packet " + packetId + ": " + e.getMessage());
		} finally {
			result.executionTimeMs = System.currentTimeMillis() - startTime;
		}

		return result;
	}

	/**
	 * 単一パケットの処理（順次送信）
	 */
	private BulkSendResult processSinglePacketSequential(int packetId, int packetIndex, int count,
			JsonArray modifications, JsonArray regexParams, Map<String, String> extractedValues,
			boolean allowDuplicateHeaders, int intervalMs) {

		BulkSendResult result = new BulkSendResult();
		result.originalPacketId = packetId;
		result.packetIndex = packetIndex;
		result.newPacketIds = new ArrayList<>();
		result.regexParamsApplied = new ArrayList<>();

		long startTime = System.currentTimeMillis();

		try {
			// パケットを取得
			Packet originalPacket = Packets.getInstance().query(packetId);
			if (originalPacket == null) {
				result.success = false;
				result.failedCount = count;
				result.error = "Packet with ID " + packetId + " not found";
				return result;
			}

			// OneShotPacketを作成
			OneShotPacket originalOneShot = createOneShotPacket(originalPacket);
			if (originalOneShot == null) {
				result.success = false;
				result.failedCount = count;
				result.error = "Cannot create OneShotPacket from packet ID " + packetId;
				return result;
			}

			// 順次送信の場合、各送信で異なる処理を実行
			int successCount = 0;
			int failCount = 0;

			for (int i = 0; i < count; i++) {
				try {
					// regex_paramsを適用（送信回数も考慮）
					OneShotPacket regexModifiedPacket = applyRegexParams(originalOneShot, regexParams, packetIndex,
							extractedValues, result.regexParamsApplied);

					// modificationsを適用
					OneShotPacket modifiedPacket = applyModifications(regexModifiedPacket, modifications,
							packetIndex * count + i + 1, allowDuplicateHeaders);

					// 単発送信
					ResendController.getInstance().resend(modifiedPacket);
					successCount++;

					// 同一パケット内の送信間隔
					if (intervalMs > 0 && i < count - 1) {
						Thread.sleep(intervalMs);
					}

				} catch (Exception e) {
					log("BulkSendTool: Failed to send packet " + packetId + " (attempt " + (i + 1) + "): "
							+ e.getMessage());
					failCount++;
				}
			}

			result.success = failCount == 0;
			result.sentCount = successCount;
			result.failedCount = failCount;

		} catch (Exception e) {
			result.success = false;
			result.failedCount = count;
			result.error = e.getMessage();
			log("BulkSendTool: Failed to process packet " + packetId + ": " + e.getMessage());
		} finally {
			result.executionTimeMs = System.currentTimeMillis() - startTime;
		}

		return result;
	}

	/**
	 * OneShotPacketを作成（ResendPacketToolと同じロジック）
	 */
	private OneShotPacket createOneShotPacket(Packet originalPacket) throws Exception {
		if (originalPacket.getModifiedData().length > 0) {
			return originalPacket.getOneShotFromModifiedData();
		} else if (originalPacket.getSentData().length > 0) {
			return originalPacket.getOneShotPacket(originalPacket.getSentData());
		} else {
			return originalPacket.getOneShotFromDecodedData();
		}
	}

	/**
	 * regex_paramsを適用
	 */
	private OneShotPacket applyRegexParams(OneShotPacket original, JsonArray regexParams, int packetIndex,
			Map<String, String> extractedValues, List<RegexParamApplied> appliedList) throws Exception {

		if (regexParams.size() == 0) {
			return original;
		}

		log("BulkSendTool: Applying " + regexParams.size() + " regex params to packet (index=" + packetIndex + ")");

		byte[] data = original.getData().clone();
		String dataStr = new String(data);

		for (JsonElement paramElement : regexParams) {
			JsonObject param = paramElement.getAsJsonObject();

			// packet_indexが指定されている場合、対象パケットかチェック
			if (param.has("packet_index") && param.get("packet_index").getAsInt() != packetIndex) {
				continue;
			}

			String pattern = param.get("pattern").getAsString();
			String valueTemplate = param.get("value_template").getAsString();
			String target = param.has("target") ? param.get("target").getAsString() : "request";

			// 値テンプレートを処理
			String processedValue = processValueTemplate(valueTemplate, packetIndex, extractedValues);

			try {
				Pattern regex = Pattern.compile(pattern);
				Matcher matcher = regex.matcher(dataStr);

				if (matcher.find()) {
					// マッチした値を抽出（後続パケットで使用可能）
					String extractedValue = null;
					try {
						// キャプチャグループがあるかチェック
						if (matcher.groupCount() > 0) {
							extractedValue = matcher.group(1);
						} else {
							// キャプチャグループがない場合は全体をマッチ
							extractedValue = matcher.group(0);
						}

						if (extractedValue != null) {
							String key = "packet_" + packetIndex + "_" + pattern;
							extractedValues.put(key, extractedValue);
						}
					} catch (Exception ex) {
						log("BulkSendTool: Failed to extract value: " + ex.getMessage());
					}

					// 置換実行
					String beforeReplace = dataStr;
					dataStr = matcher.replaceAll(processedValue);

					// デバッグログ: 置換前後の比較
					if (!beforeReplace.equals(dataStr)) {
						log("BulkSendTool: Replacement successful - pattern: " + pattern);
						log("BulkSendTool: Before: " + beforeReplace.substring(Math.max(0, matcher.start() - 20),
								Math.min(beforeReplace.length(), matcher.end() + 20)));
						log("BulkSendTool: After: " + dataStr
								.substring(Math.max(0, dataStr.indexOf(processedValue) - 20), Math.min(dataStr.length(),
										dataStr.indexOf(processedValue) + processedValue.length() + 20)));
					} else {
						log("BulkSendTool: Warning: No replacement occurred for pattern: " + pattern);
					}

					// 適用結果を記録
					RegexParamApplied applied = new RegexParamApplied();
					applied.packetIndex = packetIndex;
					applied.pattern = pattern;
					applied.extractedValue = extractedValue;
					applied.appliedCount = 1;
					appliedList.add(applied);

					log("BulkSendTool: Regex param applied - pattern: " + pattern + ", value: " + processedValue);
				} else {
					log("BulkSendTool: Pattern not found in data - pattern: " + pattern);
					// デバッグ用: データの一部を表示
					String debugData = dataStr.length() > 200 ? dataStr.substring(0, 200) + "..." : dataStr;
					log("BulkSendTool: Data sample: " + debugData.replace("\r\n", "\\r\\n"));
				}

			} catch (Exception e) {
				log("BulkSendTool: Regex param failed: " + e.getMessage());
				e.printStackTrace();
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
	 * value_templateを処理（ResendPacketToolのprocessReplacementVariablesを拡張）
	 */
	private String processValueTemplate(String template, int packetIndex, Map<String, String> extractedValues) {
		String result = template;

		// {{packet_index}} - パケットインデックス
		result = result.replace("{{packet_index}}", String.valueOf(packetIndex));

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

		// 抽出された値を置換（{{extracted:key}}形式）
		for (Map.Entry<String, String> entry : extractedValues.entrySet()) {
			String placeholder = "{{extracted:" + entry.getKey() + "}}";
			result = result.replace(placeholder, entry.getValue());
		}

		return result;
	}

	/**
	 * パケットに改変を適用（ResendPacketToolのロジックを完全実装）
	 */
	private OneShotPacket applyModifications(OneShotPacket original, JsonArray modifications, int index,
			boolean allowDuplicateHeaders) throws Exception {

		if (modifications.size() == 0) {
			return original;
		}

		log("BulkSendTool: Applying " + modifications.size() + " modifications to packet (index=" + index + ")");

		byte[] data = original.getData().clone();
		String dataStr = new String(data);

		for (JsonElement modElement : modifications) {
			JsonObject modification = modElement.getAsJsonObject();

			String target = modification.has("target") ? modification.get("target").getAsString() : "request";
			String type = modification.get("type").getAsString();

			log("BulkSendTool: Applying modification type=" + type + ", target=" + target);

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
					log("BulkSendTool: Unknown modification type: " + type);
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
	 * 正規表現置換を適用（ResendPacketToolから移植）
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
			log("BulkSendTool: Regex replace applied - pattern: " + pattern + ", replacement: " + replacement);
			return result;
		} catch (Exception e) {
			log("BulkSendTool: Regex replace failed: " + e.getMessage());
			return data;
		}
	}

	/**
	 * ヘッダー追加を適用（ResendPacketToolから移植）
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
					log("BulkSendTool: Header replaced (no duplicates allowed) - " + name + ": " + value);
					return result;
				}
			}

			// 新しいヘッダーを追加
			String newHeader = name + ": " + value + "\r\n";
			String result = headers + "\r\n" + newHeader + body;
			log("BulkSendTool: Header added - " + name + ": " + value);
			return result;
		}

		return data;
	}

	/**
	 * ヘッダー変更を適用（ResendPacketToolから移植）
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
				log("BulkSendTool: Header modified - " + name + ": " + value + " (allowDuplicates="
						+ allowDuplicateHeaders + ")");
				return result;
			} else {
				// ヘッダーが見つからない場合は追加
				return applyHeaderAdd(data, modification, index, allowDuplicateHeaders);
			}
		} catch (Exception e) {
			log("BulkSendTool: Header modify failed: " + e.getMessage());
			return data;
		}
	}

	/**
	 * 置換変数を処理（ResendPacketToolから移植）
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
	 * ランダム文字列生成（ResendPacketToolから移植）
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

	/**
	 * 個別パケットの送信結果
	 */
	private static class BulkSendResult {
		int originalPacketId;
		int packetIndex;
		boolean success;
		int sentCount;
		int failedCount;
		List<Integer> newPacketIds;
		String error;
		long executionTimeMs;
		List<RegexParamApplied> regexParamsApplied;
	}

	/**
	 * regex_paramsの適用結果
	 */
	private static class RegexParamApplied {
		int packetIndex;
		String pattern;
		String extractedValue;
		int appliedCount;
	}
}
