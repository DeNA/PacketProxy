package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import packetproxy.controller.ResendController;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.common.Range;
import packetproxy.VulCheckerManager;
import packetproxy.vulchecker.VulChecker;
import packetproxy.vulchecker.generator.Generator;

/**
 * VulCheck脆弱性テストヘルパーツール
 * 指定されたパケットにVulCheckテストケースを適用して連続送信を実行
 */
public class VulCheckHelperTool extends AuthenticatedMCPTool {

	@Override
	public String getName() {
		return "call_vulcheck_helper";
	}

	@Override
	public String getDescription() {
		return "Execute VulCheck vulnerability tests with automatic payload generation and batch sending. Applies VulCheck test cases to specified packet locations and sends modified packets with configurable intervals. IMPORTANT: For precise targeting, use regex patterns with positive lookahead assertions. Examples: To target '17' in 'X-Version: 17.0.4', use pattern: '17(?=\\.0\\.4)'. To target '123' in 'userId=123&other=456', use pattern: '(?<=userId=)123(?=&)'. To target specific values while preserving structure, use patterns like: 'sessionId=\\\\w+' with replacement 'sessionId=$1'. The pattern field supports full regex syntax including lookahead/lookbehind assertions for precise matching without affecting surrounding text. Use replacement field to control how the matched text is substituted with VulCheck payloads.";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject packetIdProp = new JsonObject();
		packetIdProp.addProperty("type", "integer");
		packetIdProp.addProperty("description", "ID of the packet to use as base for VulCheck testing");
		schema.add("packet_id", packetIdProp);

		JsonObject vulCheckTypeProp = new JsonObject();
		vulCheckTypeProp.addProperty("type", "string");
		vulCheckTypeProp.addProperty("description", "Type of VulCheck to perform (Number, JWT, etc.). Use 'list' to get available types.");
		schema.add("vulcheck_type", vulCheckTypeProp);

		JsonObject targetLocationsProp = new JsonObject();
		targetLocationsProp.addProperty("type", "array");
		targetLocationsProp.addProperty("description", "Array of target locations in the packet where VulCheck payloads should be injected. Can specify either regex patterns or position ranges.");
		JsonObject locationItem = new JsonObject();
		locationItem.addProperty("type", "object");
		JsonObject locationProps = new JsonObject();

		// Regex pattern approach (new)
		JsonObject patternProp = new JsonObject();
		patternProp.addProperty("type", "string");
		patternProp.addProperty("description", "Regex pattern to match target locations in the packet data");
		locationProps.add("pattern", patternProp);

		JsonObject replacementProp = new JsonObject();
		replacementProp.addProperty("type", "string");
		replacementProp.addProperty("description", "Optional replacement template for pattern matches. If not specified, the entire match will be replaced.");
		locationProps.add("replacement", replacementProp);

		// Position range approach (existing - for backward compatibility)
		JsonObject startProp = new JsonObject();
		startProp.addProperty("type", "integer");
		startProp.addProperty("description", "Start position of the target location in the packet data (alternative to pattern)");
		locationProps.add("start", startProp);

		JsonObject endProp = new JsonObject();
		endProp.addProperty("type", "integer");
		endProp.addProperty("description", "End position of the target location in the packet data (alternative to pattern)");
		locationProps.add("end", endProp);

		JsonObject descriptionProp = new JsonObject();
		descriptionProp.addProperty("type", "string");
		descriptionProp.addProperty("description", "Optional description of this target location");
		locationProps.add("description", descriptionProp);

		locationItem.add("properties", locationProps);
		targetLocationsProp.add("items", locationItem);
		schema.add("target_locations", targetLocationsProp);

		JsonObject intervalProp = new JsonObject();
		intervalProp.addProperty("type", "integer");
		intervalProp.addProperty("description", "Interval between packet sends in milliseconds (default: 100)");
		intervalProp.addProperty("default", 100);
		schema.add("interval_ms", intervalProp);

		JsonObject modeProp = new JsonObject();
		modeProp.addProperty("type", "string");
		JsonArray modeEnum = new JsonArray();
		modeEnum.add("sequential");
		modeEnum.add("parallel");
		modeProp.add("enum", modeEnum);
		modeProp.addProperty("description", "Execution mode: sequential (with intervals) or parallel (all at once)");
		modeProp.addProperty("default", "sequential");
		schema.add("mode", modeProp);

		JsonObject maxPayloadsProp = new JsonObject();
		maxPayloadsProp.addProperty("type", "integer");
		maxPayloadsProp.addProperty("description", "Maximum number of payloads to generate per location (default: 50, max: 1000)");
		maxPayloadsProp.addProperty("default", 50);
		maxPayloadsProp.addProperty("maximum", 1000);
		schema.add("max_payloads", maxPayloadsProp);

		JsonObject timeoutProp = new JsonObject();
		timeoutProp.addProperty("type", "integer");
		timeoutProp.addProperty("description", "Timeout for entire operation in milliseconds (default: 300000 - 5 minutes)");
		timeoutProp.addProperty("default", 300000);
		schema.add("timeout_ms", timeoutProp);

		// access_tokenを追加
		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("VulCheckHelperTool: Starting VulCheck operation");

		// パラメータ取得
		if (!arguments.has("packet_id")) {
			throw new IllegalArgumentException("packet_id parameter is required");
		}

		if (!arguments.has("vulcheck_type")) {
			throw new IllegalArgumentException("vulcheck_type parameter is required");
		}

		int packetId = arguments.get("packet_id").getAsInt();
		String vulCheckType = arguments.get("vulcheck_type").getAsString();

		// 特別なケース: 利用可能なVulCheckタイプを一覧表示
		if ("list".equals(vulCheckType)) {
			return getAvailableVulCheckTypes();
		}

		if (!arguments.has("target_locations")) {
			throw new IllegalArgumentException("target_locations parameter is required");
		}

		JsonArray targetLocationsJson = arguments.getAsJsonArray("target_locations");
		int intervalMs = arguments.has("interval_ms") ? arguments.get("interval_ms").getAsInt() : 100;
		String mode = arguments.has("mode") ? arguments.get("mode").getAsString() : "sequential";
		int maxPayloads = arguments.has("max_payloads") ? arguments.get("max_payloads").getAsInt() : 50;
		int timeoutMs = arguments.has("timeout_ms") ? arguments.get("timeout_ms").getAsInt() : 300000;

		log("VulCheckHelperTool: packet_id=" + packetId + ", vulcheck_type=" + vulCheckType 
			+ ", locations=" + targetLocationsJson.size() + ", mode=" + mode + ", max_payloads=" + maxPayloads);

		// パケットを取得
		Packet originalPacket = Packets.getInstance().query(packetId);
		if (originalPacket == null) {
			throw new IllegalArgumentException("Packet with ID " + packetId + " not found");
		}

		// OneShotPacketを作成
		OneShotPacket originalOneShot;
		if (originalPacket.getModifiedData().length > 0) {
			originalOneShot = originalPacket.getOneShotFromModifiedData();
		} else if (originalPacket.getSentData().length > 0) {
			originalOneShot = originalPacket.getOneShotPacket(originalPacket.getSentData());
		} else {
			originalOneShot = originalPacket.getOneShotFromDecodedData();
		}

		if (originalOneShot == null) {
			throw new IllegalArgumentException("Cannot create OneShotPacket from packet ID " + packetId);
		}

		// VulCheckerを取得
		VulChecker vulChecker = VulCheckerManager.getInstance().createInstance(vulCheckType);
		if (vulChecker == null) {
			throw new IllegalArgumentException("VulCheck type '" + vulCheckType + "' not found. Use 'list' to see available types.");
		}

		// ターゲット位置を解析
		List<TargetLocation> targetLocations = parseTargetLocations(targetLocationsJson, originalOneShot.getData());
		
		long startTime = System.currentTimeMillis();
		
		// VulCheckテストを実行
		VulCheckResult result = executeVulCheckTests(originalOneShot, vulChecker, targetLocations, 
			intervalMs, mode, maxPayloads, timeoutMs);

		long executionTime = System.currentTimeMillis() - startTime;

		// 結果を構築
		JsonObject response = new JsonObject();
		response.addProperty("success", result.overallSuccess);
		response.addProperty("vulcheck_type", vulCheckType);
		response.addProperty("mode", mode);
		response.addProperty("total_locations", targetLocations.size());
		response.addProperty("total_payloads_generated", result.totalPayloadsGenerated);
		response.addProperty("total_packets_sent", result.totalPacketsSent);
		response.addProperty("total_failed", result.totalFailed);
		response.addProperty("execution_time_ms", executionTime);

		// 各ターゲット位置の結果
		JsonArray locationResults = new JsonArray();
		for (LocationResult locResult : result.locationResults) {
			JsonObject locJson = new JsonObject();
			locJson.addProperty("start", locResult.range.getPositionStart());
			locJson.addProperty("end", locResult.range.getPositionEnd());
			locJson.addProperty("description", locResult.description);
			locJson.addProperty("payloads_generated", locResult.payloadsGenerated);
			locJson.addProperty("packets_sent", locResult.packetsSent);
			locJson.addProperty("packets_failed", locResult.packetsFailed);
			locJson.addProperty("execution_time_ms", locResult.executionTimeMs);

			// 生成されたペイロードの一覧
			JsonArray payloadsList = new JsonArray();
			for (String payload : locResult.generatedPayloads) {
				payloadsList.add(payload);
			}
			locJson.add("generated_payloads", payloadsList);

			locationResults.add(locJson);
		}
		response.add("location_results", locationResults);

		// パフォーマンス統計
		JsonObject performance = new JsonObject();
		performance.addProperty("average_interval_ms", result.averageIntervalMs);
		performance.addProperty("payloads_per_second", (double) result.totalPacketsSent / (executionTime / 1000.0));
		performance.addProperty("success_rate_percent", result.totalPacketsSent > 0 
			? ((double)(result.totalPacketsSent - result.totalFailed) / result.totalPacketsSent) * 100.0 : 0.0);
		response.add("performance", performance);

		log("VulCheckHelperTool: Completed. Generated " + result.totalPayloadsGenerated + " payloads, sent " 
			+ result.totalPacketsSent + " packets, " + result.totalFailed + " failed, time: " + executionTime + "ms");

		return response;
	}

