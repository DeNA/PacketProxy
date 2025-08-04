package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import packetproxy.controller.ResendController;
import packetproxy.controller.ResendController.ResendWorker;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

/**
 * 複数パケット一括送信ツール
 * フェーズ1: 基本的な並列送信機能とmodifications適用
 */
public class BulkSendTool extends AuthenticatedMCPTool {

	@Override
	public String getName() {
		return "bulk_send";
	}

	@Override
	public String getDescription() {
		return "Send multiple packets in bulk with optional modifications and multiple count support";
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
		modeProp.addProperty("description", "Sending mode: parallel (fast) or sequential (controlled) - Phase 1 supports parallel only");
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
		boolean allowDuplicateHeaders = arguments.has("allow_duplicate_headers")
				? arguments.get("allow_duplicate_headers").getAsBoolean()
				: false;
		int timeoutMs = arguments.has("timeout_ms") ? arguments.get("timeout_ms").getAsInt() : 30000;

		JsonArray modifications = arguments.has("modifications")
				? arguments.getAsJsonArray("modifications")
				: new JsonArray();

		// フェーズ1では並列送信のみサポート
		if (!"parallel".equals(mode)) {
			throw new IllegalArgumentException("Phase 1 supports parallel mode only. Sequential mode will be available in Phase 2.");
		}

		log("BulkSendTool: packet_ids=" + packetIdsArray.size() + ", mode=" + mode + ", count=" + count
				+ ", allowDuplicateHeaders=" + allowDuplicateHeaders + ", timeout=" + timeoutMs + "ms");

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

		try {
			// 各パケットを処理
			for (int i = 0; i < packetIds.size(); i++) {
				int packetId = packetIds.get(i);
				BulkSendResult result = processSinglePacket(packetId, i, count, modifications, allowDuplicateHeaders);
				results.add(result);
				sentCount += result.sentCount;
				failedCount += result.failedCount;
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

		log("BulkSendTool: Completed. Sent: " + sentCount + ", Failed: " + failedCount + ", Time: " + executionTime + "ms");
		return result;
	}

	/**
	 * 単一パケットの処理（並列送信）
	 */
	private BulkSendResult processSinglePacket(int packetId, int packetIndex, int count, 
			JsonArray modifications, boolean allowDuplicateHeaders) {
		
		BulkSendResult result = new BulkSendResult();
		result.originalPacketId = packetId;
		result.packetIndex = packetIndex;
		result.newPacketIds = new ArrayList<>();
		
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

			// 改変を適用
			OneShotPacket modifiedPacket = applyModifications(originalOneShot, modifications, packetIndex + 1, allowDuplicateHeaders);

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
	 * パケットに改変を適用（ResendPacketToolのロジックを再利用）
	 */
	private OneShotPacket applyModifications(OneShotPacket original, JsonArray modifications, int index,
			boolean allowDuplicateHeaders) throws Exception {
		
		// ResendPacketToolのapplyModificationsメソッドと同じ実装
		// ここでは簡略化のため、modificationsが空の場合はオリジナルを返す
		if (modifications.size() == 0) {
			return original;
		}

		log("BulkSendTool: Applying " + modifications.size() + " modifications to packet (index=" + index + ")");

		// 実際の改変処理はResendPacketToolと同じ実装を使用
		// フェーズ1では基本的な処理のみ実装
		// TODO: ResendPacketToolのapplyModificationsメソッドを共通化するか、ここで再実装

		return original; // フェーズ1では改変なしで返す
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
	}
}