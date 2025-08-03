package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.util.List;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

public class HistoryTool implements MCPTool {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private final Gson gson = new Gson();

	@Override
	public String getName() {
		return "get_history";
	}

	@Override
	public String getDescription() {
		return "Get packet history from PacketProxy";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject limitProp = new JsonObject();
		limitProp.addProperty("type", "integer");
		limitProp.addProperty("description", "Maximum number of packets to return");
		limitProp.addProperty("default", 100);
		schema.add("limit", limitProp);

		JsonObject offsetProp = new JsonObject();
		offsetProp.addProperty("type", "integer");
		offsetProp.addProperty("description", "Number of packets to skip");
		offsetProp.addProperty("default", 0);
		schema.add("offset", offsetProp);

		return schema;
	}

	@Override
	public JsonObject call(JsonObject arguments) throws Exception {
		log("HistoryTool called with arguments: " + arguments.toString());

		int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 100;
		int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;

		// Validate parameters
		if (limit < 1 || limit > 1000) {
			throw new Exception("Limit must be between 1 and 1000");
		}
		if (offset < 0) {
			throw new Exception("Offset must be non-negative");
		}

		try {
			Packets packets = Packets.getInstance();
			List<Packet> allPackets = packets.queryAll();

			JsonArray packetsArray = new JsonArray();

			int totalCount = allPackets.size();
			int startIndex = Math.min(offset, totalCount);
			int endIndex = Math.min(startIndex + limit, totalCount);

			for (int i = startIndex; i < endIndex; i++) {
				Packet packet = allPackets.get(i);
				JsonObject packetJson = convertPacketToJson(packet);
				packetsArray.add(packetJson);
			}

			JsonObject data = new JsonObject();
			data.add("packets", packetsArray);
			data.addProperty("total_count", totalCount);
			data.addProperty("has_more", endIndex < totalCount);

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", gson.toJson(data));

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject result = new JsonObject();
			result.add("content", contentArray);

			log("HistoryTool returning " + packetsArray.size() + " packets");
			return result;

		} catch (Exception e) {
			log("HistoryTool error: " + e.getMessage());
			throw new Exception("Failed to get packet history: " + e.getMessage());
		}
	}

	private JsonObject convertPacketToJson(Packet packet) {
		JsonObject packetJson = new JsonObject();

		packetJson.addProperty("id", packet.getId());
		packetJson.addProperty("length", packet.getDecodedData().length);
		packetJson.addProperty("client_ip", packet.getClientIP());
		packetJson.addProperty("client_port", packet.getClientPort());
		packetJson.addProperty("server_ip", packet.getServerIP());
		packetJson.addProperty("server_port", packet.getServerPort());
		packetJson.addProperty("time", dateFormat.format(packet.getDate()));
		packetJson.addProperty("resend", packet.getResend());
		packetJson.addProperty("modified", packet.getModified());
		packetJson.addProperty("type", packet.getContentType());
		packetJson.addProperty("encode", packet.getEncoder());

		// HTTPの場合、methodとurlを抽出
		try {
			String request = new String(packet.getDecodedData(), "UTF-8");
			String[] lines = request.split("\n");
			if (lines.length > 0) {
				String[] requestLine = lines[0].split(" ");
				if (requestLine.length >= 2) {
					packetJson.addProperty("method", requestLine[0]);
					packetJson.addProperty("url", requestLine[1]);
				}
			}
		} catch (Exception e) {
			// HTTP以外のパケットの場合は無視
		}

		return packetJson;
	}
}