	/**
	 * 利用可能なVulCheckタイプを取得
	 */
	private JsonObject getAvailableVulCheckTypes() throws Exception {
		VulCheckerManager manager = VulCheckerManager.getInstance();
		String[] vulCheckerNames = manager.getVulCheckerNameList();

		JsonObject result = new JsonObject();
		result.addProperty("available_vulcheck_types", String.join(", ", vulCheckerNames));
		
		JsonArray typesArray = new JsonArray();
		Map<String, JsonObject> typeDetails = new HashMap<>();

		for (String name : vulCheckerNames) {
			VulChecker checker = manager.createInstance(name);
			if (checker != null) {
				JsonObject typeInfo = new JsonObject();
				typeInfo.addProperty("name", name);
				typeInfo.addProperty("description", "VulCheck tests for " + name + " vulnerabilities");
				
				// ジェネレータの詳細を追加
				ImmutableList<Generator> generators = checker.getGenerators();
				JsonArray generatorsList = new JsonArray();
				for (Generator gen : generators) {
					JsonObject genInfo = new JsonObject();
					genInfo.addProperty("name", gen.getName());
					genInfo.addProperty("generate_on_start", gen.generateOnStart());
					generatorsList.add(genInfo);
				}
				typeInfo.add("generators", generatorsList);
				typeInfo.addProperty("generator_count", generators.size());
				
				typesArray.add(typeInfo);
				typeDetails.put(name, typeInfo);
			}
		}
		
		result.add("vulcheck_types", typesArray);
		result.addProperty("total_types", vulCheckerNames.length);
		
		return result;
	}

	/**
	 * ターゲット位置を解析してTargetLocationリストに変換
	 */
	private List<TargetLocation> parseTargetLocations(JsonArray targetLocations, byte[] packetData) throws Exception {
		List<TargetLocation> locations = new ArrayList<>();
		String packetStr = new String(packetData);
		
		for (JsonElement element : targetLocations) {
			JsonObject location = element.getAsJsonObject();
			
			// Check if regex pattern is specified
			if (location.has("pattern")) {
				// Regex-based approach
				String pattern = location.get("pattern").getAsString();
				String replacement = location.has("replacement") ? location.get("replacement").getAsString() : null;
				String description = location.has("description") ? location.get("description").getAsString() : ("Pattern: " + pattern);
				
				// Find all matches for the pattern
				Pattern regex = Pattern.compile(pattern);
				Matcher matcher = regex.matcher(packetStr);
				
				int matchCount = 0;
				while (matcher.find()) {
					matchCount++;
					int start = matcher.start();
					int end = matcher.end();
					
					TargetLocation targetLoc = new TargetLocation();
					targetLoc.range = Range.of(start, end);
					targetLoc.description = description + " (match " + matchCount + ")";
					targetLoc.pattern = pattern;
					targetLoc.replacement = replacement;
					targetLoc.originalMatch = matcher.group();
					
					locations.add(targetLoc);
				}
				
				if (matchCount == 0) {
					log("VulCheckHelperTool: No matches found for pattern: " + pattern);
				}
			} else if (location.has("start") && location.has("end")) {
				// Position-based approach (backward compatibility)
				int start = location.get("start").getAsInt();
				int end = location.get("end").getAsInt();
				String description = location.has("description") ? location.get("description").getAsString() : ("Range " + start + "-" + end);
				
				if (start < 0 || end < start || end > packetData.length) {
					throw new IllegalArgumentException("Invalid range: start=" + start + ", end=" + end + ", packet length=" + packetData.length);
				}
				
				TargetLocation targetLoc = new TargetLocation();
				targetLoc.range = Range.of(start, end);
				targetLoc.description = description;
				targetLoc.originalMatch = packetStr.substring(start, end);
				
				locations.add(targetLoc);
			} else {
				throw new IllegalArgumentException("Each target location must have either 'pattern' or both 'start' and 'end' properties");
			}
		}
		
		if (locations.isEmpty()) {
			throw new IllegalArgumentException("No valid target locations found");
		}
		
		return locations;
	}

	/**
	 * VulCheckテストを実行
	 */
	private VulCheckResult executeVulCheckTests(OneShotPacket originalPacket, VulChecker vulChecker, 
			List<TargetLocation> targetLocations, int intervalMs, String mode, int maxPayloads, int timeoutMs) throws Exception {
		
		VulCheckResult result = new VulCheckResult();
		result.overallSuccess = true;
		
		// 各ターゲット位置に対してテストを実行
		for (int locationIndex = 0; locationIndex < targetLocations.size(); locationIndex++) {
			TargetLocation targetLocation = targetLocations.get(locationIndex);
			
			log("VulCheckHelperTool: Processing target location " + (locationIndex + 1) + "/" + targetLocations.size() 
				+ " at range " + targetLocation.range.getPositionStart() + "-" + targetLocation.range.getPositionEnd() 
				+ " (" + targetLocation.description + ")");
			
			LocationResult locResult = processTargetLocation(originalPacket, vulChecker, targetLocation, 
				intervalMs, mode, maxPayloads);
			
			result.locationResults.add(locResult);
			result.totalPayloadsGenerated += locResult.payloadsGenerated;
			result.totalPacketsSent += locResult.packetsSent;
			result.totalFailed += locResult.packetsFailed;
			
			if (locResult.packetsFailed > 0) {
				result.overallSuccess = false;
			}
		}
		
		// 平均間隔を計算
		if (result.totalPacketsSent > 1) {
			result.averageIntervalMs = intervalMs; // sequentialモードの場合
		}
		
		return result;
	}

	/**
	 * 特定のターゲット位置でVulCheckテストを実行
	 */
	private LocationResult processTargetLocation(OneShotPacket originalPacket, VulChecker vulChecker, 
			TargetLocation targetLocation, int intervalMs, String mode, int maxPayloads) throws Exception {
		
		LocationResult result = new LocationResult();
		result.range = targetLocation.range;
		result.description = targetLocation.description;
		
		long startTime = System.currentTimeMillis();
		
		// 元のパケットデータを取得
		byte[] originalData = originalPacket.getData();
		String originalText = new String(originalData);
		
		// ターゲット範囲のデータを取得
		if (targetLocation.range.getPositionEnd() > originalData.length) {
			throw new IllegalArgumentException("Target range exceeds packet data length: range end=" 
				+ targetLocation.range.getPositionEnd() + ", data length=" + originalData.length);
		}
		
		String targetData = targetLocation.originalMatch != null ? targetLocation.originalMatch 
			: originalText.substring(targetLocation.range.getPositionStart(), targetLocation.range.getPositionEnd());
		
		// VulCheckのジェネレータを取得してペイロードを生成
		ImmutableList<Generator> generators = vulChecker.getGenerators();
		ResendController resendController = ResendController.getInstance();
		
		int payloadCount = 0;
		int sentCount = 0;
		int failedCount = 0;
		
		for (Generator generator : generators) {
			if (payloadCount >= maxPayloads) {
				log("VulCheckHelperTool: Reached max payload limit (" + maxPayloads + ")");
				break;
			}
			
			try {
				String payload = generator.generate(targetData);
				if (payload != null && !payload.equals(targetData)) {
					result.generatedPayloads.add(payload);
					payloadCount++;
					
					// パケットを改変して送信
					String modifiedText;
					if (targetLocation.pattern != null && targetLocation.replacement != null) {
						// Regex pattern replacement approach
						String replacementTemplate = targetLocation.replacement.replace("$1", payload);
						Pattern pattern = Pattern.compile(targetLocation.pattern);
						Matcher matcher = pattern.matcher(originalText);
						
						// Find the specific match we're targeting
						StringBuffer sb = new StringBuffer();
						int currentMatch = 0;
						while (matcher.find()) {
							if (matcher.start() == targetLocation.range.getPositionStart()) {
								// This is our target match
								matcher.appendReplacement(sb, Matcher.quoteReplacement(replacementTemplate));
								break;
							} else {
								// Keep other matches unchanged
								matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
							}
						}
						matcher.appendTail(sb);
						modifiedText = sb.toString();
					} else {
						// Position-based replacement approach
						modifiedText = originalText.substring(0, targetLocation.range.getPositionStart()) 
							+ payload 
							+ originalText.substring(targetLocation.range.getPositionEnd());
					}
					
					byte[] modifiedData = modifiedText.getBytes();
					
					OneShotPacket modifiedPacket = new OneShotPacket(originalPacket.getId(), 
						originalPacket.getListenPort(), originalPacket.getClient(), originalPacket.getServer(),
						originalPacket.getServerName(), originalPacket.getUseSSL(), modifiedData,
						originalPacket.getEncoder(), originalPacket.getAlpn(), originalPacket.getDirection(),
						originalPacket.getConn(), originalPacket.getGroup());
					
					// 送信モードに応じて処理
					if ("parallel".equals(mode)) {
						// 並列送信 - すぐに送信
						try {
							resendController.resend(modifiedPacket);
							sentCount++;
						} catch (Exception e) {
							log("VulCheckHelperTool: Failed to send packet with payload: " + e.getMessage());
							failedCount++;
						}
					} else {
						// 順次送信 - 間隔を設けて送信
						try {
							resendController.resend(modifiedPacket);
							sentCount++;
							
							if (intervalMs > 0 && payloadCount < maxPayloads && payloadCount < generators.size()) {
								Thread.sleep(intervalMs);
							}
						} catch (Exception e) {
							log("VulCheckHelperTool: Failed to send packet with payload: " + e.getMessage());
							failedCount++;
						}
					}
				}
			} catch (Exception e) {
				log("VulCheckHelperTool: Failed to generate payload with generator " + generator.getName() + ": " + e.getMessage());
				failedCount++;
			}
		}
		
		result.payloadsGenerated = payloadCount;
		result.packetsSent = sentCount;
		result.packetsFailed = failedCount;
		result.executionTimeMs = System.currentTimeMillis() - startTime;
		
		log("VulCheckHelperTool: Location complete - generated " + payloadCount + " payloads, sent " 
			+ sentCount + " packets, " + failedCount + " failed");
		
		return result;
	}

	/**
	 * VulCheckテストの全体結果
	 */
	private static class VulCheckResult {
		boolean overallSuccess = true;
		int totalPayloadsGenerated = 0;
		int totalPacketsSent = 0;
		int totalFailed = 0;
		double averageIntervalMs = 0;
		List<LocationResult> locationResults = new ArrayList<>();
	}

	/**
	 * ターゲット位置の情報
	 */
	private static class TargetLocation {
		Range range;
		String description;
		String pattern;        // regex pattern (if used)
		String replacement;    // replacement template (if used)
		String originalMatch;  // original matched text
	}

	/**
	 * 特定位置でのテスト結果
	 */
	private static class LocationResult {
		Range range;
		String description;
		int payloadsGenerated = 0;
		int packetsSent = 0;
		int packetsFailed = 0;
		long executionTimeMs = 0;
		List<String> generatedPayloads = new ArrayList<>();
	}
}